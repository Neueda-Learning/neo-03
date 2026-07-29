package com.neobank.module.repository;

import com.neobank.module.model.ReviewFail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewFailRepository extends JpaRepository<ReviewFail, String> {

    List<ReviewFail> findTop10ByOrderByCreatedAtAscReviewFailIdAsc();
}
