package com.mirkamolcode.controller;

import java.nio.charset.StandardCharsets;

final class ContentDispositionHeader {
    private static final String RFC_5987_SYMBOLS = "!#$&+-.^_`|~";

    private ContentDispositionHeader() {
    }

    static String build(boolean download, String filename) {
        String clean = filename.replaceAll("\\p{Cntrl}", "");
        StringBuilder asciiFallback = new StringBuilder();
        clean.codePoints().forEach(codePoint -> asciiFallback.append(isFallbackSafe(codePoint) ? (char) codePoint : '_'));
        return (download ? "attachment" : "inline") + "; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encodeRfc5987(clean);
    }

    private static boolean isFallbackSafe(int codePoint) {
        return isAsciiAlphanumeric(codePoint) || codePoint == ' ' || codePoint == '.' || codePoint == '_' || codePoint == '-';
    }

    private static String encodeRfc5987(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = b & 0xFF;
            if (isAsciiAlphanumeric(unsigned) || RFC_5987_SYMBOLS.indexOf(unsigned) >= 0)
                encoded.append((char) unsigned);
            else
                encoded.append('%').append(String.format("%02X", unsigned));
        }
        return encoded.toString();
    }

    private static boolean isAsciiAlphanumeric(int codePoint) {
        return (codePoint >= 'a' && codePoint <= 'z') || (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= '0' && codePoint <= '9');
    }
}
