package org.psjobergprivat.elasticlab.search;

public record PropertyNode(String path, String valueType, String value) implements QueryNode {
}
