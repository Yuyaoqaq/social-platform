package com.cy.share.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EsSyncMessage {
    /** BLOG_CREATE / BLOG_UPDATE / BLOG_LIKE / BLOG_UNLIKE */
    private String type;
    private Integer logId;
}
