package com.mirkamolcode.integration.gmail;

import com.fasterxml.jackson.databind.JsonNode;

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
                json.path("id").asText(),
                json.path("threadId").asText(),
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
        String mimeType = part.path("mimeType").asText();
        String filename = part.path("filename").asText("");
        JsonNode body = part.path("body");
        if (!filename.isEmpty() && body.hasNonNull("attachmentId")) {
            bodies.attachments.add(new GmailAttachment(body.get("attachmentId").asText(), filename, mimeType, body.path("size").asLong()));
        } else if (body.hasNonNull("data")) {
            if ("text/plain".equals(mimeType) && bodies.text == null)
                bodies.text = decode(body.get("data").asText());
            else if ("text/html".equals(mimeType) && bodies.html == null)
                bodies.html = decode(body.get("data").asText());
        }
        for (JsonNode child : part.path("parts"))
            collectParts(child, bodies);
    }

    private static String header(JsonNode payload, String name) {
        for (JsonNode header : payload.path("headers"))
            if (name.equalsIgnoreCase(header.path("name").asText()))
                return header.path("value").asText();
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
