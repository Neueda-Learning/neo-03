package com.neobank.module.integrations.orchestrator;

import java.util.List;

/**
 * <h2>The application a customer submitted. Every module receives all of it.</h2>
 *
 * <p>This is the neo-bank's application form as data — the shape is defined once in the system's
 * {@code api-contract.md} §3 and the orchestrator owns it. Your module reads only the fields its
 * own topic needs and ignores the rest: verification looks at {@code applicant} and
 * {@code product}, screening at names and countries, credit at {@code finances}. Nothing here is
 * yours to change.</p>
 *
 * <p>It is one file with nested records on purpose: the whole domain fits on one screen, and your
 * IDE will complete your way down it — {@code application.applicant().currentAddress().country()}.
 * That is the point of modelling it instead of passing a {@code Map} around.</p>
 *
 * <h3>Three typing rules that look wrong and are not</h3>
 *
 * <p><b>1. Dates are {@code String}.</b> Not {@code LocalDate}. The orchestrator can send you
 * {@code "12/03/1990"} or {@code "31-12-2030"} — and it does, on purpose. A typed date makes
 * Jackson reject the whole request with a {@code 400} before your code runs, so the module never
 * gets to say <em>which</em> field was malformed and why. <b>Validating a date is your job, not the
 * parser's.</b> Parse it yourself when you need to:
 * {@code LocalDate.parse(applicant().dateOfBirth())} inside a try/catch is a rule you can report on.
 *
 * <p><b>2. Codes are {@code String}, not enums.</b> {@code channel}, {@code residentialStatus},
 * {@code productCode}, document {@code type}. An enum would reject an unknown value at the door;
 * you will be sent product codes outside the catalogue and are expected to answer "that product
 * does not exist" rather than crash.
 *
 * <p><b>3. Numbers and booleans are boxed.</b> {@code Integer}, not {@code int}, so a field nobody
 * filled in is {@code null} rather than {@code 0} — "declared no dependants" and "did not answer"
 * are different facts, and only one of them is safe to divide by.
 *
 * <p>Unknown fields are ignored, so the orchestrator can add a field without breaking you.</p>
 */
public record Application(

        /**
         * The orchestrator's copy of the id, inside the application.
         *
         * <p><b>Do not key on this one.</b> Use {@link ApplicationRequest#applicationId()} — the
         * envelope's — which is the id the orchestrator matches on, the one it validates, and the
         * one that goes in the status-update URL. The two are normally identical; when they are
         * not, the envelope is right. (This field can also be {@code null}: an invalid envelope
         * still reaches your module.)</p>
         */
        String applicationId,

        /** How the customer applied: {@code WEB}, {@code MOBILE_APP}, {@code BRANCH}, {@code PHONE}. */
        String channel,

        /** ISO-8601 instant, e.g. {@code "2026-07-25T09:14:00Z"}. A String — see rule 1. */
        String submittedAt,

        Applicant applicant,
        IdentityDocument identityDocument,
        Employment employment,
        Finances finances,
        Product product,
        Delivery delivery,
        Consents consents) {

    /** The human applying. */
    public record Applicant(
            String fullName,
            /** ISO date {@code "1996-04-11"} — but not guaranteed to be. See rule 1. */
            String dateOfBirth,
            String email,
            String mobile,
            /** ISO 3166-1 alpha-2, e.g. {@code "PL"}. Not guaranteed — see rule 2. */
            String nationality,
            String countryOfResidence,
            /** Every country they are tax-resident in. Module 2's rules live here. */
            List<String> taxResidencies,
            /** {@code OWNER} · {@code RENTING} · {@code LIVING_WITH_FAMILY} · {@code OTHER}. */
            String residentialStatus,
            Address currentAddress,
            Integer monthsAtAddress,
            Integer dependants) {
    }

    /** Used for the current address and, when delivery differs, for the delivery address. */
    public record Address(
            String line1,
            /** Optional — frequently {@code null}. */
            String line2,
            String city,
            String postcode,
            String country) {
    }

    /** {@code type} is {@code PASSPORT} · {@code NATIONAL_ID} · {@code DRIVING_LICENCE}. */
    public record IdentityDocument(
            String type,
            String documentId,
            String issuingCountry,
            /** ISO date. An expiry in the past is a rule for you to fire, not a parse error. */
            String expiryDate) {
    }

    /** {@code status} is {@code PERMANENT} · {@code SELF_EMPLOYED} · {@code CONTRACT} · {@code STUDENT} · {@code UNEMPLOYED} · {@code RETIRED}. */
    public record Employment(
            String status,
            String employerName,
            Integer monthsInEmployment) {
    }

    /** Whole GBP per year / per month. Affordability is computed from these three. */
    public record Finances(
            Integer annualIncome,
            Integer monthlyHousingCost,
            Integer existingCreditCommitments) {
    }

    /** What they asked for. {@code productCode} may be one you do not recognise — see rule 2. */
    public record Product(
            String productCode,
            Integer requestedCreditLimit) {
    }

    /**
     * Where the card goes. If {@code useCurrentAddress} is false, {@code address} should be
     * populated — and sometimes is not, which is a rule for the card module to catch rather
     * than a {@code NullPointerException} to suffer.
     */
    public record Delivery(
            Boolean useCurrentAddress,
            Address address) {
    }

    /** What they agreed to. {@code termsAccepted} false is a hard stop. */
    public record Consents(
            Boolean termsAccepted,
            Boolean paperlessStatements,
            Boolean marketingConsent) {
    }
}
