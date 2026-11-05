package com.example.cortebe.domain.codedraft.repository;

import com.example.cortebe.domain.codedraft.entity.CodeDraft;
import com.example.cortebe.domain.judge.runner.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CodeDraftRepository extends JpaRepository<CodeDraft, Long> {

    Optional<CodeDraft> findByUserIdAndProblemIdAndLanguage(Long userId, Long problemId, Language language);

    @Modifying
    @Query("delete from CodeDraft draft where draft.problem.id = :problemId")
    int deleteAllByProblemId(Long problemId);
}
