package com.cy.share.service;

public interface NotificationService {
    /**
     * Like event notification.
     * @param likerId the user who liked
     * @param logId   the blog post that was liked
     */
    void onLike(Integer likerId, Integer logId);
}
