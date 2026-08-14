package com.cy.share.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeedVo implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<LogListVo> list;
    private Long nextCursor;
}
