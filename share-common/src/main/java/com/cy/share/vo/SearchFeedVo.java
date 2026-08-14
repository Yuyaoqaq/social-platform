package com.cy.share.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchFeedVo implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<LogListVo> list;
    /** search_after 游标，格式 "createTimeMs_id"，末页为 null */
    private String nextCursor;
}
