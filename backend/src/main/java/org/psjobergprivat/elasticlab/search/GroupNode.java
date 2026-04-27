package org.psjobergprivat.elasticlab.search;

import java.util.List;

public record GroupNode(Operator operator, List<QueryNode> children) implements QueryNode {

    public enum Operator {
        AND, OR
    }
}
