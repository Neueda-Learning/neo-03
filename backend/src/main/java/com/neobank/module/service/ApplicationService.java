package com.neobank.module.service;

import com.neobank.module.dto.DemoShowcaseView;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.DemoShowcase;
import com.neobank.module.repository.DemoShowcaseRepository;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>Your module's work happens here. This is the class you came here to write.</h2>
 *
 * <p>An ordinary service class, like the ones you wrote in Week 2 — the difference is only that the
 * layers around it are already built. The controller has answered {@code 202} and let the
 * orchestrator go; {@code integrations.orchestrator} handles both ends of the wire; the repository
 * handles storage. None of that changes when your logic changes.</p>
 *
 * <p><b>Right now it does the three smallest things that prove the contract works:</b> it prints a
 * line, it writes a row, and it reports {@code ACCEPTED}. All three are placeholders. Replacing
 * them <em>one at a time</em>, keeping the journey green after each, is the way to spend the first
 * hour — the most common way to lose a hackathon day is writing all the logic before running any
 * of it.</p>
 *
 * <h3>What to replace, in order</h3>
 *
 * <ol>
 *   <li><b>The log line</b> → whatever your module actually needs to say.</li>
 *   <li><b>The row</b> → your own table. {@link DemoShowcase} explains how; the short version is a
 *       new Liquibase change set and a new entity, not extra columns on {@code demo_showcase}.</li>
 *   <li><b>The always-{@code ACCEPTED} status</b> → your rules. Read what you need off
 *       {@code request.application()} — it is fully typed, so your IDE will show you the fields —
 *       and return {@code ACCEPTED}, {@code REJECTED} or {@code REFERRED} with a reason a bank
 *       employee could read to a customer. Keep the rules in a method of their own, and test them
 *       without Spring: a rule is a function from an application to an outcome.</li>
 * </ol>
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final Executor executor;
    private final DemoShowcaseRepository demoShowcase;
    private final OrchestratorClient orchestrator;

    /**
     * {@code applicationTaskExecutor} is the thread pool Spring Boot configures for you. Tune it in
     * {@code application.yml} under {@code spring.task.execution.*} — pool size matters once your
     * logic calls a slow mock, because that is what limits how many applications you can handle at
     * once.
     */
    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              DemoShowcaseRepository demoShowcase,
                              OrchestratorClient orchestrator) {
        this.executor = executor;
        this.demoShowcase = demoShowcase;
        this.orchestrator = orchestrator;
    }

    /**
     * Hand the work to the pool and return immediately.
     *
     * <p>The controller calls this and then writes the {@code 202}. <b>Nothing here may block:</b>
     * the orchestrator is holding a connection open, and a module that does its work on the request
     * thread turns a fast journey into a slow one.</p>
     */
    public void processApplicationAsync(ApplicationRequest request) {
        executor.execute(() -> processApplication(request));
    }

    /**
     * Do the work: say something, store something, report something.
     *
     * <p>Package-private on purpose — the outside world goes through
     * {@link #processApplicationAsync}, and a unit test can call this directly on the test thread,
     * which is what makes it testable without a thread pool.</p>
     *
     * <p><b>Deliberately not {@code @Transactional}.</b> The repository's own save is transactional;
     * wrapping the whole method would put the HTTP call inside that transaction, so a slow or
     * unreachable orchestrator could roll back a row this module had already committed. Store
     * first, report second, and let the two fail independently. When you add several writes that
     * must land together, put {@code @Transactional} on a method that does only the writes.</p>
     */
    void processApplication(ApplicationRequest request) {
        String applicationId = request.applicationId();
        try {
            // 1 — say something. summary() is the one line every module logs on receipt.
            log.info("Hello world from processApplication — {}", request.summary());

            // 2 — store something. ⚠️ demo_showcase is a placeholder; see DemoShowcase.
            demoShowcase.save(new DemoShowcase(applicationId, Decision.ACCEPTED));

            // 3 — report something. Always ACCEPTED until you write rules.
            orchestrator.applicationStatusUpdate(applicationId, Decision.ACCEPTED,
                    "hello world from processApplication");
        } catch (RuntimeException e) {
            // A module that throws never reports, and the orchestrator then waits out its 30s
            // timeout and ends the journey FAILED with nothing to explain it. So: refer it to a
            // human and say why. Keep this guard when you replace the body above.
            log.error("processApplication failed for {} — referring", applicationId, e);
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "module error: " + e);
        }
    }

    /** Everything this module has answered, newest first — what its own UI reads. */
    @Transactional(readOnly = true)
    public List<DemoShowcaseView> findAll() {
        return demoShowcase.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(DemoShowcaseView::of)
                .toList();
    }
}
