package com.mirkamolcode.dto.response;

public record GmailConnectionResponse(
        boolean connected,
        String gmailAddress,
        State status
) {
    public enum State {
        CONNECTED,
        DISCONNECTED,
        NOT_CONNECTED
    }

    public static GmailConnectionResponse notConnected() {
        return new GmailConnectionResponse(false, null, State.NOT_CONNECTED);
    }
}
