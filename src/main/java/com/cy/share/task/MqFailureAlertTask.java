package com.cy.share.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cy.share.mapper.MqFailureRecordMapper;
import com.cy.share.pojo.MqFailureRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日凌晨2点扫描 mq_failure_record 表，超过10条打印告警
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqFailureAlertTask {

    private final MqFailureRecordMapper failureRecordMapper;

    private static final int ALERT_THRESHOLD = 10;

    @Scheduled(cron = "0 0 2 * * ?")
    public void scanAndAlert() {
        Long count = failureRecordMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > ALERT_THRESHOLD) {
            log.error("[MQ告警] mq_failure_record 表累积 {} 条失败记录，超过阈值 {}，请及时处理！", count, ALERT_THRESHOLD);
        } else {
            log.info("[MQ巡检] mq_failure_record 表当前 {} 条记录，正常", count);
        }
    }
}
