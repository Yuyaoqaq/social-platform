package com.cy.share.ai.agent.rag;

import com.cy.share.ai.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 知识入库服务。
 * Markdown 中一级标题表示分类，二级标题表示一条独立知识，三级标题属于当前知识内部内容。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeIngestService {

    private static final Set<String> KNOWLEDGE_LIBRARIES = Set.of(
            "title_materials",
            "content_materials",
            "image_materials",
            "style_templates",
            "platform_rules");

    private final AgentProperties properties;
    private final QdrantKnowledgeStore knowledgeStore;

    public KnowledgeIngestResult reindex() throws IOException {
        Path root = resolveKnowledgeRoot();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("知识目录不存在: " + root.toAbsolutePath());
        }

        List<Path> files;
        try (Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> isKnowledgeContentFile(root, path))
                    .sorted()
                    .toList();
        }

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (Path file : files) {
            chunks.addAll(loadFile(root, file));
        }
        int indexed = knowledgeStore.replaceAll(chunks);
        return new KnowledgeIngestResult(files.size(), indexed);
    }

    private List<KnowledgeChunk> loadFile(Path root, Path file) throws IOException {
        String raw = Files.readString(file, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .trim();
        String content = stripFrontMatter(raw);
        Path relativePath = root.relativize(file);
        String library = relativePath.getName(0).toString();

        // 平台规则每次固定注入，不参与相似度检索。
        if ("platform_rules".equals(library)) {
            return List.of();
        }

        String contentId = relativePath.getNameCount() == 2
                ? fileNameWithoutExtension(relativePath.getFileName().toString())
                : relativePath.getName(1).toString();
        List<RecordBlock> records = splitIntoRecords(content);
        List<KnowledgeChunk> chunks = new ArrayList<>();

        int recordIndex = 0;
        for (RecordBlock record : records) {
            int pieceIndex = 0;
            for (String piece : splitLongText(record.content())) {
                if (piece.isBlank()) {
                    continue;
                }
                String rawId = library + "/" + contentId + "/" + recordIndex + "#" + pieceIndex++;
                String id = UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8)).toString();
                Map<String, Object> metadata = Map.of(
                        "library", library,
                        "category", record.category());
                chunks.add(new KnowledgeChunk(id, piece.trim(), metadata));
            }
            recordIndex++;
        }
        return chunks;
    }

    private List<RecordBlock> splitIntoRecords(String content) {
        List<RecordBlock> records = new ArrayList<>();
        String category = "未分类";
        StringBuilder current = null;

        for (String line : content.split("\n", -1)) {
            if (line.startsWith("# ")) {
                addRecord(records, category, current);
                current = null;
                category = line.substring(2).trim();
                continue;
            }
            if (line.startsWith("## ")) {
                addRecord(records, category, current);
                current = new StringBuilder(line.substring(3).trim()).append('\n');
                continue;
            }
            if (current != null) {
                if (line.startsWith("### ")) {
                    current.append(line.substring(4).trim()).append("：\n");
                } else {
                    current.append(line).append('\n');
                }
            }
        }
        addRecord(records, category, current);

        if (records.isEmpty() && !content.isBlank()) {
            records.add(new RecordBlock(category, content.trim()));
        }
        return records;
    }

    private void addRecord(List<RecordBlock> records, String category, StringBuilder current) {
        if (current != null && !current.toString().isBlank()) {
            records.add(new RecordBlock(category, current.toString().trim()));
        }
    }

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
            if (end < text.length()) {
                int paragraphEnd = text.lastIndexOf("\n\n", end);
                if (paragraphEnd > start + chunkSize / 2) {
                    end = paragraphEnd;
                }
            }
            result.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return result;
    }

    private boolean isKnowledgeContentFile(Path root, Path file) {
        Path relativePath = root.relativize(file);
        if (relativePath.getNameCount() < 2
                || !KNOWLEDGE_LIBRARIES.contains(relativePath.getName(0).toString())) {
            return false;
        }
        boolean librarySource = relativePath.getNameCount() == 2
                && "source.md".equalsIgnoreCase(relativePath.getFileName().toString());
        boolean independentContent = relativePath.getNameCount() == 3
                && "content.md".equalsIgnoreCase(relativePath.getFileName().toString());
        return librarySource || independentContent;
    }

    private String stripFrontMatter(String raw) {
        if (!raw.startsWith("---\n")) {
            return raw;
        }
        int end = raw.indexOf("\n---\n", 4);
        return end < 0 ? raw : raw.substring(end + 5).trim();
    }

    private String fileNameWithoutExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private Path resolveKnowledgeRoot() {
        Path configured = Path.of(properties.getRag().getKnowledgePath()).normalize();
        if (Files.isDirectory(configured)) {
            return configured;
        }
        Path rootCandidate = Path.of("knowledge").normalize();
        return Files.isDirectory(rootCandidate) ? rootCandidate : configured;
    }

    private record RecordBlock(String category, String content) {
    }
}
