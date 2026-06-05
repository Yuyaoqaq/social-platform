package com.cy.share.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReleaseDto {
    private Integer id;
    private List<String> picurls;
    private String title;
    private Integer love;
    private String author;
    private String info;
}
