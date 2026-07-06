package com.cy.share.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Date;
import java.util.List;

/**
 * ES 文档，存储博客列表展示所需的全量字段。
 * sort 字段：[createTimeMs, id]，用于 search_after 深分页。
 */
@Data
@NoArgsConstructor
public class LogEsDocument {
    private Integer id;
    private String title;
    private String info;
    private String author;
    private String authorAvatar;
    private Integer love;
    private List<String> picurls;
    private List<String> tags;
    @JsonIgnore
    private Date createTime;
    /** createTime 的毫秒时间戳，作为 ES sort 字段 */
    private Long createTimeMs;
}
