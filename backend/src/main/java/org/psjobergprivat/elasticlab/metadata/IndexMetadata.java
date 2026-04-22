package org.psjobergprivat.elasticlab.metadata;

import java.util.Map;

public record IndexMetadata(String name, long documentCount, String indexVersion, Map<String, Object> mappings) {
}
