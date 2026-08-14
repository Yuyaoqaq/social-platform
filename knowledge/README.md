# Agent 知识库原材料

本目录只存 AI 运营助手需要检索的业务知识，技术设计文档继续放在 `docs/`。

第一阶段统一使用 Markdown。建议目录：

```text
knowledge/
├─ rules/   运营规范、发布规范
├─ tags/    标签说明
├─ cases/   优秀内容和运营案例
├─ styles/  文案和图片风格
└─ assets/  Markdown 引用的图片
```

第一阶段直接编写普通 Markdown 即可。文件名和一级标题写清楚主题，不要求手写 YAML 头信息。

建议一篇文件只解决一个问题，例如“标题怎么写”“露营内容如何选标签”，不要把所有规则堆进同一篇文档。

启动 `share-ai` 时设置 `AGENT_RAG_AUTO_INDEX=true`，可以把本目录重新写入 Qdrant。
