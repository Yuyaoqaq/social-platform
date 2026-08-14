package com.cy.share.api;

import com.cy.share.dto.QueryDto;
import com.cy.share.vo.FeedVo;
import com.cy.share.vo.LikeTop3Vo;
import com.cy.share.vo.LogDetailVo;
import com.cy.share.vo.SearchFeedVo;

import java.util.List;

public interface OpsContentService {

    SearchFeedVo searchContent(String keyword, String cursor, int size, List<String> tags);

    FeedVo listContent(QueryDto query);

    LogDetailVo getContentDetail(Integer logId);

    List<LikeTop3Vo> getTopLikers(Integer logId);
}
