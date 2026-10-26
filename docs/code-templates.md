# 코드 템플릿

같은 기능을 요청하면 누가 만들어도 같은 코드가 나오도록 이름, 기본값, 코드 모양을 고정한다.
`src/main/AGENTS.md`, `src/test/AGENTS.md`의 규칙을 실제 코드로 옮긴 것이다. 새 도메인은 아래 예시를 복사해 이름만 바꾼다.
예시 도메인은 `Note`(노트, 작성자가 있는 글)이고, 이 문서의 코드는 그대로 붙여 넣어 빌드·테스트가 통과하는 것을 확인했다.

## 1. 이름 규칙

`<E>`는 엔티티 이름(PascalCase 단수), `<e>`는 camelCase, `<es>`는 kebab-case 복수다.

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 패키지 | `domain.<e 소문자>` | `domain.note` |
| 테이블 | snake_case 복수 | `notes`, `post_likes` |
| FK 컬럼 | `<연관 이름>_id` | `author_id` |
| API 경로 | `/api/<es>`, 하위 자원은 `/api/<부모 es>/{<부모>Id}/<es>` | `/api/notes`, `/api/posts/{postId}/likes` |
| Controller·Service 메서드 | `create`, `get`, `list`, `update`, `delete` (이 순서로 배치) | `noteService.list(page, size)` |
| DTO | `Create<E>Request`, `Update<E>Request`, `<E>Response`, `<E>PageResponse`(+중첩 `Summary`) | `NoteResponse` |
| Repository 메서드 | 단건 연관 `findWith<연관>ById`, 목록 `findSummaries`, 일괄 삭제 `deleteAllBy<부모>Id` | `findWithAuthorById` |
| 오류 코드 enum | `<E>_NOT_FOUND`, `<E>_FORBIDDEN`, `DUPLICATE_<E>` 순으로 번호를 붙인다 | `NOTE_NOT_FOUND` |
| 예외 클래스 | `<E>NotFoundException`, `<E>ForbiddenException`, `Duplicate<E>Exception`. 모두 `CorteException`을 상속하고 `new CorteException(...)`을 직접 던지지 않는다 | `NoteNotFoundException` |
| 오류 메시지 | "<대상>을(를) 찾을 수 없습니다." / "<대상> 작성자만 수정하거나 삭제할 수 있습니다." / "이미 <상태>인 <대상>입니다." | |
| 테스트 메서드 | `<조건>이면_<결과>` 한글 문장 | `작성자가_아니면_수정에_실패한다` |
| 이슈 제목 | `<대상> <기능> 구현` | `노트 CRUD 구현` |
| 브랜치 | `feature/#<번호>-<대상>-<기능>` 영문 kebab 3단어 이하 | `feature/#30-note-crud` |
| 커밋·PR 제목 | `feat: <대상> <기능> 추가` | `feat: 노트 CRUD API 추가` |

## 2. 결정 기본값

사용자가 따로 정하지 않으면 아래로 정하고, 질문할 때도 이것을 추천안으로 낸다.
단, 기본값이 사용자의 목적과 맞지 않으면 목적을 따르고 그 이유를 질문에 적는다.

