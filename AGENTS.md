# AGENTS.md

이 저장소에서 작업하는 모든 AI 에이전트가 작업 시작 전에 읽는 문서다.
코드와 이 문서가 다르면 코드를 사실로 보고, 차이를 사용자에게 알린다.

## 1. Project Overview

### 목적

AI 코드 리뷰를 받으며 성장하는 코딩테스트 사이트의 백엔드(Corte-BE).

### 기술 스택

| 구분 | 내용 |
| --- | --- |
| 언어 | Java 21 (Gradle toolchain) |
| 빌드 | Gradle Wrapper 9.7.1, 단일 모듈 `Corte-BE` |
| 프레임워크 | Spring Boot 4.1.1, io.spring.dependency-management 1.1.7 |
| 웹/보안 | Spring MVC(Servlet), Spring Security(stateless), Jakarta Validation |
| 영속성 | Spring Data JPA, MySQL 8.4(compose), 테스트는 H2(MySQL 모드) |
| 외부 연동 | Spring Cloud 2025.1.3 OpenFeign + feign-hc5 (GitHub REST API) |
| 인증 | jjwt 0.13.0 (HS 서명 access token) |
| 기타 | Lombok, Jackson 3(`tools.jackson`), Docker(eclipse-temurin 21) |

- 라이브러리 버전은 Spring Boot·Spring Cloud BOM을 따른다. jjwt만 직접 고정한다.
- Spring Cloud 2025.1.x는 Boot 4.0 대상이라 `spring.cloud.compatibility-verifier.enabled=false`로 둔다.

### 모듈/패키지 구조 (`src/main/java/com/example/cortebe`)

```
CorteBeApplication            # @EnableFeignClients, @ConfigurationPropertiesScan
domain/
  github/                     # 풀이를 사용자 GitHub 레포에 커밋
    GithubAppProperties       # @ConfigurationProperties("github.app") record
    client/GithubClient       # Feign 클라이언트
    client/dto/GithubDto      # 외부 API 요청/응답 record 모음
    event/                    # ProblemSolvedEvent, ProblemSolvedEventListener
    service/                  # GithubService, GithubAppTokenProvider
global/
  config/                     # AsyncConfig, FilterConfig, SecurityConfig
  error/                      # GlobalExceptionFilter, GlobalExceptionHandler
    exception/                # CorteException, ErrorProperty, GlobalErrorCode
    response/                 # ErrorResponse, ValidErrorResponse
  security/
    exception/AuthErrorCode
    jwt/                      # JwtProvider, JwtAuthenticationFilter
```

