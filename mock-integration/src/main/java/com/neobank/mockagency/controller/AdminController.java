package com.neobank.mockagency.controller;

import com.neobank.mockagency.dto.AgencyConfig;
import com.neobank.mockagency.model.Agency;
import com.neobank.mockagency.service.AgencyService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h2>The dials — how you make the provider misbehave without touching code.</h2>
 *
 * <pre>
 * GET  /api/v1/admin/config             both agencies, plus their call counts
 * PUT  /api/v1/admin/config/national    { "latencyMs": 5000, "failureRatePct": 0, "killSwitch": false }
 * POST /api/v1/admin/reset              everything back to healthy
 * </pre>
 *
 * <p>Three demos live behind this controller:</p>
 * <ul>
 *   <li><b>Failover</b> — kill switch on <i>national</i> only. The bank should burn its retry
 *       budget on the primary, fall back to the tax agency, and still verify the applicant.</li>
 *   <li><b>Outage</b> — kill switch on both. The bank should park the application for a human, and
 *       must never reject it: an outage says nothing about the applicant.</li>
 *   <li><b>Timeout</b> — {@code latencyMs} above the caller's 2000 ms budget. Same ending as an
 *       outage, but it proves the timeout rather than the connection.</li>
 * </ul>
 *
 * <p><b>Reset is not a convenience.</b> A demo you cannot put back is a demo you only get to give
 * once — the module brief asks for the dials to go back to defaults and Maria to verify again at 92
 * in one attempt.</p>
 *
 * <p>No authentication, deliberately: single-user local stack, and the same rule as every other
 * service here.</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AgencyService agencies;

    public AdminController(AgencyService agencies) {
        this.agencies = agencies;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> byAgency = new LinkedHashMap<>();
        agencies.allConfigs().forEach((agency, config) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agency", agency.name());
            entry.put("latencyMs", config.latencyMs());
            entry.put("failureRatePct", config.failureRatePct());
            entry.put("killSwitch", config.killSwitch());
            byAgency.put(agency.slug(), entry);
        });
        body.put("agencies", byAgency);
        body.put("calls", agencies.callCounts());
        return body;
    }

    @PutMapping("/config/{agency}")
    public AgencyConfig update(@PathVariable("agency") String agencySlug,
                               @Valid @RequestBody AgencyConfig config) {
        return agencies.updateConfig(Agency.ofSlug(agencySlug), config);
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        agencies.reset();
        return config();
    }
}