| 상황 | 기본값 |
| --- | --- |
| 문자열 길이 | 이름·카테고리 50, 제목 200, 본문 20000(`@Lob`), 닉네임 20, 이메일 100 |
| 숫자 입력 | `Integer` + `@NotNull @Positive`, 금액·개수 합계는 `Long` |
| 상태값 | enum + `@Enumerated(EnumType.STRING)` + `length = 20` |
| 시각 | 모든 새 엔티티에 `createdAt`·`updatedAt`(Auditing), 타입은 `LocalDateTime` |
| 연관관계 | `@ManyToOne` 단방향만 쓴다. `@OneToMany`·cascade는 쓰지 않는다 |
| 부모 삭제 | 자식은 Service에서 `deleteAllBy<부모>Id`(벌크 `@Modifying` JPQL)로 먼저 지운 뒤 부모를 지운다 |
| 좋아요·팔로우 같은 관계 | 별도 테이블 + 두 FK 유니크 제약. `POST` 201 / `DELETE` 204, 중복 409 `DUPLICATE_<E>`, 없으면 404 |
| 개수 표시 | 조회 시 COUNT로 계산한다. 목록은 ID 목록으로 한 번에 묶어 센다. 개수 컬럼은 성능 요구가 있을 때만 |
| 숫자 증감 | 원자적 업데이트 `@Modifying @Query("update ... set c = c + 1 where ...")` |
| 권한 | 소유자만 수정·삭제(403 `<E>_FORBIDDEN`). 관리자 전용은 `SecurityConfig`에 `hasRole("ADMIN")`을 추가한다 |
| 토큰은 유효한데 회원이 없음 | `UnauthorizedException`(401 `A001`) |
| 수정 요청 검증 | 필드마다 `@Pattern(regexp = "(?s).*\\S.*")`(공백만 금지) + `@Size`. `null`은 변경 없음 |
| 목록 | `page` 0, `size` 20, 최대 100(벗어나면 `InvalidInputException`), `id` 내림차순, 본문 같은 큰 필드는 빼고 `Summary`로 조회 |

## 3. 클래스 안 순서

상수 → 의존성 필드 → 생성자 → public 메서드(create, get, list, update, delete 순) → private 메서드.
Entity는 필드 → `@Builder` 생성자 → 확인 메서드(`isWrittenBy`) → 변경 메서드(`update`) 순이다.

## 4. 코드

#### `src/main/java/com/example/cortebe/domain/note/entity/Note.java`

```java
package com.example.cortebe.domain.note.entity;

import com.example.cortebe.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Note(String title, String content, User author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

    public boolean isWrittenBy(Long userId) {
        return author.getId().equals(userId);
    }

    public void update(String title, String content) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
    }
}
```

#### `src/main/java/com/example/cortebe/domain/note/repository/NoteRepository.java`

```java
package com.example.cortebe.domain.note.repository;

import com.example.cortebe.domain.note.entity.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

    @EntityGraph(attributePaths = "author")
    Optional<Note> findWithAuthorById(Long id);

    // 본문 없이 목록에 필요한 컬럼만 조회하고, 작성자는 join으로 한 번에 가져온다
    @Query(value = "select n.id as id, n.title as title, u.nickname as authorNickname, n.createdAt as createdAt "
        + "from Note n join n.author u",
        countQuery = "select count(n) from Note n")
    Page<Summary> findSummaries(Pageable pageable);

    interface Summary {
        Long getId();
        String getTitle();
        String getAuthorNickname();
        LocalDateTime getCreatedAt();
    }
}
```

#### `src/main/java/com/example/cortebe/domain/note/exception/NoteErrorCode.java`

접두사는 사용하지 않은 대문자로 정하고 `src/main/AGENTS.md` 접두사 목록에 추가한다.

```java
package com.example.cortebe.domain.note.exception;

import com.example.cortebe.global.error.exception.ErrorProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NoteErrorCode implements ErrorProperty {
    NOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "노트를 찾을 수 없습니다."),
    NOTE_FORBIDDEN(HttpStatus.FORBIDDEN, "N002", "노트 작성자만 수정하거나 삭제할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

#### `src/main/java/com/example/cortebe/domain/note/exception/NoteNotFoundException.java`

```java
package com.example.cortebe.domain.note.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class NoteNotFoundException extends CorteException {

    public NoteNotFoundException() {
        super(NoteErrorCode.NOTE_NOT_FOUND);
    }
}
```

#### `src/main/java/com/example/cortebe/domain/note/exception/NoteForbiddenException.java`

```java
package com.example.cortebe.domain.note.exception;

import com.example.cortebe.global.error.exception.CorteException;

public class NoteForbiddenException extends CorteException {

