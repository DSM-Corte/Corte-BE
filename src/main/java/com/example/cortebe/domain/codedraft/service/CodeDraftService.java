package com.example.cortebe.domain.codedraft.service;

import com.example.cortebe.domain.codedraft.dto.CodeDraftResponse;
import com.example.cortebe.domain.codedraft.dto.SaveCodeDraftRequest;
import com.example.cortebe.domain.codedraft.entity.CodeDraft;
import com.example.cortebe.domain.codedraft.exception.CodeDraftProblemNotFoundException;
import com.example.cortebe.domain.codedraft.repository.CodeDraftRepository;
import com.example.cortebe.domain.judge.runner.Language;
import com.example.cortebe.domain.problem.entity.Problem;
import com.example.cortebe.domain.problem.repository.ProblemRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeDraftService {

    private final CodeDraftRepository codeDraftRepository;
    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;

    @Transactional
    public SaveResult save(Long userId, Long problemId, Language language, SaveCodeDraftRequest request) {
        // 아직 저장 기록이 없는 최초 요청도 같은 회원 행을 잠가 중복 생성을 막는다.
        User user = userRepository.findLockedById(userId).orElseThrow(UnauthorizedException::new);
        // 문제 삭제도 이 행을 잠그므로 삭제 직후 코드 기록이 다시 생기지 않는다.
        Problem problem = problemRepository.findLockedById(problemId).orElseThrow(CodeDraftProblemNotFoundException::new);
        Optional<CodeDraft> existing = codeDraftRepository.findByUserIdAndProblemIdAndLanguage(userId, problemId, language);
        CodeDraft draft = existing.orElseGet(() -> CodeDraft.builder().user(user).problem(problem).language(language)
            .code(request.code()).build());
        if (existing.isPresent()) draft.update(request.code());
        CodeDraft saved = codeDraftRepository.saveAndFlush(draft);
        return SaveResult.builder().response(CodeDraftResponse.from(saved)).created(existing.isEmpty()).build();
    }

    public CodeDraftResponse get(Long userId, Long problemId, Language language) {
        if (!userRepository.existsById(userId)) throw new UnauthorizedException();
        if (!problemRepository.existsById(problemId)) throw new CodeDraftProblemNotFoundException();
        return codeDraftRepository.findByUserIdAndProblemIdAndLanguage(userId, problemId, language)
            .map(CodeDraftResponse::from).orElseGet(() -> CodeDraftResponse.empty(problemId, language));
    }

    @Builder
    public record SaveResult(CodeDraftResponse response, boolean created) {
    }
}
