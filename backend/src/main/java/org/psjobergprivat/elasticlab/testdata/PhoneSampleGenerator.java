package org.psjobergprivat.elasticlab.testdata;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Random;

/**
 * Generates randomised phone numbers in country-appropriate formats.
 * Each generator emits the same number rendered in several styles (international
 * with {@code +} or {@code 00}, national, with or without separators) so that
 * the search pipeline gets exercised on the full variety of human input.
 *
 * <p>The returned {@link PhoneSample} carries the ISO region code that the
 * generator used; downstream normalisation needs the region to interpret
 * national-format inputs that share leading digits across countries
 * (e.g. Russia's {@code 8} trunk vs. an arbitrary number starting with 8).
 */
@ApplicationScoped
public class PhoneSampleGenerator {

    private static final int[] US_AREA_CODES = {202, 212, 310, 404, 415, 512, 617, 646, 702, 773, 917};
    private static final int[] EGYPT_PREFIXES = {10, 11, 12, 15};
    private static final int[] ISRAEL_MOBILE_PREFIXES = {50, 52, 53, 54, 55, 58};
    private static final int[] CHINA_MOBILE_PREFIXES = {
            130, 131, 132, 133, 135, 136, 137, 138, 139,
            150, 151, 152, 153, 155, 156, 157, 158, 159,
            176, 177, 178, 180, 181, 182, 183, 185, 186, 187, 188, 189
    };

    public PhoneSample generate(String docLang, Random random) {
        return switch (docLang) {
            case "en" -> random.nextBoolean() ? unitedStates(random) : unitedKingdom(random);
            case "fr" -> france(random);
            case "de" -> germany(random);
            case "es" -> spain(random);
            case "ru" -> russia(random);
            case "ar" -> random.nextBoolean() ? egypt(random) : saudiArabia(random);
            case "he" -> israel(random);
            case "zh" -> china(random);
            default -> mixed(random);
        };
    }

    private PhoneSample mixed(Random random) {
        return switch (random.nextInt(11)) {
            case 0  -> sweden(random);
            case 1  -> unitedStates(random);
            case 2  -> unitedKingdom(random);
            case 3  -> germany(random);
            case 4  -> france(random);
            case 5  -> spain(random);
            case 6  -> australia(random);
            case 7  -> russia(random);
            case 8  -> egypt(random);
            case 9  -> israel(random);
            default -> china(random);
        };
    }

    // Cardinality doubled by widening the last segment from 0..99 to 0..199. Values
    // 0..99 still print as two digits; 100..199 print as three. Same approach is used
    // for every country generator below.
    private PhoneSample sweden(Random random) {
        int d = random.nextInt(10);
        int g3 = random.nextInt(1000);
        int g2a = random.nextInt(100);
        int g2b = random.nextInt(200);
        String value = switch (random.nextInt(5)) {
            case 0  -> String.format("+46 7%d-%03d %02d %02d", d, g3, g2a, g2b);
            case 1  -> String.format("07%d%03d%02d%02d", d, g3, g2a, g2b);
            case 2  -> String.format("07%d %03d %02d %02d", d, g3, g2a, g2b);
            case 3  -> String.format("07%d-%03d %02d %02d", d, g3, g2a, g2b);
            default -> String.format("0046 7%d-%03d %02d %02d", d, g3, g2a, g2b);
        };
        return new PhoneSample(value, "SE");
    }

    private PhoneSample unitedStates(Random random) {
        int area = US_AREA_CODES[random.nextInt(US_AREA_CODES.length)];
        int exchange = 200 + random.nextInt(800);
        int number = random.nextInt(20000);
        String value = switch (random.nextInt(4)) {
            case 0  -> String.format("+1 (%d) %d-%04d", area, exchange, number);
            case 1  -> String.format("%d-%d-%04d", area, exchange, number);
            case 2  -> String.format("(%d) %d-%04d", area, exchange, number);
            default -> String.format("%d%d%04d", area, exchange, number);
        };
        return new PhoneSample(value, "US");
    }

    private PhoneSample unitedKingdom(Random random) {
        int prefix = 7700 + random.nextInt(300);
        int local = random.nextInt(2_000_000);
        String value = switch (random.nextInt(5)) {
            case 0  -> String.format("+44 %d %06d", prefix, local);
            case 1  -> String.format("0%d %06d", prefix, local);
            case 2  -> String.format("+44-%d-%06d", prefix, local);
            case 3  -> String.format("+44 (0)%d %06d", prefix, local); // intl with parenthesised trunk
            default -> String.format("0044 %d %06d", prefix, local);
        };
        return new PhoneSample(value, "GB");
    }

    private PhoneSample germany(Random random) {
        int prefix = 150 + random.nextInt(30);
        int local = random.nextInt(200_000_000);
        String value = switch (random.nextInt(4)) {
            case 0  -> String.format("+49 %d %08d", prefix, local);
            case 1  -> String.format("0%d %08d", prefix, local);
            case 2  -> String.format("+49-%d-%08d", prefix, local);
            default -> String.format("0049 %d %08d", prefix, local);
        };
        return new PhoneSample(value, "DE");
    }

