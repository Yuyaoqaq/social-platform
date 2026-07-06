package com.cy.share.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginVo {
    private String name;
    private String avatorurl;
    private String ip;
    private String sign;
    private String accessToken;
}
