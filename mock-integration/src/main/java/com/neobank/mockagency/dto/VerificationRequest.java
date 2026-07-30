package com.neobank.mockagency.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * What the bank sends an identity source.
 *
 * <p>Both agencies take the same body — <i>the wire format is the contract</i>. A fallback that
 * needed a different request would not be a fallback, it would be a second integration.</p>
 *
 * <p>Dates are {@code String} here for the same reason they are {@code String} in the caller's
 * {@code Application} record: the bank may hold a malformed date and this service should be able to
 * say so, rather than have Jackson refuse the whole request with a 400 before any code runs.</p>
 */
public record VerificationRequest(

        @NotBlank(message = "is required")
        String fullName,

        @NotBlank(message = "is required")
        String dateOfBirth,

        /** Optional — the tax agency scores address confirmation lower without it, but answers. */
        Address address,

        @NotNull(message = "is required")
        @Valid
        Document document) {

    public record Address(
            String line1,
            String line2,
            String city,
            String postcode,
            String country) {
    }

    public record Document(

            /** {@code PASSPORT} · {@code NATIONAL_ID} · {@code DRIVING_LICENCE}. */
            @NotBlank(message = "is required")
            String type,

            /**
             * The document number. This is the ONLY field that changes the answer.
             *
             * <p>It is also the field the caller must never log, store outside its own record, or
             * put in a callback. It arrives here, is scored, and is not written to this service's
             * log either.</p>
             */
            @NotBlank(message = "is required")
            String documentId,

            String issuingCountry,

            String expiryDate) {
    }
}
