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

### 네이밍·코드 스타일

- 패키지 소문자, 타입 PascalCase, 메서드·필드 camelCase, 상수·enum 값 UPPER_SNAKE_CASE. 들여쓰기 공백 4칸.
- import 순서: 프로젝트·서드파티 → 빈 줄 → `java.*`/`javax.*` → 빈 줄 → static. 와일드카드 import는 쓰지 않는다.
- 의존성은 `private final` 필드 + Lombok `@RequiredArgsConstructor`로 주입한다.
  생성자에 `@Value`가 필요한 경우(`JwtProvider`)만 생성자를 직접 쓴다.
- 로깅은 Lombok `@Slf4j`. 로그·예외 메시지·주석은 한글로 쓰고, 주석은 '왜'가 필요한 곳에만 단다.
- 테스트에서 접근해야 하는 메서드·상수는 `private` 대신 package-private으로 연다(`createJwt`, `MAX_ATTEMPTS`).
- 설정값 묶음은 `@ConfigurationProperties` record로 받는다.

### 패키지 구조

- 기능은 `domain.<기능>.<계층>`(예: `client`, `client.dto`, `event`, `service`), 공통은 `global.<관심사>`에 둔다.
- 필요한 계층만 만든다. 빈 패키지나 앞으로 쓸 것 같은 클래스를 미리 만들지 않는다.

### 예외 처리

- 클라이언트에 보이는 오류는 `CorteException(ErrorProperty)`로 던진다.
  오류 코드는 `ErrorProperty`를 구현하는 enum(`status`, `code`, `message`, `@Getter @AllArgsConstructor`)으로 정의한다.
  - 공통: `GlobalErrorCode`(`C001`~). 인증: `AuthErrorCode`(`A001`~). 영역마다 enum과 접두사를 따로 둔다.
- `CorteException`(또는 cause가 `CorteException`인 예외)은 `GlobalExceptionFilter`가 `ErrorResponse` JSON으로 바꾼다.
  Spring MVC 표준 예외는 `GlobalExceptionHandler`가 `GlobalErrorCode`로 바꾼다.
- 처리하지 못한 예외는 `INTERNAL_ERROR`로 응답하고 내부 메시지를 노출하지 않는다.
  응답이 이미 전송됐으면 덮어쓰지 않고 원래 예외를 다시 던진다.
- 로그 레벨: 예상한 오류는 WARN, 처리하지 못한 오류는 ERROR와 스택 트레이스.
- HTTP 응답으로 나가지 않는 내부·설정 오류(GitHub 키 파싱 등)는 `IllegalStateException`에 원인을 담아 던진다.

### DTO

- DTO는 `record`로 만든다. 외부 API용 DTO는 `client/dto`의 holder 클래스(`GithubDto`, `final` + private 생성자)에
  중첩 record로 모으고, snake_case 필드는 `@JsonProperty`로 매핑한다.
- 도메인 DTO 변환(Entity → Response)은 Service의 private 메서드에서 한다. 별도 Mapper나 DTO 정적 팩터리를 새로 만들지 않는다.
  - 예외: 공통 오류 응답(`ErrorResponse.of`, `ValidErrorResponse.of`, `FieldErrorDetail.from`)은 기존 정적 팩터리를 유지한다.

### 테스트

- 대상과 같은 패키지의 `src/test/java`에 `<대상>Test` 이름으로 둔다.
- 메서드명은 한글 문장 + 밑줄(`충돌이_3번_계속되면_예외를_던진다`). 본문은 `// given`, `// when`, `// then`
  (또는 `// when & then`) 주석으로 나눈다.
- 검증은 AssertJ(`assertThat`, `assertThatThrownBy`), 목 설정은 BDDMockito(`given`, `willThrow`, `willAnswer`)를 쓴다.
- 단위 테스트: `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`, 또는 생성자로 직접 만든다.
- 통합 테스트: `@SpringBootTest` + `@MockitoBean`. 외부 HTTP는 JDK `HttpServer` 스텁과 `@DynamicPropertySource`로 대체한다.
- DB 테스트는 H2를 쓴다. H2 MySQL 모드는 MySQL의 락·격리 수준을 그대로 재현하지 않으므로,
  동시성 동작을 바꿨다면 MySQL에서 검증했는지 따로 보고한다.

## 3. Working Rules

- 작업 전 이 문서, 대상 코드, 현재 브랜치, 작업 트리 상태를 확인한다.
- 기존 파일 수정을 우선하고, 새 파일은 꼭 필요할 때만 만든다.
- 요청과 무관한 포맷팅·리네이밍·리팩터링을 하지 않는다. 발견한 문제는 고치지 말고 보고한다.
- 커밋은 하나의 논리적 작업 단위로 나눈다. 형식: `type: 한글 요약`
  (`feat`, `fix`, `refactor`, `test`, `docs`, `chore`).
- main 브랜치에서 직접 작업하지 않는다. main이면 작업 브랜치를 먼저 만든다.
- push하지 않는다. 사용자가 명시적으로 요청하기 전에는 마음대로 push하지 않는다.
- 버그를 고칠 때는 재현 테스트를 먼저 작성해 실패를 확인한 뒤 수정한다.
- 확인한 사실, 추론, 실행하지 못한 검증을 구분해 보고한다.

## 4. Verification Checklist

모든 작업 완료 전에 아래 항목을 확인하고, 항목마다 통과/실패/해당 없음과 근거를 보고한다.
실행하지 못한 항목은 통과로 표시하지 않는다.

- [ ] 컨벤션: Conventions 섹션의 모든 항목을 준수한다.
- [ ] 빌드: `./gradlew build`가 통과한다(테스트 포함, 실패 시 원인 수정 후 재실행).
- [ ] 성능:
  - 연관 엔티티를 반복문 안에서 조회하지 않는다(필요 시 fetch join, `@EntityGraph`, batch size 사용).
  - 목록 조회에는 페이징을 적용한다.
- [ ] 동시성: 동시 요청이 가능한 로직(제출, 채점 결과 갱신, 점수/랭킹 등)에는
  락, 유니크 제약, 원자적 업데이트 중 무엇으로 보호했는지 명시한다. 대상이 없으면 해당 없음으로 적는다.
- [ ] 변경 범위: `git diff`를 확인해 작업과 무관한 수정(포맷팅, 리네이밍, 리팩터링)이 없다.
