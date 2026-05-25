package org.psjobergprivat.elasticlab.testdata;

/**
 * A generated phone string and the ISO 3166-1 alpha-2 region the formatter
 * used to produce it. The region travels with the value so that downstream
 * normalisation can parse trunk-prefixed national formats correctly
 * (e.g. Russia's {@code 8} trunk, Hungary's {@code 06}, France's {@code 0}).
 */
public record PhoneSample(String value, String region) {
}