    private PhoneSample france(Random random) {
        int g2a = random.nextInt(100);
        int g2b = random.nextInt(100);
        int g2c = random.nextInt(100);
        int g2d = random.nextInt(200);
        String value = switch (random.nextInt(5)) {
            case 0  -> String.format("+33 6 %02d %02d %02d %02d", g2a, g2b, g2c, g2d);
            case 1  -> String.format("06 %02d %02d %02d %02d", g2a, g2b, g2c, g2d);
            case 2  -> String.format("06%02d%02d%02d%02d", g2a, g2b, g2c, g2d);
            case 3  -> String.format("0033 6 %02d %02d %02d %02d", g2a, g2b, g2c, g2d);
            default -> String.format("06.%02d.%02d.%02d.%02d", g2a, g2b, g2c, g2d);
        };
        return new PhoneSample(value, "FR");
    }

    private PhoneSample spain(Random random) {
        int d2 = random.nextInt(100);
        int g3a = random.nextInt(1000);
        int g3b = random.nextInt(2000);
        String value = switch (random.nextInt(5)) {
            case 0  -> String.format("+34 6%02d %03d %03d", d2, g3a, g3b);
            case 1  -> String.format("6%02d %03d %03d", d2, g3a, g3b);
            case 2  -> String.format("6%02d-%03d-%03d", d2, g3a, g3b);
            case 3  -> String.format("0034 6%02d %03d %03d", d2, g3a, g3b);
            default -> String.format("6%02d.%03d.%03d", d2, g3a, g3b);
        };
        return new PhoneSample(value, "ES");
    }

    private PhoneSample australia(Random random) {
        int d2 = random.nextInt(100);
        int g3a = random.nextInt(1000);
        int g3b = random.nextInt(2000);
        String value = switch (random.nextInt(4)) {
            case 0  -> String.format("+61 4%02d %03d %03d", d2, g3a, g3b);
            case 1  -> String.format("04%02d %03d %03d", d2, g3a, g3b);
            case 2  -> String.format("04%02d-%03d-%03d", d2, g3a, g3b);
            default -> String.format("0061 4%02d %03d %03d", d2, g3a, g3b);
        };
        return new PhoneSample(value, "AU");
    }

    private PhoneSample russia(Random random) {
        int prefix = 900 + random.nextInt(99);
        int g3 = random.nextInt(1000);
        int g2a = random.nextInt(100);
        int g2b = random.nextInt(200);
        String value = switch (random.nextInt(5)) {
            case 0  -> String.format("+7 %d %03d-%02d-%02d", prefix, g3, g2a, g2b);
            case 1  -> String.format("8 %d %03d %02d %02d", prefix, g3, g2a, g2b);
            case 2  -> String.format("8%d%03d%02d%02d", prefix, g3, g2a, g2b);
            case 3  -> String.format("+7-%d-%03d-%02d-%02d", prefix, g3, g2a, g2b);
            default -> String.format("7 (%d) %03d-%02d-%02d", prefix, g3, g2a, g2b);
        };
        return new PhoneSample(value, "RU");
    }

    private PhoneSample egypt(Random random) {
        int prefix = EGYPT_PREFIXES[random.nextInt(EGYPT_PREFIXES.length)];
        int g4a = random.nextInt(10000);
        int g4b = random.nextInt(20000);
        String value = switch (random.nextInt(4)) {
            case 0  -> String.format("+20 %d %04d %04d", prefix, g4a, g4b);
            case 1  -> String.format("0%d%04d%04d", prefix, g4a, g4b);
            case 2  -> String.format("+20-%d-%04d-%04d", prefix, g4a, g4b);
            default -> String.format("0020 %d %04d %04d", prefix, g4a, g4b);
        };
        return new PhoneSample(value, "EG");
    }

    private PhoneSample saudiArabia(Random random) {
        int prefix = 50 + random.nextInt(10);
        int g3 = random.nextInt(1000);
        int g4 = random.nextInt(20000);
        String value = switch (random.nextInt(4)) {
            case 0  -> String.format("+966 %d %03d %04d", prefix, g3, g4);
            case 1  -> String.format("0%d%03d%04d", prefix, g3, g4);
            case 2  -> String.format("+966-%d-%03d-%04d", prefix, g3, g4);
            default -> String.format("00966 %d %03d %04d", prefix, g3, g4);
        };
        return new PhoneSample(value, "SA");
    }

    private PhoneSample israel(Random random) {
        int prefix = ISRAEL_MOBILE_PREFIXES[random.nextInt(ISRAEL_MOBILE_PREFIXES.length)];
        int g3 = random.nextInt(1000);
        int g4 = random.nextInt(20000);
        String value = switch (random.nextInt(4)) {
            case 0  -> String.format("+972 %d-%03d-%04d", prefix, g3, g4);
            case 1  -> String.format("0%d-%03d-%04d", prefix, g3, g4);
            case 2  -> String.format("0%d%03d%04d", prefix, g3, g4);
            default -> String.format("00972 %d-%03d-%04d", prefix, g3, g4);
        };
        return new PhoneSample(value, "IL");
    }

    private PhoneSample china(Random random) {
        int prefix = CHINA_MOBILE_PREFIXES[random.nextInt(CHINA_MOBILE_PREFIXES.length)];
        int g4a = random.nextInt(10000);
        int g4b = random.nextInt(20000);
        String value = switch (random.nextInt(4)) {
            case 0  -> String.format("+86 %d %04d %04d", prefix, g4a, g4b);
            case 1  -> String.format("%d%04d%04d", prefix, g4a, g4b);
            case 2  -> String.format("+86-%d-%04d-%04d", prefix, g4a, g4b);
            default -> String.format("0086 %d %04d %04d", prefix, g4a, g4b);
        };
        return new PhoneSample(value, "CN");
    }
}