    public NoteForbiddenException() {
        super(NoteErrorCode.NOTE_FORBIDDEN);
    }
}
```

#### `src/main/java/com/example/cortebe/domain/note/dto/CreateNoteRequest.java`

```java
package com.example.cortebe.domain.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNoteRequest(
    @NotBlank @Size(max = 200)
    String title,

    @NotBlank @Size(max = 20000)
    String content
) {
}
```

#### `src/main/java/com/example/cortebe/domain/note/dto/UpdateNoteRequest.java`

```java
package com.example.cortebe.domain.note.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateNoteRequest(
    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 200)
    String title,

    @Pattern(regexp = "(?s).*\\S.*") @Size(max = 20000)
    String content
) {
}
```

#### `src/main/java/com/example/cortebe/domain/note/dto/NoteResponse.java`

```java
package com.example.cortebe.domain.note.dto;

import com.example.cortebe.domain.note.entity.Note;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record NoteResponse(
    Long id,
    String title,
    String content,
    String author,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static NoteResponse from(Note note) {
        return NoteResponse.builder()
            .id(note.getId())
            .title(note.getTitle())
            .content(note.getContent())
            .author(note.getAuthor().getNickname())
            .createdAt(note.getCreatedAt())
            .updatedAt(note.getUpdatedAt())
            .build();
    }
}
```

#### `src/main/java/com/example/cortebe/domain/note/dto/NotePageResponse.java`

```java
package com.example.cortebe.domain.note.dto;

import com.example.cortebe.domain.note.repository.NoteRepository;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public record NotePageResponse(
    List<Summary> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static NotePageResponse from(Page<NoteRepository.Summary> notes) {
        return new NotePageResponse(notes.getContent().stream().map(Summary::from).toList(),
            notes.getNumber(), notes.getSize(), notes.getTotalElements(), notes.getTotalPages());
    }

    public record Summary(Long id, String title, String author, LocalDateTime createdAt) {
        public static Summary from(NoteRepository.Summary note) {
            return new Summary(note.getId(), note.getTitle(), note.getAuthorNickname(), note.getCreatedAt());
        }
    }
}
```

#### `src/main/java/com/example/cortebe/domain/note/service/NoteService.java`

```java
package com.example.cortebe.domain.note.service;

import com.example.cortebe.domain.note.dto.CreateNoteRequest;
import com.example.cortebe.domain.note.dto.NotePageResponse;
import com.example.cortebe.domain.note.dto.NoteResponse;
import com.example.cortebe.domain.note.dto.UpdateNoteRequest;
import com.example.cortebe.domain.note.entity.Note;
import com.example.cortebe.domain.note.exception.NoteForbiddenException;
import com.example.cortebe.domain.note.exception.NoteNotFoundException;
import com.example.cortebe.domain.note.repository.NoteRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.InvalidInputException;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoteService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;

    @Transactional
    public NoteResponse create(Long userId, CreateNoteRequest request) {
        User author = userRepository.findById(userId)
            .orElseThrow(UnauthorizedException::new);
        Note note = noteRepository.save(Note.builder()
            .title(request.title())
            .content(request.content())
            .author(author)
            .build());
        return NoteResponse.from(note);
    }

    public NoteResponse get(Long id) {
        return NoteResponse.from(findNote(id));
    }

    public NotePageResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidInputException();
        }
        return NotePageResponse.from(noteRepository.findSummaries(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))));
    }

    @Transactional
    public NoteResponse update(Long userId, Long id, UpdateNoteRequest request) {
        Note note = findOwnedNote(userId, id);
        note.update(request.title(), request.content());
        // 변경 감지의 flush 이후 @LastModifiedDate가 반영되도록 즉시 반영한다
        noteRepository.flush();
        return NoteResponse.from(note);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        noteRepository.delete(findOwnedNote(userId, id));
    }

    private Note findNote(Long id) {
        return noteRepository.findWithAuthorById(id)
            .orElseThrow(NoteNotFoundException::new);
    }

    private Note findOwnedNote(Long userId, Long id) {
        Note note = findNote(id);
        if (!note.isWrittenBy(userId)) {
            throw new NoteForbiddenException();
        }
        return note;
    }
}
```

#### `src/main/java/com/example/cortebe/domain/note/controller/NoteController.java`

```java
package com.example.cortebe.domain.note.controller;

