package com.cy.share.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LikeNotificationMessage {
    /** "like" for first push / "like_aggregated" for aggregated push */
    private String type;
    private Integer blogId;
    private String blogTitle;
    private Integer count;
    private String message;
    private Long timestamp;
}
