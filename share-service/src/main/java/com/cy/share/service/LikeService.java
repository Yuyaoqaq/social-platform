package com.cy.share.service;

import com.cy.share.vo.LikeTop3Vo;
import com.cy.share.vo.LogListVo;

import java.util.List;
import java.util.Map;

public interface LikeService {

    void like(Integer userId, Integer logId);

    void unlike(Integer userId, Integer logId);

    boolean isLiked(Integer userId, Integer logId);

    Map<Integer, Boolean> isLikedBatch(Integer userId, List<Integer> logIds);

    List<LikeTop3Vo> getTop3Likers(Integer logId);

    List<LogListVo> getUserLikedLogs(Integer userId, int page, int size);
}
