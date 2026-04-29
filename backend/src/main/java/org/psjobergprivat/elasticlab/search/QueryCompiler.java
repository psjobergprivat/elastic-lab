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
import java.util.Optional;

/**
 * Compiles a {@link QueryNode} tree into an Elasticsearch {@link Query}.
 *
 * <p>The internal {@code compileX} helpers return {@link Optional}: empty means the
 * node "contributes no constraint" — for example a property leaf with a blank
 * value, an empty group, or a NOT with no child. Empty contributions are
 * filtered out inside parent groups; if the whole tree is empty,
 * {@link #compile(QueryNode, Map)} falls back to {@code match_all}.
 *
 * <p>Validity rules per node, in one place:
 * <ul>
 *   <li>{@link PropertyNode} — needs a non-blank {@code path} and {@code value}.</li>
 *   <li>{@link TypeAllNode} — needs a non-blank {@code valueType} and {@code value},
 *       and at least one field of that type in the mapping.</li>
 *   <li>{@link GlobalNode}, {@link FreeTextNode} — need a non-blank {@code value}.</li>
 *   <li>{@link GroupNode} — valid iff at least one child is valid.</li>
 *   <li>{@link NotNode} — valid iff its child is valid.</li>
 * </ul>
 */
@ApplicationScoped
public class QueryCompiler {

    public Query compile(QueryNode node, Map<String, Object> mappingsRoot) {
        return compileNode(node, mappingsRoot).orElseGet(this::matchAll);
    }

    private Optional<Query> compileNode(QueryNode node, Map<String, Object> mappingsRoot) {
        if (node == null) return Optional.empty();
        return switch (node) {
            case GroupNode g -> compileGroup(g, mappingsRoot);
            case NotNode n -> compileNot(n, mappingsRoot);
            case PropertyNode p -> compileProperty(p);
            case TypeAllNode t -> compileTypeAll(t, mappingsRoot);
            case GlobalNode g -> compileGlobal(g);
            case FreeTextNode f -> compileFreeText(f);
        };
    }

    private Optional<Query> compileGroup(GroupNode group, Map<String, Object> mappingsRoot) {
        List<QueryNode> rawChildren = group.children() != null ? group.children() : List.of();
        List<Query> children = rawChildren.stream()
                .map(child -> compileNode(child, mappingsRoot))
                .flatMap(Optional::stream)
                .toList();
        if (children.isEmpty()) return Optional.empty();
        GroupNode.Operator op = group.operator() != null ? group.operator() : GroupNode.Operator.AND;
        return Optional.of(switch (op) {
            case AND -> BoolQuery.of(b -> b.must(children))._toQuery();
            case OR -> BoolQuery.of(b -> b.should(children).minimumShouldMatch("1"))._toQuery();
        });
    }

    private Optional<Query> compileNot(NotNode node, Map<String, Object> mappingsRoot) {
        return compileNode(node.child(), mappingsRoot)
                .map(child -> BoolQuery.of(b -> b
                        .must(matchAll())
                        .mustNot(child))._toQuery());
    }

    private Optional<Query> compileProperty(PropertyNode node) {
        String path = node.path();
        String value = node.value();
        if (isBlank(path) || isBlank(value)) return Optional.empty();
        String type = node.valueType() == null ? "text" : node.valueType().toLowerCase();

        if (containsWildcard(value) && !"text".equals(type)) {
            return Optional.of(WildcardQuery.of(w -> w.field(path).value(value).caseInsensitive(true))._toQuery());
        }

        return Optional.of(switch (type) {
            case "keyword", "date" -> TermQuery.of(t -> t.field(path).value(FieldValue.of(value)))._toQuery();
            case "boolean" -> TermQuery.of(t -> t.field(path).value(FieldValue.of(Boolean.parseBoolean(value))))._toQuery();
            case "long", "integer", "short", "byte" -> TermQuery.of(t -> t.field(path).value(FieldValue.of(parseLongSafe(value))))._toQuery();
            case "double", "float", "half_float", "scaled_float" -> TermQuery.of(t -> t.field(path).value(FieldValue.of(parseDoubleSafe(value))))._toQuery();
            default -> MatchQuery.of(m -> m.field(path).query(value))._toQuery();
        });
    }

    private Optional<Query> compileTypeAll(TypeAllNode node, Map<String, Object> mappingsRoot) {
        if (isBlank(node.value()) || isBlank(node.valueType())) return Optional.empty();
        List<String> fields = collectFieldsByType(mappingsRoot, node.valueType());
        if (fields.isEmpty()) return Optional.empty();
        return Optional.of(MultiMatchQuery.of(m -> m.query(node.value()).fields(fields))._toQuery());
    }

    private Optional<Query> compileGlobal(GlobalNode node) {
        if (isBlank(node.value())) return Optional.empty();
        return Optional.of(MultiMatchQuery.of(m -> m.query(node.value()).fields("*"))._toQuery());
    }

    private Optional<Query> compileFreeText(FreeTextNode node) {
        if (isBlank(node.value())) return Optional.empty();
        return Optional.of(QueryStringQuery.of(q -> q.query(node.value()))._toQuery());
    }

    private Query matchAll() {
        return MatchAllQuery.of(m -> m)._toQuery();
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
                            && Objects.equals(subDef.get("type"), type)) {
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
