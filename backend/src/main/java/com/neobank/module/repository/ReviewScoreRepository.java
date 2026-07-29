package com.neobank.module.repository;

import com.neobank.module.model.ReviewScore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewScoreRepository extends JpaRepository<ReviewScore, String> {

    List<ReviewScore> findTop10ByReviewResultOrderByCreatedAtAscReviewScoreIdAsc(String reviewResult);

    Optional<ReviewScore> findFirstByKycIdAndReviewResult(String kycId, String reviewResult);
}
