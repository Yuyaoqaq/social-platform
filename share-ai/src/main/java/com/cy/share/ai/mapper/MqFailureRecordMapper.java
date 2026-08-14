package com.cy.share.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cy.share.pojo.MqFailureRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * MQ 失败记录表的 MyBatis-Plus Mapper，用于持久化标签同步消息发送失败时的重试/排查记录。
 */
@Mapper
public interface MqFailureRecordMapper extends BaseMapper<MqFailureRecord> {
}
