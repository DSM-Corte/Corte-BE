package com.example.cortebe.domain.workbook.repository;

import com.example.cortebe.domain.workbook.entity.Workbook;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WorkbookRepository extends JpaRepository<Workbook, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workbook w where w.id = :id")
    Optional<Workbook> findLockedById(Long id);

    @Query(value = "select w.id as id, w.name as name from Workbook w", countQuery = "select count(w) from Workbook w")
    Page<Summary> findSummaries(Pageable pageable);

    interface Summary {
        Long getId();
        String getName();
    }
}
