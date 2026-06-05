package com.cy.share.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LikeTop3Vo implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private String name;
    private String avatarUrl;
}
