package com.cy.share.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EsSyncMessage {
    /** BLOG_CREATE / BLOG_UPDATE / BLOG_LIKE / BLOG_UNLIKE / BLOG_TAG */
    private String type;
    private Integer logId;
    /** BLOG_TAG 类型时携带标签列表 */
    private List<String> tags;
}
