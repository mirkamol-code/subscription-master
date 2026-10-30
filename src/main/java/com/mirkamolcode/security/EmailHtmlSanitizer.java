package com.mirkamolcode.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class EmailHtmlSanitizer {
    private static final Pattern INLINE_IMAGE = Pattern.compile("(?i)^data:image/(png|jpe?g|gif|webp);base64,[a-z0-9+/=\\s]+$");
    private static final Pattern REMOTE_URL = Pattern.compile("(?i)^\\s*(https?:)?//.*");
    private static final Pattern PROPERTY_NAME = Pattern.compile("^[a-z-]+$");
    private static final Pattern FUNCTION_CALL = Pattern.compile("(?i)([a-z-]*)\\s*\\(");
    private static final Pattern FORBIDDEN_VALUE = Pattern.compile("(?i)url|image-set|expression|javascript|@");
    private static final Set<String> ALLOWED_FUNCTIONS = Set.of("rgb", "rgba", "hsl", "hsla", "calc");
    private static final Set<String> ALLOWED_PROPERTIES = Set.of(
            "color", "background-color", "font", "font-family", "font-size", "font-style", "font-weight", "font-variant",
            "text-align", "text-decoration", "text-transform", "text-indent", "line-height", "letter-spacing", "word-spacing",
            "white-space", "vertical-align", "direction",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
            "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
            "border", "border-top", "border-right", "border-bottom", "border-left", "border-color", "border-style",
            "border-width", "border-radius", "border-collapse", "border-spacing",
            "width", "min-width", "max-width", "height", "min-height", "max-height",
            "display", "table-layout", "list-style-type");

    private final Safelist safelist = Safelist.relaxed()
            .addAttributes(":all", "style")
            .addProtocols("img", "src", "http", "https", "data");

    public SanitizedEmail sanitize(String html, boolean allowRemoteImages) {
        Document original = Jsoup.parse(html == null ? "" : html);
        int blocked = countBlockedRemoteImages(original, allowRemoteImages);
        Document cleaned = new Cleaner(safelist).clean(original);
        removeDisallowedImages(cleaned, allowRemoteImages);
        cleaned.select("[style]").forEach(EmailHtmlSanitizer::keepOnlySafeDeclarations);
        cleaned.select("a[href]").forEach(link -> link.attr("rel", "noopener noreferrer").attr("target", "_blank"));
        return new SanitizedEmail(cleaned.body().html(), blocked);
    }

    private static int countBlockedRemoteImages(Document document, boolean allowRemoteImages) {
        return (int) document.select("img[src]").stream()
                .map(image -> image.attr("src"))
                .filter(src -> REMOTE_URL.matcher(src).matches())
                .filter(src -> !(allowRemoteImages && isHttps(src)))
                .count();
    }

    private static void removeDisallowedImages(Document document, boolean allowRemoteImages) {
        for (Element image : document.select("img")) {
            String src = image.attr("src").trim();
            boolean keep = INLINE_IMAGE.matcher(src).matches() || (allowRemoteImages && isHttps(src));
            if (!keep)
                image.remove();
        }
    }

    private static boolean isHttps(String src) {
        return src.trim().toLowerCase(Locale.ROOT).startsWith("https://");
    }

    private static void keepOnlySafeDeclarations(Element element) {
        String safe = Arrays.stream(element.attr("style").split(";"))
                .map(String::trim)
                .filter(declaration -> !declaration.isEmpty())
                .filter(EmailHtmlSanitizer::isSafeDeclaration)
                .collect(Collectors.joining(";"));
        if (safe.isEmpty())
            element.removeAttr("style");
        else
            element.attr("style", safe);
    }

    private static boolean isSafeDeclaration(String declaration) {
        int colon = declaration.indexOf(':');
        if (colon < 0)
            return false;
        String property = declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String value = declaration.substring(colon + 1);
        return PROPERTY_NAME.matcher(property).matches()
                && ALLOWED_PROPERTIES.contains(property)
                && value.indexOf('\\') < 0
                && !FORBIDDEN_VALUE.matcher(value).find()
                && usesOnlyAllowedFunctions(value);
    }

    private static boolean usesOnlyAllowedFunctions(String value) {
        Matcher call = FUNCTION_CALL.matcher(value);
        while (call.find())
            if (!ALLOWED_FUNCTIONS.contains(call.group(1).toLowerCase(Locale.ROOT)))
                return false;
        return true;
    }
}
