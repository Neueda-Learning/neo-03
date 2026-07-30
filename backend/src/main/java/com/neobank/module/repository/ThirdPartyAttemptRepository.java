package com.neobank.module.repository;

import com.neobank.module.model.ThirdPartyAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThirdPartyAttemptRepository extends JpaRepository<ThirdPartyAttempt, String> {

    /**
     * Every call this module made for one case, in the order it made them.
     *
     * <p><b>Ordered by {@code attemptNumber}, deliberately not by {@code createdAt}.</b> All of a
     * case's attempts are written in a single {@code saveAll} batch once the ladder has finished,
     * so their {@code created_at} values are identical and ordering by them leaves the sequence up
     * to the database — the same defect {@code DemoShowcaseRepository} documents for the board.
     * {@code attemptNumber} is assigned as the ladder runs and is the only field that actually
     * knows what happened first.</p>
     *
     * <p>Covered by {@code idx_third_party_attempts_kyc} from change set 003.</p>
     */
    List<ThirdPartyAttempt> findByKycIdOrderByAttemptNumberAsc(String kycId);
}