- 설정: `src/main/resources/application.yml`. 값은 `${ENV:기본값}` 형태로 환경 변수에서 받는다.
  필수 값은 `JWT_SECRET`(Base64)이다. GitHub App은 `GITHUB_APP_CLIENT_ID`, `GITHUB_APP_PRIVATE_KEY`(PKCS#8)를 쓴다.
- 테스트 설정: `src/test/resources/application.yml`이 main 설정을 대체한다(H2 메모리 DB, `create-drop`).
- 실행: `compose.yml`(MySQL + app), `Dockerfile`(빌드/실행 다단계 이미지).

### 주요 도메인

| 도메인 | 상태 |
| --- | --- |
| 인증 | JWT 발급·검증과 필터만 있다. 회원 엔티티와 로그인 API는 아직 없다. |
| GitHub 풀이 저장 | 구현됨. 문제를 맞히면 README와 코드를 커밋 하나로 저장한다. |
| 문제, 제출, 채점, AI 리뷰, 점수/랭킹 | 아직 구현되지 않았다. 코드에 없는 구조를 사실처럼 가정하지 않는다. |

### 아키텍처

- 요청 흐름: `GlobalExceptionFilter`(최우선 순서) → Spring Security(`JwtAuthenticationFilter`)
  → DispatcherServlet → Controller → `GlobalExceptionHandler`(MVC 예외).
- 보안: 세션 없음, CSRF 끔. `/auth/**`만 허용하고 나머지는 인증이 필요하다.
  `JwtAuthenticationFilter`는 `/auth/**`에서 토큰을 검사하지 않는다. 인증 주체(principal)는 `Long userId`다.
- GitHub 저장: 트랜잭션 안에서 `ProblemSolvedEvent`를 발행하면 커밋된 뒤
  `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` 리스너가 `GithubService`를 호출한다.
  Git Data API로 tree·commit을 만들고, 브랜치는 `force=false`로 갱신한다.
  충돌(409/422)이 나면 최신 ref로 최대 3회 다시 시도한다. 실패는 리스너가 ERROR 로그로 남기고 삼킨다.
- 종료 시 비동기 작업 큐를 최대 30초 기다린다(`AsyncConfig`).

## 2. Conventions

컨벤션은 디렉터리별 AGENTS.md에 있다. 파일을 수정하기 전에 그 경로에서 가장 가까운 AGENTS.md를 읽는다.
도구가 하위 AGENTS.md를 자동으로 읽지 않더라도 직접 열어 읽는다.

| 경로 | 문서 | 내용 |
| --- | --- | --- |
| `src/main/` | `src/main/AGENTS.md` | 네이밍, 패키지 구조, 예외 처리, DTO 변환 |
| `src/test/` | `src/test/AGENTS.md` | 테스트 위치·이름·구조, 테스트 범위 |

## 3. Working Rules

- 작업 전 이 문서, 수정할 경로의 AGENTS.md, 대상 코드, 현재 브랜치, 작업 트리 상태를 확인한다.
- 새 기능 요청은 4. Feature Workflow를 따른다. 버그 수정·작은 변경은 2~4단계 중 필요한 부분만 따른다.
- 기존 파일 수정을 우선하고, 새 파일은 꼭 필요할 때만 만든다.
- 요청과 무관한 포맷팅·리네이밍·리팩터링을 하지 않는다. 발견한 문제는 고치지 말고 보고한다.
- 커밋은 하나의 논리적 작업 단위로 나눈다. 형식: `type: 한글 요약`
  (`feat`, `fix`, `refactor`, `test`, `docs`, `chore`).
- main 브랜치에서 직접 작업하지 않는다. main이면 작업 브랜치를 먼저 만든다.
- push하지 않는다. 사용자가 명시적으로 요청하기 전에는 마음대로 push하지 않는다.
- 버그를 고칠 때는 재현 테스트를 먼저 작성해 실패를 확인한 뒤 수정한다.
- 확인한 사실, 추론, 실행하지 못한 검증을 구분해 보고한다.

## 4. Feature Workflow

사용자가 기능을 설명하면 아래 순서로 끝까지 만든다. 사용자 확인은 1단계 끝에서 한 번만 받는다.

### 1단계: 명세 (여기서 멈추고 승인받는다)

관련 코드와 디렉터리별 AGENTS.md를 읽은 뒤, 아래 형식으로 명세를 보여준다.

```
## 기능: <이름>
- 목적: <한 줄>
- API: <METHOD /path> — 요청 / 응답 / 인증 필요 여부
- 데이터: 엔티티·필드·연관관계·제약(유니크, not null)
- 규칙: 비즈니스 규칙과 경계값
- 오류: 상황 → 오류 코드(<접두사>###) / HTTP 상태
- 동시성: 동시 요청 지점과 보호 방법(락 / 유니크 제약 / 원자적 업데이트), 없으면 "없음"
- 성능: 목록 조회 페이징, 연관 조회 방식(fetch join 등)
- 범위 밖: 이번에 만들지 않는 것
- 새 컨벤션 제안: 선례가 없는 계층(Entity, Controller 등)의 작성 방식
- 질문: [꼭 필요] … / [있으면 좋음] … / [가정] 묻지 않고 이렇게 처리함: …
```

- 사용자의 설명에 근거가 없는 기능을 명세에 넣지 않는다.
- 사용자가 승인하거나 수정 의견을 주면, 반영한 최종 명세를 기준으로 2단계부터 멈추지 않고 진행한다.
- 승인된 새 컨벤션은 해당 디렉터리의 AGENTS.md에 추가한다.

### 2단계: 브랜치

- 작업 트리에 커밋하지 않은 변경이 있으면 멈추고 사용자에게 묻는다.
- `git fetch origin develop` 후 `origin/develop`을 기준으로 `feature/#<이슈번호>-<영문-kebab-설명>` 브랜치를 만든다
  (예: `feature/#21-submission-create`).
  이슈 번호를 받지 못했다면 1단계 질문에 [꼭 필요]로 포함한다.

### 3단계: 구현

- Entity → Repository → Service → Controller 순서로, 계층마다 실패하는 테스트를 먼저 쓰고 통과시킨다.
- 커밋은 동작하는 단위로 나눈다(예: `feat: 제출 엔티티와 저장소 추가` → `feat: 제출 생성 API 추가`).
  각 커밋 시점에 `./gradlew build`가 통과해야 한다.
- 구현 중 명세에 없는 결정이 필요하면:
  - 결과가 크게 달라지는 결정이면 멈추고 묻는다.
  - 아니면 가장 단순한 방법으로 정하고 보고서의 "가정"에 적는다.

### 4단계: 검증과 보고

Verification Checklist를 모두 확인한 뒤 아래 형식으로 보고한다.

```
## 결과: <기능 이름> (브랜치: <이름>)
### 커밋별 리뷰 요약
- <해시> <커밋 메시지>
  - 변경: 무엇을 왜 바꿨는지
  - 리뷰 포인트: 리뷰어가 특히 봐야 할 곳(파일:줄)과 이유
### 가정
### Verification Checklist (항목마다 통과/실패/해당 없음 + 근거)
### 발견했지만 고치지 않은 문제
### 남은 작업
```

## 5. Verification Checklist

모든 작업 완료 전에 아래 항목을 확인하고, 항목마다 통과/실패/해당 없음과 근거를 보고한다.
실행하지 못한 항목은 통과로 표시하지 않는다.

- [ ] 컨벤션: 변경한 경로의 AGENTS.md에 있는 Conventions를 모두 준수한다.
- [ ] 빌드: `./gradlew build`가 통과한다(테스트 포함, 실패 시 원인 수정 후 재실행).
- [ ] 성능:
  - 연관 엔티티를 반복문 안에서 조회하지 않는다(필요 시 fetch join, `@EntityGraph`, batch size 사용).
  - 목록 조회에는 페이징을 적용한다.
- [ ] 동시성: 동시 요청이 가능한 로직(제출, 채점 결과 갱신, 점수/랭킹 등)에는
  락, 유니크 제약, 원자적 업데이트 중 무엇으로 보호했는지 명시한다. 대상이 없으면 해당 없음으로 적는다.
- [ ] 변경 범위: `git diff`를 확인해 작업과 무관한 수정(포맷팅, 리네이밍, 리팩터링)이 없다.
