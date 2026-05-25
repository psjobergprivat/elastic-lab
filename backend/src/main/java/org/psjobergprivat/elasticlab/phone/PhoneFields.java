package org.psjobergprivat.elasticlab.phone;

/**
 * Top-level catchall field names that drive the phone-aware search branch.
 * Documents are populated by the test-data writer; queries are built by the
 * search compiler. Keeping the names in one place ensures the two stay
 * aligned.
 */
public final class PhoneFields {

    /** Every phone's E.164 digits (country code + national significant number). */
    public static final String CANONICAL = "phone_all_canonical";

    /** Every phone's national significant number (subscriber alone). */
    public static final String SUBSCRIBER = "phone_all_subscriber";

    /** Subscriber from phones written in national format only. */
    public static final String SUBSCRIBER_NATIONAL = "phone_all_subscriber_national";

    private PhoneFields() {}
}
