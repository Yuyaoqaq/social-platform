package com.cy.share;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ES log 索引 tags 映射验证测试
 *
 * 验证:
 * 1. log 索引的 tags 字段是否为 keyword 类型
 * 2. updateTags 能否正常写入 tags
 * 3. termsQuery 标签过滤是否命中
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public class EsMappingTest {

    private static final String ES_HOST = "192.168.175.18";
    private static final int    ES_PORT = 9200;
    private static final String INDEX   = "log";

    private static RestHighLevelClient client;
    private static ObjectMapper objectMapper;

    /** 测试用临时文档 ID */
    private static final String TEST_DOC_ID = "999999999";

    @BeforeAll
    public static void setUp() {
        client = new RestHighLevelClient(
                RestClient.builder(new org.apache.http.HttpHost(ES_HOST, ES_PORT, "http"))
        );
        objectMapper = new ObjectMapper();
        System.out.println(">>> ES 客户端已连接: " + ES_HOST + ":" + ES_PORT);
    }

    @AfterAll
    public static void tearDown() throws IOException {
        if (client != null) {
            client.close();
            System.out.println(">>> ES 客户端已关闭");
        }
    }

    // ==================== 1. 映射验证 ====================

    /**
     * 验证 log 索引存在且 tags 字段的类型为 keyword
     * 使用低级 REST 客户端避免 ES 7.x ImmutableOpenMap 泛型问题
     */
    @Test
    @SuppressWarnings("unchecked")
    public void test01_tagsFieldMappingIsKeyword() throws IOException {
        Request req = new Request("GET", "/" + INDEX + "/_mapping");
        Response resp = client.getLowLevelClient().performRequest(req);
        String body = EntityUtils.toString(resp.getEntity());
        Map<String, Object> map = objectMapper.readValue(body, Map.class);

        // 解析: { "log": { "mappings": { "properties": { "tags": { "type": "keyword" } } } } }
        Map<String, Object> indexEntry = (Map<String, Object>) map.get(INDEX);
        assertNotNull(indexEntry, "log 索引不存在! 请先创建索引");

        Map<String, Object> mappings = (Map<String, Object>) indexEntry.get("mappings");
        assertNotNull(mappings, "log 索引没有 mappings 定义");

        Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
        assertNotNull(properties, "log 索引没有 properties 定义");

        Map<String, Object> tagsMapping = (Map<String, Object>) properties.get("tags");
        if (tagsMapping == null) {
            fail("\n>>> FAIL: log 索引缺少 tags 字段映射! "
                    + "\n    请在 Kibana 执行: PUT log/_mapping { \"properties\": { \"tags\": { \"type\": \"keyword\" } } }");
        }

        String type = (String) tagsMapping.get("type");
        assertEquals("keyword", type, "tags 字段类型不是 keyword! 当前类型: " + type);
        System.out.println(">>> PASS: tags 字段映射类型 = " + type + " ✓");
    }

    // ==================== 2. 写入验证 ====================

    /**
     * 写一个测试文档，包含 tags
     */
    @Test
    public void test02_writeDocWithTags() throws IOException {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", 999999999);
        doc.put("title", "ES映射测试文档");
        doc.put("info", "这是一条测试，验证 tags keyword 字段的写入");
        doc.put("author", "test");
        doc.put("authorAvatar", "");
        doc.put("love", 0);
        doc.put("picurls", Collections.emptyList());
        doc.put("tags", Arrays.asList("测试", "AI"));
        doc.put("createTimeMs", System.currentTimeMillis());

        String json = objectMapper.writeValueAsString(doc);
        IndexRequest request = new IndexRequest(INDEX)
                .id(TEST_DOC_ID)
                .source(json, XContentType.JSON);
        IndexResponse response = client.index(request, RequestOptions.DEFAULT);

        assertNotNull(response);
        System.out.println(">>> PASS: 写入测试文档 id=" + TEST_DOC_ID + " result=" + response.getResult()
                + " tags=[测试, AI] ✓");
    }

    // ==================== 3. tags 更新验证 ====================

    /**
     * 用 updateTags 方式局部更新 tags
     */
    @Test
    public void test03_updateTags() throws IOException {
        UpdateRequest request = new UpdateRequest(INDEX, TEST_DOC_ID)
                .doc("tags", Arrays.asList("穿搭", "OOTD"));
        client.update(request, RequestOptions.DEFAULT);

        // 回读验证
        GetResponse getResponse = client.get(new GetRequest(INDEX, TEST_DOC_ID), RequestOptions.DEFAULT);
        assertTrue(getResponse.isExists(), "文档 " + TEST_DOC_ID + " 不存在");

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) getResponse.getSourceAsMap().get("tags");
        assertNotNull(tags, "tags 字段为空");
        assertEquals(2, tags.size());
        assertTrue(tags.contains("穿搭"));
        assertTrue(tags.contains("OOTD"));

        System.out.println(">>> PASS: updateTags 写入成功, 读取到 tags=" + tags + " ✓");
    }

    // ==================== 4. termsQuery 过滤验证 ====================

    /**
     * 用 termsQuery 过滤 tags，验证 keyword 类型能精确匹配
     */
    @Test
    public void test04_termsQueryFilter() throws IOException {
        SearchSourceBuilder source = new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termsQuery("tags", "穿搭")))
                .size(10);

        SearchResponse response = client.search(
                new SearchRequest(INDEX).source(source), RequestOptions.DEFAULT);

        boolean found = Arrays.stream(response.getHits().getHits())
                .anyMatch(hit -> TEST_DOC_ID.equals(hit.getId()));

        assertTrue(found, "termsQuery 过滤 '穿搭' 未找到测试文档! tags 可能不是 keyword 类型");
        System.out.println(">>> PASS: termsQuery 过滤 tags='穿搭' 命中测试文档 ✓");
    }

    // ==================== 5. 清测试数据 ====================

    /**
     * 删除测试文档
     */
    @Test
    public void test05_cleanup() throws IOException {
        client.delete(new DeleteRequest(INDEX, TEST_DOC_ID), RequestOptions.DEFAULT);
        System.out.println(">>> 已删除测试文档 id=" + TEST_DOC_ID + " ✓");
    }
}
