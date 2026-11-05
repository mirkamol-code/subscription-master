package com.mirkamolcode.integration.gmail;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class GmailMessageMapper {
    private GmailMessageMapper() {
    }

    public static GmailMessage toMessage(JsonNode json) {
        JsonNode payload = json.path("payload");
        Bodies bodies = new Bodies();
        collectParts(payload, bodies);
        return new GmailMessage(
                json.path("id").asString(),
                json.path("threadId").asString(),
                Instant.ofEpochMilli(json.path("internalDate").asLong()),
                header(payload, "From"),
                header(payload, "Subject"),
                bodies.html,
                bodies.text,
                bodies.attachments,
                new MessageHeaders(
                        header(payload, "List-Id"),
                        header(payload, "List-Unsubscribe"),
                        header(payload, "Authentication-Results")));
    }

    private static void collectParts(JsonNode part, Bodies bodies) {
        String mimeType = part.path("mimeType").asString();
        String filename = part.path("filename").asString("");
        JsonNode body = part.path("body");
        if (!filename.isEmpty() && body.hasNonNull("attachmentId")) {
            bodies.attachments.add(new GmailAttachment(body.get("attachmentId").asString(), filename, mimeType, body.path("size").asLong()));
        } else if (body.hasNonNull("data")) {
            if ("text/plain".equals(mimeType) && bodies.text == null)
                bodies.text = decode(body.get("data").asString());
            else if ("text/html".equals(mimeType) && bodies.html == null)
                bodies.html = decode(body.get("data").asString());
        }
        for (JsonNode child : part.path("parts"))
            collectParts(child, bodies);
    }

    private static String header(JsonNode payload, String name) {
        for (JsonNode header : payload.path("headers"))
            if (name.equalsIgnoreCase(header.path("name").asString()))
                return header.path("value").asString();
        return null;
    }

    private static String decode(String base64Url) {
        return new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8);
    }

    private static final class Bodies {
        private String text;
        private String html;
        private final List<GmailAttachment> attachments = new ArrayList<>();
    }
}
