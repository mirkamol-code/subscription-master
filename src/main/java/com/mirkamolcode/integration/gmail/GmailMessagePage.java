package com.mirkamolcode.integration.gmail;

import java.util.List;

public record GmailMessagePage(List<String> messageIds, String nextPageToken) {
}