import com.example.cortebe.domain.note.dto.CreateNoteRequest;
import com.example.cortebe.domain.note.dto.NotePageResponse;
import com.example.cortebe.domain.note.dto.NoteResponse;
import com.example.cortebe.domain.note.dto.UpdateNoteRequest;
import com.example.cortebe.domain.note.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    public ResponseEntity<NoteResponse> create(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody CreateNoteRequest request) {
        NoteResponse response = noteService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/notes/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public NoteResponse get(@PathVariable Long id) {
        return noteService.get(id);
    }

    @GetMapping
    public NotePageResponse list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return noteService.list(page, size);
    }

    @PatchMapping("/{id}")
    public NoteResponse update(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                               @Valid @RequestBody UpdateNoteRequest request) {
        return noteService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        noteService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
```

## 5. 테스트

무엇을 쓰는지 고정한다.
- Service 단위 테스트: public 메서드마다 성공 1개 + 오류 경로마다 1개.
- Controller 테스트: 엔드포인트마다 성공 1개, 인증이 필요한 경로는 401 `A001` 1개(대표 엔드포인트 하나), 검증 규칙이 있으면 400 `C001` 1개,
  도메인 오류마다 1개.
- Repository 테스트: 직접 쓴 `@Query`·제약이 있을 때만. Auditing 엔티티는 `@Import(JpaAuditingConfig.class)`가 필요하다.

#### `src/test/java/com/example/cortebe/domain/note/service/NoteServiceTests.java`

```java
package com.example.cortebe.domain.note.service;

import com.example.cortebe.domain.note.dto.CreateNoteRequest;
import com.example.cortebe.domain.note.dto.NoteResponse;
import com.example.cortebe.domain.note.dto.UpdateNoteRequest;
import com.example.cortebe.domain.note.entity.Note;
import com.example.cortebe.domain.note.exception.NoteForbiddenException;
import com.example.cortebe.domain.note.exception.NoteNotFoundException;
import com.example.cortebe.domain.note.repository.NoteRepository;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.error.exception.InvalidInputException;
import com.example.cortebe.global.security.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NoteServiceTests {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NoteService noteService;

    @Test
    void 회원이_있으면_노트를_만든다() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(user(1L)));
        given(noteRepository.save(any(Note.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        NoteResponse response = noteService.create(1L, new CreateNoteRequest("제목", "본문"));

        // then
        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.author()).isEqualTo("사용자1");
    }

    @Test
    void 회원이_없으면_노트를_만들지_않는다() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> noteService.create(1L, new CreateNoteRequest("제목", "본문")))
            .isInstanceOf(UnauthorizedException.class);
        then(noteRepository).should(never()).save(any());
    }

    @Test
    void 노트가_없으면_조회에_실패한다() {
        // given
        given(noteRepository.findWithAuthorById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> noteService.get(1L))
            .isInstanceOf(NoteNotFoundException.class);
    }

    @Test
    void 페이지_범위를_벗어나면_목록_조회에_실패한다() {
        // when & then
        assertThatThrownBy(() -> noteService.list(0, 101))
            .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void 작성자가_아니면_수정에_실패한다() {
        // given
        given(noteRepository.findWithAuthorById(1L)).willReturn(Optional.of(note(user(1L))));

        // when & then
        assertThatThrownBy(() -> noteService.update(2L, 1L, new UpdateNoteRequest("새 제목", null)))
            .isInstanceOf(NoteForbiddenException.class);
    }

    @Test
    void 작성자가_아니면_삭제에_실패한다() {
        // given
        given(noteRepository.findWithAuthorById(1L)).willReturn(Optional.of(note(user(1L))));

        // when & then
        assertThatThrownBy(() -> noteService.delete(2L, 1L))
            .isInstanceOf(NoteForbiddenException.class);
        then(noteRepository).should(never()).delete(any());
    }

    private User user(Long id) {
        User user = User.builder().email("user" + id + "@corte.com").password("password").nickname("사용자" + id).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Note note(User author) {
        return Note.builder().title("제목").content("본문").author(author).build();
    }
}
```

#### `src/test/java/com/example/cortebe/domain/note/controller/NoteControllerTests.java`

```java
package com.example.cortebe.domain.note.controller;

import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoteControllerTests {

    private static final String CREATE_REQUEST = """
        {"title": "제목", "content": "본문"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private String authorToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        authorToken = bearer(saveUser("author"));
        otherToken = bearer(saveUser("other"));
    }

    @Test
    void 노트를_만들면_201과_위치를_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/notes").header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.title").value("제목"))
            .andExpect(jsonPath("$.author").value("author"));
    }

    @Test
    void 토큰이_없으면_401_A001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/notes").contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void 제목이_비어_있으면_400_C001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/notes").header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \" \", \"content\": \"본문\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void 노트를_조회하면_200을_반환한다() throws Exception {
        // given
        String location = createNote();

        // when & then
        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION, otherToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("본문"));
    }

    @Test
    void 없는_노트를_조회하면_404_N001을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/notes/999999").header(HttpHeaders.AUTHORIZATION, authorToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("N001"));
    }

    @Test
    void 목록을_최신순으로_반환한다() throws Exception {
        // given
        createNote();
        createNote();

        // when & then
        mockMvc.perform(get("/api/notes").header(HttpHeaders.AUTHORIZATION, authorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void 작성자가_수정하면_200을_반환한다() throws Exception {
        // given
        String location = createNote();

        // when & then
        mockMvc.perform(patch(location).header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"새 제목\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("새 제목"))
            .andExpect(jsonPath("$.content").value("본문"));
    }

    @Test
    void 작성자가_아니면_수정에_403_N002를_반환한다() throws Exception {
        // given
        String location = createNote();

        // when & then
        mockMvc.perform(patch(location).header(HttpHeaders.AUTHORIZATION, otherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"새 제목\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("N002"));
    }

    @Test
    void 작성자가_삭제하면_204를_반환한다() throws Exception {
        // given
        String location = createNote();

        // when & then
        mockMvc.perform(delete(location).header(HttpHeaders.AUTHORIZATION, authorToken))
            .andExpect(status().isNoContent());
    }

    private String createNote() throws Exception {
        return mockMvc.perform(post("/api/notes").header(HttpHeaders.AUTHORIZATION, authorToken)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_REQUEST))
            .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
    }

    private User saveUser(String nickname) {
        return userRepository.save(User.builder()
            .email(nickname + "@corte.com").password("password").nickname(nickname).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }
}
```

#### `src/test/java/com/example/cortebe/domain/note/repository/NoteRepositoryTests.java`

```java
package com.example.cortebe.domain.note.repository;

import com.example.cortebe.domain.note.entity.Note;
import com.example.cortebe.domain.user.entity.User;
import com.example.cortebe.domain.user.repository.UserRepository;
import com.example.cortebe.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class NoteRepositoryTests {

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 목록_조회는_작성자_닉네임과_함께_최신순으로_가져온다() {
        // given
        User author = userRepository.save(User.builder().email("a@corte.com").password("password").nickname("작성자").build());
        noteRepository.save(Note.builder().title("첫 글").content("본문").author(author).build());
        noteRepository.save(Note.builder().title("둘째 글").content("본문").author(author).build());

        // when
        Page<NoteRepository.Summary> page = noteRepository.findSummaries(
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")));

        // then
        assertThat(page.getContent()).extracting(NoteRepository.Summary::getTitle).containsExactly("둘째 글", "첫 글");
        assertThat(page.getContent().getFirst().getAuthorNickname()).isEqualTo("작성자");
    }
}
```

## 6. 커밋 순서

1. `docs: <기능> 명세 추가` (명세, 새 컨벤션)
2. `feat: <대상> 엔티티와 저장소 추가` (Entity, Repository, Repository 테스트가 있으면 함께)
3. `feat: <기능> API 추가` (ErrorCode, 예외, DTO, Service, Controller와 각 테스트)
4. 보안 설정을 바꿨으면 `feat: <기능> 권한 설정 추가`로 따로 커밋한다.
