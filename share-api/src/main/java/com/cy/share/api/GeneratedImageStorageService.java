package com.cy.share.api;

/**
 * 将外部模型生成的临时图片转存到平台 OSS。
 */
public interface GeneratedImageStorageService {

    String storeFromUrl(String sourceUrl, String userId);
}
