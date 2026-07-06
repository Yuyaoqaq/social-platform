package com.cy.share.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cy.share.dto.EsSyncMessage;
import com.cy.share.mapper.LogMapper;
import com.cy.share.mapper.LogPicMapper;
import com.cy.share.mapper.UserMapper;
import com.cy.share.pojo.Log;
import com.cy.share.pojo.LogEsDocument;
import com.cy.share.pojo.User;
import com.cy.share.service.EsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "es-sync-topic",
        consumerGroup = "share-es-sync-consumer",
        consumeMode = ConsumeMode.ORDERLY,//顺序消费vs并发消费
        maxReconsumeTimes = 3
)
public class EsSyncConsumer implements RocketMQListener<EsSyncMessage> {

    private final LogMapper logMapper;
    private final LogPicMapper logPicMapper;
    private final UserMapper userMapper;
    private final EsService esService;

    @Override
    public void onMessage(EsSyncMessage msg) {
        Integer logId = msg.getLogId();
        log.debug("MQ received: logId={}, type={}", logId, msg.getType());
        try {
            switch (msg.getType()) {
                case "BLOG_CREATE":
                case "BLOG_UPDATE":
                    syncFullDocument(logId);
                    break;
                case "BLOG_LIKE":
                case "BLOG_UNLIKE":
                    syncLoveField(logId);
                    break;
                case "BLOG_TAG":
                    esService.updateTags(logId, msg.getTags());
                    break;
            }
        } catch (Exception e) {
            log.error("MQ consume failed: logId={}, type={}", logId, msg.getType(), e);
            throw e; // RocketMQ 重试
        }
    }

    private void syncFullDocument(Integer logId) {
        Log log = logMapper.selectById(logId);
        if (log == null || log.getDeleted() != 1) {
            return;
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getName, log.getAuthor()).last("LIMIT 1"));
        List<String> picurls = logPicMapper.selectUrlsByLogId(logId);

        LogEsDocument doc = new LogEsDocument();
        doc.setId(log.getId());
        doc.setTitle(log.getTitle());
        doc.setInfo(log.getInfo());
        doc.setAuthor(log.getAuthor());
        doc.setAuthorAvatar(user != null ? user.getAvatorurl() : null);
        doc.setLove(log.getLove());
        doc.setPicurls(picurls);
        doc.setCreateTime(log.getCreateTime());
        doc.setCreateTimeMs(log.getCreateTime() != null
                ? log.getCreateTime().getTime() : System.currentTimeMillis());

        esService.saveLog(doc);
    }

    private void syncLoveField(Integer logId) {
        Log log = logMapper.selectById(logId);
        if (log != null && log.getDeleted() == 1) {
            esService.updateLove(logId, log.getLove());
        }
    }
}
