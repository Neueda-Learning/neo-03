package com.neobank.module;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * One module of the neo-bank onboarding journey.
 *
 * <p>It accepts an application from the orchestrator with {@code 202}, runs
 * {@link com.neobank.module.service.DecisionRules} off the request thread, and calls the
 * orchestrator back with {@code ACCEPTED} / {@code REJECTED} / {@code REFERRED}.</p>
 *
 * <p>Which module this is — its id, display name and BIAN domain — is configuration, not
 * code: see {@code application.yml} and {@code .env.example}.</p>
 */
@SpringBootApplication
public class ModuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModuleApplication.class, args);
    }
}
