package com.cy.share.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogListVo implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private List<String> picurls;
    private String title;
    private String authorAvatar;
    private Integer love;
    private String author;
    private List<String> tags;
    private Boolean isLiked;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
