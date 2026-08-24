package com.cy.share.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 当前登录用户发布过的图文（含正文），用于分析作者创作风格。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MyContentVo implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private String title;
    private String info;
    private List<String> tags;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
