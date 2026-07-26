/**
 * <h2>Everything that talks to the orchestrator. Do not change anything in this package.</h2>
 *
 * <p>Three shapes and one client, and that is the whole conversation: the orchestrator POSTs an
 * {@link com.neobank.module.integrations.orchestrator.ApplicationRequest} carrying an
 * {@link com.neobank.module.integrations.orchestrator.Application}, this module answers
 * {@code 202} immediately, and once it has an answer
 * {@link com.neobank.module.integrations.orchestrator.OrchestratorClient} PUTs an
 * {@link com.neobank.module.integrations.orchestrator.ApplicationStatusUpdate} to
 * {@code /api/v1/applications/{applicationId}}.</p>
 *
 * <p>The {@code 202} body is built inline in {@code ApplicationController} rather than living here:
 * nothing reads it — the orchestrator dispatches with {@code toBodilessEntity} — so a record of its
 * own was ceremony. Its shape is still fixed by the contract and pinned by a test.</p>
 *
 * <p>{@code Application} is the customer's form as data and is worth reading in full before you
 * write a rule — it is the only place the domain is written down in Java, and the fields your
 * module needs are all already there.</p>
 *
 * <p>Every module in the system speaks this shape and the orchestrator parses it — a field
 * renamed here is a module that silently stops working the day it joins the stack. The rule
 * is the folder, not one file: if you are editing something in
 * {@code integrations.orchestrator}, stop.</p>
 *
 * <h3>Your own integrations go beside it, not in it</h3>
 *
 * <p>The orchestrator is simply the external system you did not choose. Any system your
 * module talks to — an ID provider, an e-sign service, a card bureau, real or mocked — gets
 * its own sibling package with the same shape: a client, and the records it exchanges.</p>
 *
 * <pre>{@code
 * integrations/
 * ├── orchestrator/   ← this package. Fixed.
 * └── idprovider/     ← yours: IdProviderClient + its own records
 * }</pre>
 *
 * <p>Whatever you add here, declare it in {@code MOCKED_DEPENDENCIES} so it shows up on
 * {@code /info} and in the UI header — that register is a graded deliverable.</p>
 */
package com.neobank.module.integrations.orchestrator;
