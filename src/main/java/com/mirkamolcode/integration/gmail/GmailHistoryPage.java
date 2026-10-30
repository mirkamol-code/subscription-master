package com.mirkamolcode.integration.gmail;

import java.util.List;

public record GmailHistoryPage(List<String> addedMessageIds, String nextPageToken, String historyId) {
}
