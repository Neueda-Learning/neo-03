package com.neobank.module.repository;

import com.neobank.module.model.KycRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycRecordRepository extends JpaRepository<KycRecord, String> {
}
