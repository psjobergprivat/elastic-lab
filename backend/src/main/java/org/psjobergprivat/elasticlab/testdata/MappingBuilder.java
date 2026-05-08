package org.psjobergprivat.elasticlab.testdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MappingBuilder {

    private static final Map<String, String> JOIN_RELATIONS = Map.of("parent", "child");

    @Inject
    FieldCatalog catalog;

    @Inject
    ObjectMapper objectMapper;

    public String build() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("dynamic_templates", buildTemplates());
        body.put("mappings", mappings);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize index mapping", e);
        }
    }

    private List<Map<String, Object>> buildTemplates() {
        List<Map<String, Object>> templates = new ArrayList<>();
        for (FieldDefinition fd : catalog.all()) {
            templates.add(Map.of("field_" + fd.name(), buildTemplate(fd)));
        }
        return templates;
    }

    private Map<String, Object> buildTemplate(FieldDefinition fd) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("match", fd.name());
        String matchType = matchMappingTypeFor(fd.type());
        if (matchType != null) {
            template.put("match_mapping_type", matchType);
        }
        template.put("mapping", buildTypeMapping(fd));
        return template;
    }

    private Map<String, Object> buildTypeMapping(FieldDefinition fd) {
        Map<String, Object> typeMapping = new LinkedHashMap<>();
        typeMapping.put("type", fd.type());
        if ("constant_keyword".equals(fd.type())) {
            String src = fd.source();
            if (src != null && src.startsWith("constant:")) {
                typeMapping.put("value", src.substring("constant:".length()));
            }
        } else if ("join".equals(fd.type())) {
            typeMapping.put("relations", JOIN_RELATIONS);
        }
        return typeMapping;
    }

    private String matchMappingTypeFor(String esType) {
        return switch (esType) {
            case "long_range", "double_range", "date_range", "ip_range",
                 "flattened", "join", "object", "nested" -> "object";
            default -> null;
        };
    }
}
