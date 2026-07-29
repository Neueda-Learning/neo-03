package com.neobank.module.repository;

import com.neobank.module.model.ThirdPartyAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThirdPartyAttemptRepository extends JpaRepository<ThirdPartyAttempt, String> {
}