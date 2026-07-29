package com.neobank.module.repository;

import com.neobank.module.model.ReviewFail;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewFailRepository extends JpaRepository<ReviewFail, String> {

    List<ReviewFail> findTop10ByReviewResultOrderByCreatedAtAscReviewFailIdAsc(String reviewResult);

    Optional<ReviewFail> findFirstByKycIdAndReviewResult(String kycId, String reviewResult);
}
