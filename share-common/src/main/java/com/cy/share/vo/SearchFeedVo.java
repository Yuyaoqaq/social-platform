package com.cy.share.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchFeedVo {
    private List<LogListVo> list;
    /** search_after 游标，格式 "createTimeMs_id"，末页为 null */
    private String nextCursor;
}
