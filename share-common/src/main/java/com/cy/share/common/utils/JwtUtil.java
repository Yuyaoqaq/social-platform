package com.cy.share.common.utils;

import com.cy.share.common.constant.Jwtconstant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;

public class JwtUtil {

    public static String createAccessToken(String userId, PrivateKey privateKey) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(userId)
                .subject(userId)
                .issuer("cyy")
                .issuedAt(new Date(now))
                .expiration(new Date(now + Jwtconstant.ACCESS_TOKEN_TTL))
                .signWith(privateKey)
                .compact();
    }

    public static JwtResult validateAccessToken(String token, PublicKey publicKey) {
        JwtResult result = new JwtResult();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            result.setSuccess(true);
            result.setClaims(claims);
        } catch (ExpiredJwtException e) {
            result.setSuccess(false);
            result.setErrCode(Jwtconstant.JWT_ERRCODE_EXPIRE);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrCode(Jwtconstant.JWT_ERRCODE_FAIL);
        }
        return result;
    }
}
