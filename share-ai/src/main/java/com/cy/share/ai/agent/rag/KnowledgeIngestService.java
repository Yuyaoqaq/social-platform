package com.cy.share.ai.agent.rag;

import com.cy.share.ai.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 知识入库服务，遍历 knowledge 目录中的 Markdown 文件，解析 YAML 头、按标题切分并向量化后写入 Qdrant。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeIngestService {

    private final AgentProperties properties;
    private final QdrantKnowledgeStore knowledgeStore;

    //load文件并插入Qdrant
    public KnowledgeIngestResult reindex() throws IOException {
        Path root = resolveKnowledgeRoot();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("知识目录不存在: " + root.toAbsolutePath());
        }

        List<Path> files;
        //深度搜索root，过滤出所有的.md/.MD文件
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .toList();
        }

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (Path file : files) {
            chunks.addAll(loadFile(root, file));
        }
        int indexed = knowledgeStore.upsert(chunks);
        return new KnowledgeIngestResult(files.size(), indexed);
    }

    //给每个md文件都读成List<KnowledgeChunk>
    private List<KnowledgeChunk> loadFile(Path root, Path file) throws IOException {
        //读取一个 UTF-8 编码的文本文件，并将其内容转换为一个统一换行符、去除首尾空白字符的字符串。
        String raw = Files.readString(file, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .trim();
        ParsedMarkdown parsed = parseFrontMatter(raw);
        //计算出当前文件相对于知识库根目录的“相对路径”
        String source = root.relativize(file).toString().replace('\\', '/');
        Map<String, Object> baseMetadata = new HashMap<>(parsed.metadata());
        baseMetadata.putIfAbsent("source", "knowledge/" + source);
        baseMetadata.putIfAbsent("type", "knowledge");
        baseMetadata.putIfAbsent("version", "1.0");
        //按标题切分内容
        List<String> sections = splitByHeading(parsed.content());
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int index = 0;
        for (String section : sections) {
            //再切分长内容为小块
            for (String piece : splitLongText(section)) {
                if (piece.isBlank()) {
                    continue;
                }
                String rawId = source + "#" + index++;
                String id = UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8)).toString();
                Map<String, Object> metadata = new HashMap<>(baseMetadata);
                metadata.put("chunkIndex", index - 1);
                chunks.add(new KnowledgeChunk(id, piece.trim(), metadata));
            }
        }
        return chunks;
    }
    //读取md中开头元数据部分。并提纯内容
    private ParsedMarkdown parseFrontMatter(String raw) {
        if (!raw.startsWith("---\n")) {
            return new ParsedMarkdown(Map.of(), raw);
        }
        //从raw的第4个字符开始，找\n---\n
        int end = raw.indexOf("\n---\n", 4);
        if (end < 0) {
            return new ParsedMarkdown(Map.of(), raw);
        }
        Map<String, Object> metadata = new HashMap<>();
        String frontMatter = raw.substring(4, end);
        //一行就是一个key ： value
        for (String line : frontMatter.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            //value是数组，用逗号分隔
            if (value.startsWith("[") && value.endsWith("]")) {
                String inner = value.substring(1, value.length() - 1);
                metadata.put(key, Stream.of(inner.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList());
            } else {
                metadata.put(key, value);
            }
        }
        return new ParsedMarkdown(metadata, raw.substring(end + 5).trim());
    }

    //按标题切分内容String session，以#开头为标题
    private List<String> splitByHeading(String content) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\n")) {
            if (line.startsWith("#") && !current.isEmpty()) {
                sections.add(current.toString());
                //清空
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        if (!current.isEmpty()) {
            sections.add(current.toString());
        }
        return sections;
    }

    //滑动窗口将长session按chunksize切分，考虑overlap
    private List<String> splitLongText(String text) {
        int chunkSize = Math.max(300, properties.getRag().getChunkSize());
        int overlap = Math.max(0, Math.min(properties.getRag().getChunkOverlap(), chunkSize / 3));
        if (text.length() <= chunkSize) {
            return List.of(text);
        }
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            result.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start = end - overlap;
        }
        return result;
    }

    //配置目录找不到，回退到找当前目录找一下有没有
    private Path resolveKnowledgeRoot() {
        Path configured = Path.of(properties.getRag().getKnowledgePath()).normalize();
        if (Files.isDirectory(configured)) {
            return configured;
        }
        Path rootCandidate = Path.of("knowledge").normalize();
        return Files.isDirectory(rootCandidate) ? rootCandidate : configured;
    }

    private record ParsedMarkdown(Map<String, Object> metadata, String content) {
    }
}
