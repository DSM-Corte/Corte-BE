package com.example.cortebe.domain.problem.repository;

import com.example.cortebe.domain.problem.entity.Problem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    Page<Summary> findAllBy(Pageable pageable);

    interface Summary {
        Long getId();
        String getTitle();
    }
}
