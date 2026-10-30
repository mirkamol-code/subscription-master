package com.mirkamolcode.integration.gmail;

public interface GmailClient {
    GmailMessagePage listMessageIds(String accessToken, String query, String pageToken);

    GmailMessage getMessage(String accessToken, String messageId);

    GmailMessage getMessageHeaders(String accessToken, String messageId);

    byte[] getAttachment(String accessToken, String messageId, String attachmentId);

    GmailHistoryPage listHistory(String accessToken, String startHistoryId, String pageToken);

    String fetchCurrentHistoryId(String accessToken);
}
