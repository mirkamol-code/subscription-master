package com.mirkamolcode.service;

import com.mirkamolcode.dto.response.GmailConnectionResponse;
import com.mirkamolcode.entity.GmailConnection;
import com.mirkamolcode.repository.GmailConnectionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GmailConnectionService {
    private final GmailConnectionRepository connections;
    private final CurrentUserService currentUser;

    public GmailConnectionService(GmailConnectionRepository connections, CurrentUserService currentUser) {
        this.connections = connections;
        this.currentUser = currentUser;
    }

    public GmailConnectionResponse findStatus() {
        return connections.findByUserId(currentUser.requiredUser().getId())
                .map(GmailConnectionService::mapToGmailConnectionResponse)
                .orElseGet(GmailConnectionResponse::notConnected);
    }

    private static GmailConnectionResponse mapToGmailConnectionResponse(GmailConnection connection) {
        GmailConnectionResponse.State state = connection.isConnected()
                ? GmailConnectionResponse.State.CONNECTED
                : GmailConnectionResponse.State.DISCONNECTED;
        return new GmailConnectionResponse(connection.isConnected(), connection.getGmailAddress(), state);
    }
}
