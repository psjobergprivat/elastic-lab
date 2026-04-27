package org.psjobergprivat.elasticlab.search;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The user-supplied query expression as a tree.
 *
 * <p>Two structural nodes:
 * <ul>
 *   <li>{@link GroupNode} — combines its children with AND or OR. The list may be empty.</li>
 *   <li>{@link NotNode}   — negates exactly one child. The child may be {@code null}.</li>
 * </ul>
 *
 * <p>Four leaf nodes — each holds a single user-entered value:
 * <ul>
 *   <li>{@link PropertyNode}   — match a specific field path with a typed value.</li>
 *   <li>{@link TypeAllNode}    — match a value across every field of a given mapping type.</li>
 *   <li>{@link GlobalNode}     — match a value across every field.</li>
 *   <li>{@link FreeTextNode}   — raw Elasticsearch {@code query_string} expression.</li>
 * </ul>
 *
 * <p>A node may be "empty" (blank values, no children, null child). See
 * {@link QueryCompiler} for the full validity contract; the short version is
 * that empty nodes contribute no constraint and an empty tree compiles to
 * {@code match_all}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GroupNode.class, name = "group"),
        @JsonSubTypes.Type(value = NotNode.class, name = "not"),
        @JsonSubTypes.Type(value = PropertyNode.class, name = "property"),
        @JsonSubTypes.Type(value = TypeAllNode.class, name = "typeAll"),
        @JsonSubTypes.Type(value = GlobalNode.class, name = "global"),
        @JsonSubTypes.Type(value = FreeTextNode.class, name = "freeText")
})
public sealed interface QueryNode
        permits GroupNode, NotNode, PropertyNode, TypeAllNode, GlobalNode, FreeTextNode {
}
