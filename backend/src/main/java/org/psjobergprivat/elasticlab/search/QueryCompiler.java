package org.psjobergprivat.elasticlab.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryStringQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.WildcardQuery;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class QueryCompiler {

    public Query compile(QueryNode node, Map<String, Object> mappingsRoot) {
        Query compiled = compileNode(node, mappingsRoot);
        return compiled != null ? compiled : MatchAllQuery.of(m -> m)._toQuery();
    }

    private Query compileNode(QueryNode node, Map<String, Object> mappingsRoot) {
        if (node == null) return null;
        return switch (node) {
            case GroupNode g -> compileGroup(g, mappingsRoot);
            case PropertyNode p -> compileProperty(p);
            case TypeAllNode t -> compileTypeAll(t, mappingsRoot);
            case GlobalNode g -> compileGlobal(g);
            case FreeTextNode f -> compileFreeText(f);
        };
    }

    private Query compileGroup(GroupNode group, Map<String, Object> mappingsRoot) {
        List<Query> children = new ArrayList<>();
        if (group.children() != null) {
            for (QueryNode child : group.children()) {
                Query compiled = compileNode(child, mappingsRoot);
                if (compiled != null) children.add(compiled);
            }
        }
        if (children.isEmpty()) return null;
        GroupNode.Operator op = group.operator() != null ? group.operator() : GroupNode.Operator.AND;
        return switch (op) {
            case AND -> BoolQuery.of(b -> b.must(children))._toQuery();
            case OR -> BoolQuery.of(b -> b.should(children).minimumShouldMatch("1"))._toQuery();
            case NOT -> BoolQuery.of(b -> b
                    .must(MatchAllQuery.of(m -> m)._toQuery())
                    .mustNot(children))._toQuery();
        };
    }

    private Query compileProperty(PropertyNode node) {
        String path = node.path();
        String value = node.value();
        if (isBlank(path) || isBlank(value)) return null;
        String type = node.valueType() == null ? "text" : node.valueType().toLowerCase();

        if (containsWildcard(value) && !"text".equals(type)) {
            return WildcardQuery.of(w -> w.field(path).value(value).caseInsensitive(true))._toQuery();
        }

        return switch (type) {
            case "text" -> MatchQuery.of(m -> m.field(path).query(value))._toQuery();
            case "keyword" -> TermQuery.of(t -> t.field(path).value(FieldValue.of(value)))._toQuery();
            case "boolean" -> TermQuery.of(t -> t.field(path).value(FieldValue.of(Boolean.parseBoolean(value))))._toQuery();
            case "long", "integer", "short", "byte" -> TermQuery.of(t -> t.field(path).value(FieldValue.of(parseLongSafe(value))))._toQuery();
            case "double", "float", "half_float", "scaled_float" -> TermQuery.of(t -> t.field(path).value(FieldValue.of(parseDoubleSafe(value))))._toQuery();
            case "date" -> TermQuery.of(t -> t.field(path).value(FieldValue.of(value)))._toQuery();
            default -> MatchQuery.of(m -> m.field(path).query(value))._toQuery();
        };
    }

    private Query compileTypeAll(TypeAllNode node, Map<String, Object> mappingsRoot) {
        if (isBlank(node.value()) || isBlank(node.valueType())) return null;
        List<String> fields = collectFieldsByType(mappingsRoot, node.valueType());
        if (fields.isEmpty()) return null;
        return MultiMatchQuery.of(m -> m.query(node.value()).fields(fields))._toQuery();
    }

    private Query compileGlobal(GlobalNode node) {
        if (isBlank(node.value())) return null;
        return MultiMatchQuery.of(m -> m.query(node.value()).fields("*"))._toQuery();
    }

    private Query compileFreeText(FreeTextNode node) {
        if (isBlank(node.value())) return null;
        return QueryStringQuery.of(q -> q.query(node.value()))._toQuery();
    }

    @SuppressWarnings("unchecked")
    private List<String> collectFieldsByType(Map<String, Object> mappingsRoot, String type) {
        List<String> out = new ArrayList<>();
        if (mappingsRoot == null) return out;
        Object props = mappingsRoot.get("properties");
        if (props instanceof Map<?, ?> propsMap) {
            walkProperties((Map<String, Object>) propsMap, "", type, out);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void walkProperties(Map<String, Object> properties, String prefix, String type, List<String> out) {
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> defMap)) continue;
            Map<String, Object> def = (Map<String, Object>) defMap;
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object nestedProps = def.get("properties");
            if (nestedProps instanceof Map<?, ?> nested) {
                walkProperties((Map<String, Object>) nested, path, type, out);
            }
            Object fieldType = def.get("type");
            if (Objects.equals(fieldType, type)) {
                out.add(path);
            }
            Object subFields = def.get("fields");
            if (subFields instanceof Map<?, ?> subMap) {
                for (Map.Entry<?, ?> sub : subMap.entrySet()) {
                    if (sub.getValue() instanceof Map<?, ?> subDef
                            && Objects.equals((subDef).get("type"), type)) {
                        out.add(path + "." + sub.getKey());
                    }
                }
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private boolean containsWildcard(String s) {
        return s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
    }

    private long parseLongSafe(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0L; }
    }

    private double parseDoubleSafe(String s) {
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 0d; }
    }
}
