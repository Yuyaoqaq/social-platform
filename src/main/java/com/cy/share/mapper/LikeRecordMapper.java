package com.cy.share.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cy.share.pojo.LikeRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LikeRecordMapper extends BaseMapper<LikeRecord> {

    int insertIgnore(LikeRecord record);

    int deleteByUserAndLog(@Param("userId") Integer userId, @Param("logId") Integer logId);

    int countByUserIdAndLogId(@Param("userId") Integer userId, @Param("logId") Integer logId);

    List<LikeRecord> selectByLogId(@Param("logId") Integer logId);

    List<LikeRecord> selectByUserId(@Param("userId") Integer userId);
}
