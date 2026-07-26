package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * <h2>⚠️ PLACEHOLDER — delete this and write your own table.</h2>
 *
 * <p>This exists so the skeleton has something to write and something to show: three columns, one
 * row per application, and a status that is always {@code ACCEPTED}. <b>It is not your module's
 * data model, and it is not a base class to extend.</b></p>
 *
 * <p>Your real table is whatever your business topic needs — a verification case with its checks,
 * a screening hit with its match score, a credit decision with the limit it approved. It will have
 * different columns and probably more than one table.</p>
 *
 * <h3>How to replace it</h3>
 *
 * <ol>
 *   <li>Write a new change set, {@code db/changelog/changes/002-create-<your-table>.yaml}, that
 *       creates your table. <b>Never edit {@code 001}</b> once it has run — Liquibase compares
 *       checksums and refuses to start.</li>
 *   <li>Add your entity, repository and view record beside these.</li>
 *   <li>When nothing references {@code DemoShowcase} any more, add a change set that drops
 *       {@code demo_showcase} and delete this class.</li>
 * </ol>
 *
 * <p>Do not grow columns onto {@code demo_showcase} instead. A table called "demo showcase"
 * holding your real credit decisions is the kind of thing that survives to production.</p>
 */
@Entity
@Table(name = "demo_showcase")
public class DemoShowcase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The id the orchestrator gave us — from the <em>envelope</em>, not from inside the
     * application object. See {@code ApplicationRequest.applicationId}.
     */
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    /** Always {@code ACCEPTED} while this is a placeholder. Stored as text, not an ordinal. */
    @Column(nullable = false, length = 32)
    private String status;

    /** When this module answered. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DemoShowcase() {
        // JPA
    }

    public DemoShowcase(String applicationId, Decision status) {
        this.applicationId = applicationId;
        this.status = status.name();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
