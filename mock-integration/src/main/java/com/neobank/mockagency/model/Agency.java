package com.neobank.mockagency.model;

/**
 * The two identity sources this service stands in for.
 *
 * <p>{@link #slug()} is what appears in the URL — {@code /api/v1/agencies/national/verifications} —
 * and in the caller's {@code third_party_attempts} rows, so it is short and stable. The enum
 * constant is the wire value in the response body.</p>
 */
public enum Agency {

    /**
     * The primary. Holds the passport and national-ID registers, so it is the only source that can
     * say whether a document is genuine.
     */
    NATIONAL_IDENTITY_AGENCY("national", "nat"),

    /**
     * The fallback, tried once after the primary's retry budget is spent. It can confirm that a
     * name, date of birth and address belong together — it has never seen the document.
     */
    TAX_AGENCY("tax", "tax");

    private final String slug;
    private final String refPrefix;

    Agency(String slug, String refPrefix) {
        this.slug = slug;
        this.refPrefix = refPrefix;
    }

    /** The path segment: {@code national} or {@code tax}. */
    public String slug() {
        return slug;
    }

    /** Prefix of the {@code providerRef} this agency issues, so a reference names its source. */
    public String refPrefix() {
        return refPrefix;
    }

    /**
     * Resolve a path segment to an agency.
     *
     * @throws IllegalArgumentException if no agency owns that slug — the controller turns this
     *         into a 404 rather than letting an unknown source silently behave like a known one.
     */
    public static Agency ofSlug(String slug) {
        for (Agency agency : values()) {
            if (agency.slug.equalsIgnoreCase(slug)) {
                return agency;
            }
        }
        throw new IllegalArgumentException("unknown agency: " + slug);
    }
}
