package com.cy.share.common.utils;

import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

@Component
public class RsaKeyHolder {

    private final PrivateKey privateKey;
    private final PublicKey  publicKey;

    public RsaKeyHolder() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            this.privateKey = pair.getPrivate();
            this.publicKey  = pair.getPublic();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 密钥对生成失败", e);
        }
    }

    public PrivateKey getPrivateKey() { return privateKey; }
    public PublicKey  getPublicKey()  { return publicKey;  }
}
