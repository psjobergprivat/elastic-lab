package org.psjobergprivat.elasticlab.testdata;

import org.psjobergprivat.elasticlab.phone.PhoneFields;
import org.psjobergprivat.elasticlab.phone.PhoneNumberNormalizer;
import org.psjobergprivat.elasticlab.phone.PhoneNumberNormalizer.Normalized;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Accumulates the three catchall lists that drive the phone-aware search
 * branch. Lives for the duration of one document being generated.
 *
 * <ul>
 *   <li>{@code phone_all_canonical} — every phone's E.164 digits (country code
 *       + national significant number). Lets a query in international form
 *       match docs in the same country.</li>
 *   <li>{@code phone_all_subscriber} — every phone's national significant
 *       number alone. Lets a query in national form match docs from any
 *       country that share the subscriber digits.</li>
 *   <li>{@code phone_all_subscriber_national} — only contributed by phones
 *       written in national format. Lets an international-form query match
 *       docs whose phone was stored without a country code, without bleeding
 *       cross-country matches between two international-format documents.</li>
 * </ul>
 *
 * The asymmetry of the third field is what makes the four-way matching rules
 * hold: two international-format docs in different countries never share a
 * token, even though they share a subscriber.
 */
final class PhoneCatchallCollector {

    private final PhoneNumberNormalizer normalizer;
    private final List<String> canonical = new ArrayList<>();
    private final List<String> subscriber = new ArrayList<>();
    private final List<String> subscriberNational = new ArrayList<>();

    PhoneCatchallCollector(PhoneNumberNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    void add(PhoneSample sample) {
        normalizer.normalize(sample.value(), sample.region()).ifPresent(this::collect);
    }

    private void collect(Normalized n) {
        canonical.add(n.fullDigits());
        subscriber.add(n.subscriber());
        if (!n.international()) {
            subscriberNational.add(n.subscriber());
        }
    }

    void applyTo(Map<String, Object> root) {
        if (!canonical.isEmpty()) root.put(PhoneFields.CANONICAL, canonical);
        if (!subscriber.isEmpty()) root.put(PhoneFields.SUBSCRIBER, subscriber);
        if (!subscriberNational.isEmpty()) root.put(PhoneFields.SUBSCRIBER_NATIONAL, subscriberNational);
    }
}
