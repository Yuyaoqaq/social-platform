package com.cy.share.mq;

import com.cy.share.dto.EsSyncMessage;
import com.cy.share.mapper.MqFailureRecordMapper;
import com.cy.share.pojo.MqFailureRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 死信队列消费者：重试耗尽后落库，不抛异常避免无限循环
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "%DLQ%share-es-sync-consumer",
        consumerGroup = "share-es-sync-dlq-consumer"
)
public class EsSyncDlqConsumer implements RocketMQListener<EsSyncMessage> {

    private final MqFailureRecordMapper failureRecordMapper;

    @Override
    public void onMessage(EsSyncMessage msg) {
        Integer logId = msg.getLogId();
        String type = msg.getType();
        log.warn("DLQ received: logId={}, type={}", logId, type);

        String desc = buildDesc(type, logId);
        try {
            MqFailureRecord record = new MqFailureRecord();
            record.setFailType("CONSUMER");
            record.setErrorMsg("消费重试3次后进入死信队列");
            record.setOperationDesc(desc);
            failureRecordMapper.insert(record);
            log.info("DLQ failure recorded: logId={}, type={}", logId, type);
        } catch (Exception e) {
            log.error("Failed to save DLQ failure record: logId={}, type={}", logId, type, e);
        }
    }

    private String buildDesc(String type, Integer logId) {
        switch (type) {
            case "BLOG_CREATE":
                return "ES同步-博客创建: logId=" + logId;
            case "BLOG_UPDATE":
                return "ES同步-博客更新: logId=" + logId;
            case "BLOG_LIKE":
                return "ES同步-点赞: logId=" + logId;
            case "BLOG_UNLIKE":
                return "ES同步-取消点赞: logId=" + logId;
            default:
                return "ES同步-" + type + ": logId=" + logId;
        }
    }
}
