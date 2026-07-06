package com.cy.share.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cy.share.mapper.MqFailureRecordMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @XxlJob("mqFailureAlertJob")
    public void scanAndAlert() {
        Long count = failureRecordMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > ALERT_THRESHOLD) {
            String msg = String.format("[MQ告警] mq_failure_record 表累积 %d 条失败记录，超过阈值 %d，请及时处理！", count, ALERT_THRESHOLD);
            log.error(msg);
            XxlJobHelper.log(msg);
            XxlJobHelper.handleFail(msg);
        } else {
            String msg = String.format("[MQ巡检] mq_failure_record 表当前 %d 条记录，正常", count);
            log.info(msg);
            XxlJobHelper.log(msg);
            XxlJobHelper.handleSuccess();
        }
    }
}
