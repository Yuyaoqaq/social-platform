package com.cy.share.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiTagRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer logId;
    private String title;
    private String info;
    private List<String> imageUrls;
}
