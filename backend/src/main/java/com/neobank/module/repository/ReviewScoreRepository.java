package com.neobank.module.repository;

import com.neobank.module.model.ReviewScore;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewScoreRepository extends JpaRepository<ReviewScore, String> {

    List<ReviewScore> findTop10ByOrderByCreatedAtAscReviewScoreIdAsc();
}
