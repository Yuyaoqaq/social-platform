package com.cy.share.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiTagResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer logId;
    private List<String> tags;
}
