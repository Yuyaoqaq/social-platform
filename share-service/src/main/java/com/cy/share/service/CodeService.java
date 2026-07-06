package com.cy.share.service;

public interface CodeService {
    void save(String phone, String code);
    boolean verify(String phone, String code);
}
