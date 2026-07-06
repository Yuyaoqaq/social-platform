package com.cy.share.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@TableName("log_tag")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long logId;
    private String tagName;
    private Date createTime;
}
