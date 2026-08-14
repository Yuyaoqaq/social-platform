package com.cy.share.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueryDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long cursor;
    private Integer size;
    private String author;
    /** 标签精确匹配过滤 */
    private List<String> tags;
}
