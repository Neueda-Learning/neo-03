package com.neobank.module.model;

/**
 * The three outcomes this module may reach about an application.
 *
 * <p>Only {@link #ACCEPTED} advances the journey; {@link #REJECTED} ends it and
 * {@link #REFERRED} parks it for a human. The names are the exact strings the orchestrator
 * expects on the callback, which is why this is an enum and not a {@code String}: a typo
 * cannot reach the wire.</p>
 */
public enum Decision {

    /** Your step passed. The only outcome that moves the application to the next module. */
    ACCEPTED,

    /** A rule failed and no human can rescue it. Ends the journey. */
    REJECTED,

    /** You cannot decide automatically. Parks the application for a human. */
    REFERRED
}
