#!/usr/bin/env python3
"""阿里云百炼模型路由器。

先在本地提取用户意图、判断任务难度，再只调用一次目标模型：

- 简单任务：qwen-turbo，限制较短输出
- 复杂任务：qwen3.7-plus，允许较长输出

环境变量：
    DASHSCOPE_API_KEY           百炼 API Key；也会自动读取 IDEA 的 aliQwen-api
    DASHSCOPE_BASE_URL          可选，默认北京地域兼容接口
    BAILIAN_SIMPLE_MODEL        可选，默认 qwen-turbo
    BAILIAN_COMPLEX_MODEL       可选，默认 qwen3.7-plus
    BAILIAN_SIMPLE_MAX_TOKENS   可选，默认 512
    BAILIAN_COMPLEX_MAX_TOKENS  可选，默认 2048
    BAILIAN_COMPLEX_THINKING    可选，复杂任务是否开启思考，默认 false

使用示例：
    python bailian_router.py --dry-run "你好，帮我润色这句话"
    echo "你好" | python bailian_router.py --dry-run --stdin
    python bailian_router.py "分析这段代码的并发问题并给出修改方案"
    python bailian_router.py --json "写一条周末露营短图文"
"""

import argparse
import json
import os
import re
import sys
from dataclasses import asdict, dataclass
from typing import Any, Dict, List, Optional, Tuple
from urllib import error, request


DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"


@dataclass(frozen=True)
class RouteDecision:
    """一次路由判断的结果。"""

    intent: str
    simple: bool
    complexity_score: int
    model: str
    max_tokens: int
    enable_thinking: bool
    requires_tools: bool
    reasons: List[str]


