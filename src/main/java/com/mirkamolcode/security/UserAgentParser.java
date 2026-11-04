package com.mirkamolcode.security;

public final class UserAgentParser {

    private UserAgentParser() {
    }

    public record ClientInfo(String device, String browser, String os) {
    }

    public static ClientInfo parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ClientInfo("Unknown", "Unknown", "Unknown");
        }
        String os = detectOperatingSystem(userAgent);
        String device = detectDevice(userAgent, os);
        String browser = detectBrowser(userAgent);
        return new ClientInfo(device, browser, os);
    }

    private static String detectOperatingSystem(String userAgent) {
        if (userAgent.contains("Windows")) {
            return "Windows";
        }
        if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iPod")) {
            return "iOS";
        }
        if (userAgent.contains("Android")) {
            return "Android";
        }
        if (userAgent.contains("Mac OS X") || userAgent.contains("Macintosh")) {
            return "macOS";
        }
        if (userAgent.contains("CrOS")) {
            return "ChromeOS";
        }
        if (userAgent.contains("Linux") || userAgent.contains("X11")) {
            return "Linux";
        }
        return "Unknown";
    }

    private static String detectDevice(String userAgent, String os) {
        if (userAgent.contains("iPad") || userAgent.contains("Tablet")
                || ("Android".equals(os) && !userAgent.contains("Mobile"))) {
            return "Tablet";
        }
        if (userAgent.contains("Mobile") || userAgent.contains("iPhone") || userAgent.contains("iPod")
                || "Android".equals(os) || userAgent.contains("Windows Phone")) {
            return "Mobile";
        }
        return "Desktop";
    }

    private static String detectBrowser(String userAgent) {
        if (userAgent.contains("Edg/") || userAgent.contains("Edge/")) {
            return "Edge";
        }
        if (userAgent.contains("OPR/") || userAgent.contains("Opera/")) {
            return "Opera";
        }
        if (userAgent.contains("SamsungBrowser/")) {
            return "Samsung Internet";
        }
        if (userAgent.contains("UCBrowser/")) {
            return "UC Browser";
        }
        if (userAgent.contains("Firefox/") || userAgent.contains("FxiOS/")) {
            return "Firefox";
        }
        if (userAgent.contains("Chrome/") || userAgent.contains("CriOS/")) {
            return "Chrome";
        }
        if (userAgent.contains("Safari/") || userAgent.contains("AppleWebKit/")) {
            return "Safari";
        }
        return "Unknown";
    }
}
