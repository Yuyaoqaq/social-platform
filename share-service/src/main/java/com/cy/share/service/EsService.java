package com.cy.share.service;

import com.cy.share.common.utils.UserContext;
import com.cy.share.pojo.LogEsDocument;
import com.cy.share.vo.LogListVo;
import com.cy.share.vo.SearchFeedVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EsService {

    private static final String INDEX = "log";

    @Autowired
    private RestHighLevelClient esClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LikeService likeService;

    public void saveLog(LogEsDocument doc) {
        try {
            String json = objectMapper.writeValueAsString(doc);
            IndexRequest request = new IndexRequest(INDEX)
                    .id(String.valueOf(doc.getId()))
                    .source(json, XContentType.JSON);
            IndexResponse response = esClient.index(request, RequestOptions.DEFAULT);
            log.debug("ES 写入 log id={} result={}", doc.getId(), response.getResult());
        } catch (IOException e) {
            log.error("ES 写入失败 id={}", doc.getId(), e);
        }
    }

    /** 部分更新 love 字段 */
    public void updateLove(Integer logId, Integer love) {
        try {
            UpdateRequest request = new UpdateRequest(INDEX, String.valueOf(logId))
                    .doc("love", love);
            esClient.update(request, RequestOptions.DEFAULT);
            log.debug("ES 更新 love id={} love={}", logId, love);
        } catch (IOException e) {
            log.error("ES 更新 love 失败 id={}", logId, e);
        }
    }

    /** 部分更新 tags 字段 */
    public void updateTags(Integer logId, List<String> tags) {
        try {
            UpdateRequest request = new UpdateRequest(INDEX, String.valueOf(logId))
                    .doc("tags", tags != null ? tags : Collections.emptyList());
            esClient.update(request, RequestOptions.DEFAULT);
            log.debug("ES 更新 tags id={} tags={}", logId, tags);
        } catch (IOException e) {
            log.error("ES 更新 tags 失败 id={}", logId, e);
        }
    }

    /**
     * 搜索博客，支持 search_after 深分页 + 标签过滤。
     * title 权重 3，info 权重 1，tags 精确匹配。
     */
    public SearchFeedVo search(String keyword, String cursor, int size, List<String> tags) {
        try {
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

            if (org.springframework.util.StringUtils.hasText(keyword)) {
                boolQuery.must(QueryBuilders.multiMatchQuery(keyword, "title", "info")
                        .field("title", 3).field("info", 1));
            }

            if (tags != null && !tags.isEmpty()) {
                boolQuery.filter(QueryBuilders.termsQuery("tags", tags));
            }

            SearchSourceBuilder source = new SearchSourceBuilder()
                    .query(boolQuery)
                    .sort("createTimeMs", SortOrder.DESC)
                    .sort("id", SortOrder.DESC)
                    .size(size);

            if (cursor != null && !cursor.isBlank()) {
                String[] parts = cursor.split("_");
                long createTimeMs = Long.parseLong(parts[0]);
                int lastId = Integer.parseInt(parts[1]);
                source.searchAfter(new Object[]{createTimeMs, lastId});
            }

            SearchRequest request = new SearchRequest(INDEX).source(source);
            SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);

            SearchHit[] hits = response.getHits().getHits();
            if (hits.length == 0) {
                return new SearchFeedVo(new ArrayList<>(), null);
            }

            List<LogListVo> list = new ArrayList<>();
            for (SearchHit hit : hits) {
                Map<String, Object> src = hit.getSourceAsMap();
                LogListVo vo = new LogListVo();
                vo.setId((Integer) src.get("id"));
                vo.setTitle((String) src.get("title"));
                vo.setAuthor((String) src.get("author"));
                vo.setAuthorAvatar((String) src.get("authorAvatar"));
                vo.setLove((Integer) src.get("love"));
                Object picurlsObj = src.get("picurls");
                if (picurlsObj instanceof List) {
                    vo.setPicurls((List<String>) picurlsObj);
                }
                Object tagsObj = src.get("tags");
                if (tagsObj instanceof List) {
                    vo.setTags((List<String>) tagsObj);
                }
                Object createTimeMs = src.get("createTimeMs");
                if (createTimeMs != null) {
                    vo.setCreateTime(new Date(((Number) createTimeMs).longValue()));
                }
                list.add(vo);
            }

            // 填充当前用户点赞状态
            String userIdStr = UserContext.get();
            if (userIdStr != null) {
                Integer userId = Integer.valueOf(userIdStr);
                List<Integer> logIds = list.stream().map(LogListVo::getId).collect(Collectors.toList());
                Map<Integer, Boolean> likedMap = likeService.isLikedBatch(userId, logIds);
                list.forEach(vo -> vo.setIsLiked(likedMap.getOrDefault(vo.getId(), false)));
            } else {
                list.forEach(vo -> vo.setIsLiked(false));
            }

            SearchHit lastHit = hits[hits.length - 1];
            Map<String, Object> lastSrc = lastHit.getSourceAsMap();
            String nextCursor = null;
            if (lastSrc.get("createTimeMs") != null && lastSrc.get("id") != null) {
                nextCursor = lastSrc.get("createTimeMs") + "_" + lastSrc.get("id");
            }

            return new SearchFeedVo(list, nextCursor);
        } catch (IOException e) {
            log.error("ES 搜索失败 keyword={}", keyword, e);
            return new SearchFeedVo(new ArrayList<>(), null);
        }
    }
}
