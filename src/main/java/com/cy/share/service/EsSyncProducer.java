package com.cy.share.service;

import com.cy.share.dto.EsSyncMessage;
import com.cy.share.mapper.MqFailureRecordMapper;
import com.cy.share.pojo.MqFailureRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EsSyncProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final MqFailureRecordMapper failureRecordMapper;

    private static final String TOPIC = "es-sync-topic";

    private static final String DESC_CREATE = "ES同步-博客创建";
    private static final String DESC_UPDATE = "ES同步-博客更新";
    private static final String DESC_LIKE = "ES同步-点赞";
    private static final String DESC_UNLIKE = "ES同步-取消点赞";

    public void sendBlogCreate(Integer logId) {
        send(logId, "BLOG_CREATE", DESC_CREATE);
    }

    public void sendBlogUpdate(Integer logId) {
        send(logId, "BLOG_UPDATE", DESC_UPDATE);
    }

    public void sendBlogLike(Integer logId) {
        send(logId, "BLOG_LIKE", DESC_LIKE);
    }

    public void sendBlogUnlike(Integer logId) {
        send(logId, "BLOG_UNLIKE", DESC_UNLIKE);
    }

    private void send(Integer logId, String type, String operationDesc) {
        EsSyncMessage msg = new EsSyncMessage(type, logId);
        try {
            rocketMQTemplate.syncSendOrderly(TOPIC, msg, logId.toString());
            log.debug("MQ sent: logId={}, type={}", logId, type);
        } catch (Exception e) {
            log.error("MQ send failed: logId={}, type={}", logId, type, e);
            saveFailure("PRODUCER", e, operationDesc + ": logId=" + logId);
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
