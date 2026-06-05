package com.cy.share.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "mq_failure_record")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MqFailureRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** PRODUCER / CONSUMER */
    private String failType;

    private String errorMsg;

    /** 操作描述，用于数据恢复，如 "ES同步-博客创建: logId=123" */
    private String operationDesc;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
