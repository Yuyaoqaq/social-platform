package com.cy.share.service.rpc;

import com.cy.share.api.OpsContentService;
import com.cy.share.dto.QueryDto;
import com.cy.share.service.EsService;
import com.cy.share.service.LikeService;
import com.cy.share.service.LogService;
import com.cy.share.vo.FeedVo;
import com.cy.share.vo.LikeTop3Vo;
import com.cy.share.vo.LogDetailVo;
import com.cy.share.vo.MyContentVo;
import com.cy.share.vo.SearchFeedVo;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 提供给 AI 模块使用的图文查询服务。
 */
@DubboService
@RequiredArgsConstructor
public class OpsContentServiceImpl implements OpsContentService {

    private final EsService esService;
    private final LogService logService;
    private final LikeService likeService;

    @Override
    public SearchFeedVo searchContent(String keyword, String cursor, int size, List<String> tags) {
        return esService.search(keyword, cursor, normalizeSize(size), tags);
    }

    @Override
    public FeedVo listContent(QueryDto query) {
        QueryDto safeQuery = query == null ? new QueryDto() : query;
        if (safeQuery.getSize() == null || safeQuery.getSize() <= 0) {
            safeQuery.setSize(10);
        }
        return logService.queryLogList(safeQuery);
    }

    @Override
    public List<MyContentVo> listMyContent(Integer userId, Integer size) {
        return logService.listMyContent(userId, size);
    }

    @Override
    public LogDetailVo getContentDetail(Integer logId) {
        if (logId == null || logId <= 0) {
            return null;
        }
        return logService.findById(logId);
    }

    @Override
    public List<LikeTop3Vo> getTopLikers(Integer logId) {
        if (logId == null || logId <= 0) {
            return List.of();
        }
        return likeService.getTop3Likers(logId);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 50);
    }
}
