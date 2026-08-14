package com.cy.share.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cy.share.pojo.LogTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签表（log_tag）的 MyBatis-Plus Mapper，为 AI 打标结果提供持久化读写。
 */
@Mapper
public interface LogTagMapper extends BaseMapper<LogTag> {
}
