package com.cy.share.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogEditVo implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<String> picurls;
    private String title;
    private Integer love;
    private String author;
    private String info;
}
