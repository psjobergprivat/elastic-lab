package org.psjobergprivat.elasticlab.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ConstantScoreQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryStringQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.WildcardQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.psjobergprivat.elasticlab.phone.PhoneFields;
import org.psjobergprivat.elasticlab.phone.PhoneNumberNormalizer;
import org.psjobergprivat.elasticlab.phone.PhoneNumberNormalizer.Normalized;

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
 *   <li>{@link PropertyNode} — needs a non-blank {@code value}; needs a non-blank
 *       {@code path} unless {@code valueType} is {@code "phone"}, in which case
 *       the query targets the phone catchall fields and the path is ignored.</li>
 *   <li>{@link TypeAllNode} — needs a non-blank {@code valueType} and {@code value},
 *       and at least one field of that type in the mapping.</li>
 *   <li>{@link GlobalNode}, {@link FreeTextNode} — need a non-blank {@code value}.</li>
 *   <li>{@link GroupNode} — valid iff at least one child is valid.</li>
 *   <li>{@link NotNode} — valid iff its child is valid.</li>
 * </ul>
 *
 * <p>The {@code "phone"} value type is the four-way phone match described in
 * {@link PhoneFields}. The query string is parsed by libphonenumber; if it
 * starts with {@code +} or {@code 00} it is treated as international (must
 * match the same-country canonical form, or any document whose phone was
 * stored in national form), otherwise national (matches any phone, in any
 * country, that shares the subscriber digits). National-format inputs need a
 * default region to disambiguate trunk prefixes; that region is read from
 * {@code elastic-lab.phone.default-search-region} and is optional.
 */
@ApplicationScoped
public class QueryCompiler {

    @Inject
    PhoneNumberNormalizer phoneNormalizer;

    @ConfigProperty(name = "elastic-lab.phone.default-search-region")
    Optional<String> defaultSearchRegion;

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
        String value = node.value();
        if (isBlank(value)) return Optional.empty();
        String type = node.valueType() == null ? "text" : node.valueType().toLowerCase();

        if ("phone".equals(type)) {
            return compilePhone(value);
        }

        if ("email".equals(type)) {
            return Optional.of(asConstantScore(termQuery("email_all", value)));
        }

        String path = node.path();
        if (isBlank(path)) return Optional.empty();

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

    private Optional<Query> compilePhone(String value) {
        return phoneNormalizer.normalize(value, defaultSearchRegion.orElse(null))
                .map(this::phoneQuery)
                .map(this::asConstantScore);
    }

    private Query phoneQuery(Normalized n) {
        if (n.international()) {
            // Same-country international match on the canonical (CC + subscriber) form,
            // OR a national-format document with the same subscriber digits. The latter
            // intentionally ignores country code — a doc written in national form
            // doesn't carry one.
            Query sameCountryIntl = termQuery(PhoneFields.CANONICAL, n.fullDigits());
            Query nationalDoc = termQuery(PhoneFields.SUBSCRIBER_NATIONAL, n.subscriber());
            return BoolQuery.of(b -> b
                    .should(sameCountryIntl)
                    .should(nationalDoc)
                    .minimumShouldMatch("1"))._toQuery();
        }
        // National query: subscriber digits collapse country code AND trunk prefix,
        // so a single term match against the universal subscriber catchall is enough.
        return termQuery(PhoneFields.SUBSCRIBER, n.subscriber());
    }

    // Phone match is presence-not-relevance: a doc either carries the subscriber
    // digits or it doesn't. Wrapping in constant_score skips IDF computation and
    // sorts hits by _doc instead of by digit rarity.
    private Query asConstantScore(Query inner) {
        return ConstantScoreQuery.of(c -> c.filter(inner))._toQuery();
    }

    private Query termQuery(String field, String value) {
        return TermQuery.of(t -> t.field(field).value(FieldValue.of(value)))._toQuery();
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
