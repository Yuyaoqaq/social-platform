package com.cy.share.ai.mq;

import com.cy.share.ai.mapper.MqFailureRecordMapper;
import com.cy.share.dto.EsSyncMessage;
import com.cy.share.pojo.MqFailureRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TagSyncProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final MqFailureRecordMapper failureRecordMapper;

    private static final String TOPIC = "es-sync-topic";
    private static final String DESC_TAG = "AI标签同步-BlogTag";

    public void sendBlogTag(Integer logId, List<String> tags) {
        EsSyncMessage msg = new EsSyncMessage("BLOG_TAG", logId, tags);
        try {
            rocketMQTemplate.syncSendOrderly(TOPIC, msg, logId.toString());
            log.debug("BLOG_TAG sent: logId={}, tags={}", logId, tags);
        } catch (Exception e) {
            log.error("BLOG_TAG send failed: logId={}", logId, e);
            saveFailure("PRODUCER", e, DESC_TAG + ": logId=" + logId);
        }
    }

    private void saveFailure(String failType, Exception e, String operationDesc) {
        try {
            MqFailureRecord record = new MqFailureRecord();
            record.setFailType(failType);
            record.setErrorMsg(stackTraceToString(e));
            record.setOperationDesc(operationDesc);
            failureRecordMapper.insert(record);
        } catch (Exception ex) {
            log.error("Failed to save MQ failure record", ex);
        }
    }

    private String stackTraceToString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
