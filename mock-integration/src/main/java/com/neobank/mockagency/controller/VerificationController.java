package com.neobank.mockagency.controller;

import com.neobank.mockagency.dto.VerificationRequest;
import com.neobank.mockagency.dto.VerificationResponse;
import com.neobank.mockagency.model.Agency;
import com.neobank.mockagency.service.AgencyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h2>The only endpoint the bank calls.</h2>
 *
 * <pre>
 * POST /api/v1/agencies/national/verifications    the National Identity Agency (primary)
 * POST /api/v1/agencies/tax/verifications         the Tax Agency (fallback)
 * </pre>
 *
 * <p><b>One path variable, not two controllers.</b> Both agencies take the same request and answer
 * the same shape — that is the contract, and it is what lets the caller fail over by changing a URL
 * segment rather than by writing a second integration. If the fallback needed its own request
 * shape, failing over would mean a second client, a second mapper and a second set of tests, and
 * teams would (correctly) decide it was not worth it.</p>
 */
@RestController
@RequestMapping("/api/v1/agencies")
public class VerificationController {

    private final AgencyService agencies;

    public VerificationController(AgencyService agencies) {
        this.agencies = agencies;
    }

    @PostMapping("/{agency}/verifications")
    public VerificationResponse verify(@PathVariable("agency") String agencySlug,
                                       @Valid @RequestBody VerificationRequest request) {
        return agencies.verify(Agency.ofSlug(agencySlug), request);
    }
}
