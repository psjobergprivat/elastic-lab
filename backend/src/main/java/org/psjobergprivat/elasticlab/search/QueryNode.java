package org.psjobergprivat.elasticlab.search;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GroupNode.class, name = "group"),
        @JsonSubTypes.Type(value = PropertyNode.class, name = "property"),
        @JsonSubTypes.Type(value = TypeAllNode.class, name = "typeAll"),
        @JsonSubTypes.Type(value = GlobalNode.class, name = "global"),
        @JsonSubTypes.Type(value = FreeTextNode.class, name = "freeText")
})
public sealed interface QueryNode
        permits GroupNode, PropertyNode, TypeAllNode, GlobalNode, FreeTextNode {
}
