package com.neobank.mockagency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * <h2>The government identity sources this module pretends to talk to.</h2>
 *
 * <p>A bank does not examine passports itself. It pays a specialist — Onfido, Jumio, Veriff — that
 * reaches into government databases and answers "how confident are we that this person is who they
 * say they are", as a number. This service is that specialist, in a box, for a laptop.</p>
 *
 * <p>Two sources, because one is what real banks avoid:</p>
 * <ul>
 *   <li><b>National Identity Agency</b> — the primary. Examines the document itself, so it can
 *       answer {@code documentGenuine}.</li>
 *   <li><b>Tax Agency</b> — the fallback, tried only once the primary's retry budget is spent. A
 *       tax office can confirm that a name, date of birth and address belong together; it cannot
 *       look at a passport. So it answers the same confidence with one fewer check.</li>
 * </ul>
 *
 * <p><b>It answers 100% of the time by default.</b> Every misbehaviour dial seeds to off. Turn one
 * on ({@code PUT /api/v1/admin/config/national}) and the caller's retry ladder, failover and
 * circuit breaker become things you can watch rather than things you have to read.</p>
 *
 * <p><b>Stateless on purpose.</b> No database: the dials live in memory and a confidence score is a
 * pure function of the document id. Restarting it resets the dials, which is the right behaviour
 * for a dev tool and costs nothing, because it stores nothing worth keeping.</p>
 */
@SpringBootApplication
public class MockAgencyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockAgencyApplication.class, args);
    }
}
