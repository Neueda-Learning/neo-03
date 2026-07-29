package com.neobank.module.integrations.idprovider;

/**
 * The two identity sources this module calls, in the order it calls them.
 *
 * <p>They are declared in that order deliberately — {@code Agency.values()} IS the failover chain,
 * so adding a third source is one enum constant rather than an edit to the retry logic.</p>
 */
public enum Agency {

    /**
     * The National Identity Agency: the primary. Holds the passport and national-ID registers, so
     * it is the only source that can tell us whether a document is genuine. Gets the full retry
     * budget.
     */
    NATIONAL("national"),

    /**
     * The Tax Agency: the fallback, tried once after the primary's budget is spent. It confirms
     * that a name, date of birth and address belong together; it has never seen the document, so
     * a decision made on its answer has no forgery check behind it.
     */
    TAX("tax");

    private final String slug;

    Agency(String slug) {
        this.slug = slug;
    }

    /** The path segment in the provider's URL. */
    public String slug() {
        return slug;
    }
}