class BailianRouter:
    """不额外调用模型的轻量路由器。"""

    INTENT_RULES: Tuple[Tuple[str, Tuple[str, ...]], ...] = (
        ("image_generation", ("生成图片", "画一张", "做张图", "配图", "封面图", "海报")),
        ("xhs_search", ("小红书", "高赞笔记", "爆款笔记", "热门图文")),
        ("current_search", ("最新", "今天", "现在", "实时", "联网", "搜索", "查一下", "热搜")),
        ("coding", ("代码", "报错", "bug", "接口", "数据库", "sql", "python", "java", "vue", "重构")),
        ("analysis", ("分析", "比较", "对比", "原因", "推理", "评估", "诊断", "为什么")),
        ("planning", ("方案", "计划", "步骤", "架构", "设计", "规划", "怎么实现")),
        ("content_creation", ("写一篇", "写一条", "文案", "标题", "短图文", "小红书", "创作")),
        ("summarization", ("总结", "概括", "摘要", "提炼")),
        ("translation", ("翻译", "译成", "translate")),
        ("rewriting", ("润色", "改写", "缩写", "扩写", "换个说法")),
        ("question_answering", ("是什么", "多少", "谁是", "哪里", "怎么读", "解释一下")),
    )

    TOOL_MARKERS = (
        "最新", "今天", "现在", "实时", "联网", "搜索", "查一下", "站内",
        "小红书", "高赞笔记", "爆款笔记", "热门图文",
        "生成图片", "画一张", "做张图", "配图", "封面图", "海报",
    )

    COMPLEX_MARKERS = (
        "深入", "详细", "全面", "系统", "完整", "多角度", "逐步", "权衡",
        "根因", "架构", "重构", "并发", "安全", "性能", "数学证明", "算法",
        "数据库", "接口设计", "可行性", "优缺点", "风险", "专业", "报告",
    )

    MULTI_STEP_MARKERS = (
        "并且", "同时", "然后", "最后", "分别", "第一", "第二", "第三",
        "以及", "还要", "另外", "除此之外",
    )

    def __init__(
        self,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        simple_model: Optional[str] = None,
        complex_model: Optional[str] = None,
    ) -> None:
        self.api_key = (
            api_key
            or os.getenv("DASHSCOPE_API_KEY")
            or os.getenv("ALI_QWEN_API_KEY")
            or os.getenv("aliQwen-api")
        )
        self.base_url = (base_url or os.getenv("DASHSCOPE_BASE_URL") or DEFAULT_BASE_URL).rstrip("/")
        self.simple_model = simple_model or os.getenv("BAILIAN_SIMPLE_MODEL", "qwen-turbo")
        self.complex_model = complex_model or os.getenv("BAILIAN_COMPLEX_MODEL", "qwen3.7-plus")
        self.simple_max_tokens = self._positive_int("BAILIAN_SIMPLE_MAX_TOKENS", 512)
        self.complex_max_tokens = self._positive_int("BAILIAN_COMPLEX_MAX_TOKENS", 2048)
        self.complex_thinking = self._env_bool("BAILIAN_COMPLEX_THINKING", False)

    def extract_intent(self, text: str) -> str:
        """用本地关键词提取主意图，不产生额外模型 Token。"""
        normalized = text.strip().lower()
        for intent, keywords in self.INTENT_RULES:
            if any(keyword in normalized for keyword in keywords):
                return intent
        return "casual_chat" if len(normalized) <= 30 else "general_request"

    def route(self, text: str) -> RouteDecision:
        """根据输入长度、任务类型和多步骤特征选择模型。"""
        normalized = re.sub(r"\s+", " ", text).strip().lower()
        if not normalized:
            raise ValueError("用户输入不能为空")

        intent = self.extract_intent(normalized)
        score = 0
        reasons: List[str] = []

        if len(normalized) > 800:
            score += 3
            reasons.append("输入很长")
        elif len(normalized) > 240:
            score += 2
            reasons.append("输入较长")
        elif len(normalized) > 100:
            score += 1
            reasons.append("输入包含较多内容")

        complex_hits = [word for word in self.COMPLEX_MARKERS if word in normalized]
        if complex_hits:
            score += min(3, len(complex_hits))
            reasons.append("含复杂要求：" + "、".join(complex_hits[:3]))

        step_hits = [word for word in self.MULTI_STEP_MARKERS if word in normalized]
        if len(step_hits) >= 2:
            score += 2
            reasons.append("包含多个步骤")
        elif step_hits:
            score += 1
            reasons.append("包含组合要求")

        if intent in {"coding", "analysis", "planning"}:
            score += 2
            reasons.append("意图需要推理或规划")

        if "```" in text or re.search(r"\b(class|def|select|insert|update|public|private)\b", normalized):
            score += 2
            reasons.append("包含代码或结构化内容")

        requires_tools = any(marker in normalized for marker in self.TOOL_MARKERS)
        if requires_tools:
            score += 3
            reasons.append("可能需要搜索或外部工具")

        simple = score < 3
        if not reasons:
            reasons.append("短文本且没有复杂任务特征")

        return RouteDecision(
            intent=intent,
            simple=simple,
            complexity_score=score,
            model=self.simple_model if simple else self.complex_model,
            max_tokens=self.simple_max_tokens if simple else self.complex_max_tokens,
            enable_thinking=False if simple else self.complex_thinking,
            requires_tools=requires_tools,
            reasons=reasons,
        )

    def chat(self, text: str, system_prompt: Optional[str] = None) -> Dict[str, Any]:
        """完成路由并调用一次百炼 Chat Completions 接口。"""
        decision = self.route(text)
        if not self.api_key:
            raise RuntimeError("未找到 DASHSCOPE_API_KEY 或 IDEA 环境变量 aliQwen-api，无法调用百炼")

        compact_system_prompt = system_prompt or (
            "你是一个中文助手。直接回答用户问题，内容准确、简洁，不展示思考过程。"
        )
        payload = {
            "model": decision.model,
            "messages": [
                {"role": "system", "content": compact_system_prompt},
                {"role": "user", "content": text},
            ],
            "temperature": 0.2,
            "max_tokens": decision.max_tokens,
            "enable_thinking": decision.enable_thinking,
            "stream": False,
        }

        response_data = self._post_json("/chat/completions", payload)
        try:
            answer = response_data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise RuntimeError("百炼返回格式异常：" + json.dumps(response_data, ensure_ascii=False)) from exc

        return {
            "routing": asdict(decision),
            "answer": answer,
            "usage": response_data.get("usage", {}),
            "request_id": response_data.get("id"),
        }

    def _post_json(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        endpoint = self.base_url + path
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        http_request = request.Request(
            endpoint,
            data=body,
            headers={
                "Authorization": "Bearer " + str(self.api_key),
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with request.urlopen(http_request, timeout=120) as response:
                return json.loads(response.read().decode("utf-8"))
        except error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError("百炼请求失败（HTTP {}）：{}".format(exc.code, detail)) from exc
        except error.URLError as exc:
            raise RuntimeError("无法连接百炼：{}".format(exc.reason)) from exc

    @staticmethod
    def _positive_int(name: str, default: int) -> int:
        raw = os.getenv(name)
        if raw is None:
            return default
        try:
            value = int(raw)
        except ValueError as exc:
            raise ValueError("环境变量 {} 必须是整数".format(name)) from exc
        if value <= 0:
            raise ValueError("环境变量 {} 必须大于 0".format(name))
        return value

    @staticmethod
    def _env_bool(name: str, default: bool) -> bool:
        raw = os.getenv(name)
        if raw is None:
            return default
        return raw.strip().lower() in {"1", "true", "yes", "on"}


def main() -> int:
    parser = argparse.ArgumentParser(description="本地判断意图后路由到不同的百炼模型")
    parser.add_argument("message", nargs="*", help="用户输入")
    parser.add_argument("--stdin", action="store_true", help="从标准输入读取用户消息")
    parser.add_argument("--dry-run", action="store_true", help="只看路由结果，不调用百炼")
    parser.add_argument("--json", action="store_true", help="以 JSON 输出完整结果")
    args = parser.parse_args()

    message = sys.stdin.read().strip() if args.stdin else " ".join(args.message).strip()
    if not message:
        parser.error("用户输入不能为空")
    router = BailianRouter()

    try:
        if args.dry_run:
            print(json.dumps(asdict(router.route(message)), ensure_ascii=False, indent=2))
            return 0

        result = router.chat(message)
        if args.json:
            print(json.dumps(result, ensure_ascii=False, indent=2))
        else:
            routing = result["routing"]
            print("[routing] intent={} simple={} model={} score={}".format(
                routing["intent"], routing["simple"], routing["model"], routing["complexity_score"]
            ))
            print(result["answer"])
        return 0
    except (ValueError, RuntimeError) as exc:
        print("错误：{}".format(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
