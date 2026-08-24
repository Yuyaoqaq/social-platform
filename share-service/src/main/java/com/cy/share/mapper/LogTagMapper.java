package com.cy.share.mapper;

import com.cy.share.pojo.LogTag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 标签表（log_tag）Mapper，用于按图文 ID 批量查询标签。
 */
public interface LogTagMapper {

    List<LogTag> selectByLogIds(@Param("logIds") List<Long> logIds);
}
