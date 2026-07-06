package com.cy.share.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName(value = "log_pic")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogPic {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer logId;
    private String picurl;
    private Integer sort;
}
