package com.mirkamolcode.integration.gmail;

import java.util.Set;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

final class HtmlText {
    private static final Set<String> LINE_BREAKING_TAGS = Set.of(
            "br", "p", "div", "tr", "li", "ul", "ol", "table", "tbody", "thead", "tfoot",
            "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "section", "article", "header", "footer", "hr");
    private static final Set<String> CELL_TAGS = Set.of("td", "th");
    private static final String SPACES = "[\\s\\u00A0\\u2007\\u2009\\u202F]+";

    private HtmlText() {
    }

    static String toLines(String html) {
        StringBuilder text = new StringBuilder();
        Jsoup.parse(html).body().traverse(new LineWriter(text));
        return text.toString().lines()
                .map(line -> line.replaceAll(SPACES, " ").strip())
                .filter(line -> !line.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private record LineWriter(StringBuilder text) implements NodeVisitor {
        @Override
        public void head(Node node, int depth) {
            if (node instanceof TextNode textNode)
                text.append(textNode.text());
            else if (isLineBreaking(node))
                text.append('\n');
        }

        @Override
        public void tail(Node node, int depth) {
            if (isLineBreaking(node))
                text.append('\n');
            else if (node instanceof Element element && CELL_TAGS.contains(element.normalName()))
                text.append(' ');
        }

        private static boolean isLineBreaking(Node node) {
            return node instanceof Element element && LINE_BREAKING_TAGS.contains(element.normalName());
        }
    }
}
