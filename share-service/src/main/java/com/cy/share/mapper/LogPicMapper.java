package com.cy.share.mapper;

import com.cy.share.pojo.LogPic;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LogPicMapper {
    void insertBatch(@Param("list") List<LogPic> list);
    void deleteByLogId(@Param("logId") Integer logId);
    List<String> selectUrlsByLogId(@Param("logId") Integer logId);
    List<LogPic> selectByLogIds(@Param("logIds") List<Integer> logIds);
}
