package com.neobank.module.repository;

import com.neobank.module.model.KycRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycRecordRepository extends JpaRepository<KycRecord, String> {

    Optional<KycRecord> findFirstByApplicationIdOrderByCreatedAtDescKycIdDesc(String applicationId);

    List<KycRecord> findAllByOrderByCreatedAtDescKycIdDesc();
}
