package org.psjobergprivat.elasticlab.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.psjobergprivat.elasticlab.elasticsearch.ElasticsearchGateway;
import org.psjobergprivat.elasticlab.elasticsearch.SearchHits;
import org.psjobergprivat.elasticlab.metadata.IndexMetadataService;
import org.psjobergprivat.elasticlab.phone.PhoneFields;
import org.psjobergprivat.elasticlab.phone.PhoneNumberNormalizer;
import org.psjobergprivat.elasticlab.testdata.ElasticsearchTestResource;
import org.psjobergprivat.elasticlab.testdata.MappingBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * Verifies the four-way phone matching contract:
 *
 * <ol>
 *   <li>National query matches docs in any country (intl + national) with same subscriber.</li>
 *   <li>International query matches docs written in national form (any country) with same subscriber.</li>
 *   <li>National query matches national docs with same subscriber.</li>
 *   <li>International query matches international docs only when the country code agrees.</li>
 * </ol>
 *
 * Plus two libphonenumber-only edge cases:
 *
 * <ul>
 *   <li>UK numbers written as <code>+44 (0)7700 …</code> normalise to the same canonical form
 *       as <code>+44 7700 …</code>.</li>
 *   <li>Russian numbers written with the trunk prefix <code>8</code> (instead of <code>+7</code>)
 *       normalise to the same subscriber.</li>
 * </ul>
 */
@QuarkusTest
@QuarkusTestResource(ElasticsearchTestResource.class)
@TestInstance(Lifecycle.PER_CLASS)
class PhoneSearchTest {

    @Inject
    ElasticsearchGateway elasticsearch;

    @Inject
    MappingBuilder mappingBuilder;

    @Inject
    PhoneNumberNormalizer phoneNormalizer;

    @Inject
    QueryCompiler queryCompiler;

    @Inject
    IndexMetadataService indexMetadataService;

    @ConfigProperty(name = "elastic-lab.default-elastic-index")
    String index;

    private static final String SE_INTL = "+46 70 123 4567";
    private static final String SE_NATIONAL = "0701234567";
    private static final String NO_INTL = "+47 41234567";
    private static final String UK_PARENS = "+44 (0)7700 123456";
    private static final String US_NATIONAL = "(212) 555-1234";
    private static final String RU_NATIONAL = "8 925 1234567";

    @BeforeEach
    void resetIndex() throws IOException {
        elasticsearch.dropIndex(index);
        elasticsearch.createIndex(index, mappingBuilder.build());
        indexPhoneDoc(SE_INTL, "SE");
        indexPhoneDoc(SE_NATIONAL, "SE");
        indexPhoneDoc(NO_INTL, "NO");
        indexPhoneDoc(UK_PARENS, "GB");
        indexPhoneDoc(US_NATIONAL, "US");
        indexPhoneDoc(RU_NATIONAL, "RU");
        elasticsearch.refresh(index);
    }

    @AfterAll
    void cleanupIndex() throws IOException {
        elasticsearch.dropIndex(index);
    }

    @Test
    void nationalQueryMatchesAnyCountrySharingSubscriber() throws IOException {
        // "0701234567" → subscriber 701234567. Sweden intl and Sweden national share it; nobody else does.
        assertHits("0701234567", SE_INTL, SE_NATIONAL);
    }

    @Test
    void internationalQueryMatchesSameCountryAndAnyNational() throws IOException {
        // "+46 70 123 4567" → canonical 46701234567 (matches Sweden intl) and subscriber 701234567
        // matched against SUBSCRIBER_NATIONAL (matches Sweden national). Norway intl must NOT match
        // even though the subscriber-coincidence rules wouldn't bleed across countries here.
        assertHits("+46 70 123 4567", SE_INTL, SE_NATIONAL);
    }

    @Test
    void internationalQueryDoesNotMatchOtherCountryInternational() throws IOException {
        // Hypothetical Norwegian number with subscriber that happens to look Swedish-shaped.
        // The point: Norway intl-format query should not match Sweden intl-format doc.
        assertHits("+47 41234567", NO_INTL);
    }

    @Test
    void usNationalQueryMatchesUsDoc() throws IOException {
        // US national has no trunk prefix, so libphonenumber returns the same subscriber as the
        // E.164 form would. A national-form query with that subscriber must hit the US doc.
        assertHits("2125551234", US_NATIONAL);
    }

    @Test
    void usInternationalQueryMatchesUsDoc() throws IOException {
        // Same number written internationally — canonical 12125551234 should match the doc that
        // was indexed in national form (via SUBSCRIBER_NATIONAL).
        assertHits("+1 212 555 1234", US_NATIONAL);
    }

    @Test
    void ukParenthesisedTrunkNormalisesCorrectly() throws IOException {
        // The "(0)" inside +44 (0)7700 ... is libphonenumber's quirk handling: it strips the
        // parenthesised trunk, leaving canonical 447700123456. A clean intl query for the same
        // number should match the doc.
        assertHits("+44 7700 123456", UK_PARENS);
    }

    @Test
    void russiaTrunkPrefixNormalisesToSameSubscriber() throws IOException {
        // "8 925 1234567" is parsed with region RU, libphonenumber recognises the 8 trunk and
        // produces subscriber 9251234567 / canonical 79251234567. An international query
        // "+7 925 1234567" should hit the doc via SUBSCRIBER_NATIONAL.
        assertHits("+7 925 1234567", RU_NATIONAL);
    }

    private void assertHits(String input, String... expectedPhones) throws IOException {
        PropertyNode node = new PropertyNode("", "phone", input);
        Map<String, Object> mappings = indexMetadataService.loadMetadata(index).mappings();
        Query query = queryCompiler.compile(node, mappings);
        SearchHits hits = elasticsearch.search(index, query, 100, 0);
        Set<String> actual = hits.hits().stream()
                .map(h -> (String) h.source().get("phone"))
                .collect(Collectors.toSet());
        assertThat("Query '" + input + "' returned: " + actual,
                actual, containsInAnyOrder(expectedPhones));
    }

    private void indexPhoneDoc(String rawPhone, String region) throws IOException {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("phone", rawPhone);
        phoneNormalizer.normalize(rawPhone, region).ifPresent(n -> {
            List<String> canonical = new ArrayList<>();
            canonical.add(n.fullDigits());
            doc.put(PhoneFields.CANONICAL, canonical);

            List<String> subscriber = new ArrayList<>();
            subscriber.add(n.subscriber());
            doc.put(PhoneFields.SUBSCRIBER, subscriber);

            if (!n.international()) {
                List<String> nat = new ArrayList<>();
                nat.add(n.subscriber());
                doc.put(PhoneFields.SUBSCRIBER_NATIONAL, nat);
            }
        });
        elasticsearch.indexDocument(index, doc);
    }
}
