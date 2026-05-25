package org.psjobergprivat.elasticlab.testdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.psjobergprivat.elasticlab.phone.PhoneFields;

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
        body.put("settings", buildSettings());
        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put("dynamic_templates", buildTemplates());
        mappings.put("properties", buildExplicitProperties());
        body.put("mappings", mappings);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize index mapping", e);
        }
    }

    // ---------- settings / analysis ----------

    private Map<String, Object> buildSettings() {
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("char_filter", buildCharFilters());
        analysis.put("filter", buildTokenFilters());
        analysis.put("analyzer", buildAnalyzers());
        analysis.put("normalizer", buildNormalizers());
        return Map.of("analysis", analysis);
    }

    private Map<String, Object> buildCharFilters() {
        Map<String, Object> charFilters = new LinkedHashMap<>();
        charFilters.put("phone_digit_strip", patternReplace("[^0-9]", ""));
        return charFilters;
    }

    private Map<String, Object> buildTokenFilters() {
        Map<String, Object> tokenFilters = new LinkedHashMap<>();
        tokenFilters.put("phone_ngram_filter", Map.of(
                "type", "edge_ngram",
                "min_gram", 1,
                "max_gram", 15));
        tokenFilters.put("email_tag_strip", patternReplace("\\+[^@]+", ""));
        return tokenFilters;
    }

    private Map<String, Object> buildAnalyzers() {
        Map<String, Object> analyzers = new LinkedHashMap<>();
        analyzers.put("phone_digits", Map.of(
                "type", "custom",
                "char_filter", List.of("phone_digit_strip"),
                "tokenizer", "keyword"));
        analyzers.put("phone_ngram", Map.of(
                "type", "custom",
                "char_filter", List.of("phone_digit_strip"),
                "tokenizer", "keyword",
                "filter", List.of("phone_ngram_filter")));
        return analyzers;
    }

    private Map<String, Object> buildNormalizers() {
        Map<String, Object> normalizers = new LinkedHashMap<>();
        normalizers.put("email_normalizer", Map.of(
                "type", "custom",
                "filter", List.of("lowercase", "email_tag_strip")));
        // phone_all stores keyword-style exact matches of digits. Normalizer mirrors the
        // phone_digits analyzer (char filter only, implicit keyword tokenisation) so that
        // doc_values and term lookups handle equality without going through text scoring.
        normalizers.put("phone_digits_normalizer", Map.of(
                "type", "custom",
                "char_filter", List.of("phone_digit_strip")));
        return normalizers;
    }

    private Map<String, Object> patternReplace(String pattern, String replacement) {
        return Map.of(
                "type", "pattern_replace",
                "pattern", pattern,
                "replacement", replacement);
    }

    // ---------- explicit properties ----------

    private Map<String, Object> buildExplicitProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("phone_all", phoneAllField());
        properties.put(PhoneFields.CANONICAL, keywordField());
        properties.put(PhoneFields.SUBSCRIBER, keywordField());
        properties.put(PhoneFields.SUBSCRIBER_NATIONAL, keywordField());
        properties.put("email_all", Map.of("type", "keyword", "normalizer", "email_normalizer"));
        return properties;
    }

    private Map<String, Object> phoneAllField() {
        // Parent: keyword + normalizer is cheaper than text + analyzer for exact-digit
        // matching — uses doc_values, no scoring overhead, and the smart-match scenarios
        // never need TF/IDF on a digit catchall.
        // Sub-field stays plain text: edge_ngram prefix search needs distinct
        // analyzer/search_analyzer, and the typed match_only_text field only accepts
        // analyzer (not search_analyzer), so it can't model this use case cleanly.
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "keyword");
        field.put("normalizer", "phone_digits_normalizer");
        field.put("fields", Map.of("ngram", Map.of(
                "type", "text",
                "analyzer", "phone_ngram",
                "search_analyzer", "phone_digits")));
        return field;
    }

    private Map<String, Object> keywordField() {
        return Map.of("type", "keyword");
    }

    // ---------- dynamic templates ----------

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

        if (fd.source().startsWith("email:")) {
            typeMapping.put("type", "keyword");
            typeMapping.put("normalizer", "email_normalizer");
            typeMapping.put("copy_to", "email_all");
            return typeMapping;
        }

        if (fd.source().startsWith("phone:")) {
            typeMapping.put("type", "keyword");
            typeMapping.put("copy_to", "phone_all");
            typeMapping.put("fields", Map.of(
                    "digits", Map.of("type", "text", "analyzer", "phone_digits"),
                    "ngram",  Map.of("type", "text", "analyzer", "phone_ngram", "search_analyzer", "phone_digits")));
            return typeMapping;
        }

        typeMapping.put("type", fd.type());
        if ("constant_keyword".equals(fd.type()) && fd.source().startsWith("constant:")) {
            typeMapping.put("value", fd.source().substring("constant:".length()));
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
