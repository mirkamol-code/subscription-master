package com.mirkamolcode.integration.gmail;

public record MessageHeaders(
        String listId,
        String listUnsubscribe,
        String authenticationResults
) {
    public static MessageHeaders none() {
        return new MessageHeaders(null, null, null);
    }

    public boolean isMailingList() {
        return listId != null && !listId.isBlank();
    }

    public boolean hasUnsubscribeLink() {
        return listUnsubscribe != null && !listUnsubscribe.isBlank();
    }

    public MessageHeaders withAuthenticationResults(String results) {
        return new MessageHeaders(listId, listUnsubscribe, results);
    }
}
