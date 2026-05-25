package org.psjobergprivat.elasticlab.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Wraps Google's libphonenumber to produce the digit forms the phone search
 * relies on. Two forms are extracted from a raw phone string:
 *
 * <ul>
 *   <li>{@code fullDigits} — country code followed by the national significant
 *       number, with no separators. Equivalent to E.164 minus the leading
 *       {@code +}. For trunk-prefixed national input ({@code 0701234567} in
 *       Sweden, {@code 8 9XX...} in Russia) libphonenumber recognises the
 *       trunk and produces the same canonical digits as the international
 *       form.</li>
 *   <li>{@code subscriber} — the national significant number alone, without
 *       country code or trunk prefix. Two phone numbers belonging to the same
 *       subscriber share this string regardless of how they were written.</li>
 * </ul>
 *
 * Inputs that begin with {@code +} or {@code 00} are treated as international
 * and parsed without a default region. National-format inputs need a default
 * region to disambiguate (Russia's {@code 8} trunk vs. Hungary's {@code 06}
 * vs. Spain's no-trunk format). When no region is supplied for a national
 * input, or libphonenumber refuses to parse, a digit-only fallback is used:
 * subscriber is the digit string with one leading {@code 0} stripped.
 */
@ApplicationScoped
public class PhoneNumberNormalizer {

    private static final Logger LOG = Logger.getLogger(PhoneNumberNormalizer.class);

    private final PhoneNumberUtil util = PhoneNumberUtil.getInstance();

    public Optional<Normalized> normalize(String raw) {
        return normalize(raw, null);
    }

    public Optional<Normalized> normalize(String raw, String defaultRegion) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String trimmed = raw.trim();
        boolean international = isInternationalForm(trimmed);
        String parseInput = trimmed.startsWith("00") ? "+" + trimmed.substring(2) : trimmed;
        String region = international ? null : defaultRegion;
        try {
            PhoneNumber pn = util.parse(parseInput, region);
            String subscriber = String.valueOf(pn.getNationalNumber());
            String fullDigits = pn.getCountryCode() + subscriber;
            String resolvedRegion = util.getRegionCodeForNumber(pn);
            return Optional.of(new Normalized(fullDigits, subscriber, resolvedRegion, international));
        } catch (NumberParseException e) {
            LOG.debugf("libphonenumber could not parse '%s' (region=%s); using heuristic fallback.",
                    raw, region);
            return heuristicFallback(trimmed, international);
        }
    }

    private Optional<Normalized> heuristicFallback(String trimmed, boolean international) {
        String digits = trimmed.replaceAll("\\D", "");
        if (digits.isEmpty()) return Optional.empty();
        String subscriber = digits.startsWith("0") ? digits.substring(1) : digits;
        return Optional.of(new Normalized(digits, subscriber, null, international));
    }

    private boolean isInternationalForm(String trimmed) {
        return trimmed.startsWith("+") || trimmed.startsWith("00");
    }

    public record Normalized(String fullDigits, String subscriber, String regionCode, boolean international) {}
}
