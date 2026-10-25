# src/main 작업 지침

`src/main` 아래 코드를 수정하기 전에 읽는다. 저장소 루트의 `AGENTS.md`를 먼저 읽었다는 전제다.
아래 항목은 기존 코드에서 확인했거나 사용자와 합의한 것이다. 새 규칙을 합의하면 이 파일에 추가한다.

## Conventions

### 네이밍·코드 스타일

- 패키지 소문자, 타입 PascalCase, 메서드·필드 camelCase, 상수·enum 값 UPPER_SNAKE_CASE. 들여쓰기 공백 4칸.
- import 순서: 프로젝트·서드파티 → 빈 줄 → `java.*`/`javax.*` → 빈 줄 → static. 와일드카드 import는 쓰지 않는다.
- 의존성은 `private final` 필드 + Lombok `@RequiredArgsConstructor`로 주입한다.
  생성자에 `@Value`가 필요한 경우(`JwtProvider`)만 생성자를 직접 쓴다.
- 로깅은 Lombok `@Slf4j`. 새로 쓰는 로그·예외 메시지·주석은 한글로 쓴다(기존 영문 로그는 그대로 둔다).
  주석은 '왜'가 필요한 곳에만 단다.
- 공개 메서드로 검증하기 어려울 때만 테스트용으로 메서드·상수를 package-private으로 연다(`createJwt`, `MAX_ATTEMPTS`).
- 설정값 묶음은 `@ConfigurationProperties` record로 받는다. 값은 `application.yml`에서 `${ENV:기본값}`으로 받는다.

### 패키지 구조

- 기능은 `domain.<기능>.<계층>`(예: `client`, `client.dto`, `event`, `service`), 공통은 `global.<관심사>`에 둔다.
- 필요한 계층만 만든다. 빈 패키지나 앞으로 쓸 것 같은 클래스를 미리 만들지 않는다.

### 예외 처리

- 클라이언트에 보이는 오류는 `CorteException(ErrorProperty)`로 던진다.
  오류 코드는 `ErrorProperty`를 구현하는 enum(`status`, `code`, `message`, `@Getter @AllArgsConstructor`)으로 정의한다.
  - 사용 중인 접두사: `C` 공통(`GlobalErrorCode`), `A` 인증(`AuthErrorCode`).
  - 새 도메인은 `domain.<기능>.exception.<기능>ErrorCode`에 사용하지 않은 대문자 접두사로 만들고, 위 목록에 추가한다.
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

### 신규 계층 기본값 (선례 없음)

Entity, Repository, Controller, DB를 쓰는 도메인 Service는 아직 선례가 없다. 첫 명세에서 바꾸자는 합의가 없으면 아래를 따르고,
합의한 내용은 이 섹션을 고쳐 기록한다.

- Controller: JSON 본문은 `@Valid @RequestBody` 요청 record로, 조회 조건은 쿼리 파라미터로 받는다.
  응답은 Response record. Entity를 응답으로 반환하지 않는다.
  로그인 사용자는 `@AuthenticationPrincipal Long userId`로 받아 Service에 넘긴다.
  요청 본문·경로의 userId로 인증 주체를 대신하지 않는다.
- Service: 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에 `@Transactional`.
  자원 소유권 확인, 연관 조회, DTO 변환을 트랜잭션 안에서 끝낸다.
  조회 실패는 `CorteException`(도메인 `*_NOT_FOUND` 또는 `GlobalErrorCode.RESOURCE_NOT_FOUND`)으로 던진다.
- 유니크 제약으로 보호하는 쓰기는 `saveAndFlush`로 저장한다. 사전 조회(`exists*`)만으로 막지 않는다.
  `DataIntegrityViolationException`은 예상한 유니크 제약 위반일 때만 도메인 오류 코드로 바꾸고, 다른 무결성 오류는 그대로 전파한다.
  판별: 제약 이름을 `@UniqueConstraint(name = "uk_<테이블>_<컬럼>")`으로 정하고, 원인인
  `org.hibernate.exception.ConstraintViolationException`의 `getKind() == UNIQUE`이며
  `getConstraintName()`이 그 이름과 같은지 본다. 값은 DB마다 형태가 달라(H2: `public.<이름> INDEX ...`)
  첫 공백 앞까지 자르고 마지막 `.` 뒤만 남겨 대소문자 무시로 정확히 비교한다. 부분 문자열로 비교하지 않고, 식별하지 못하면 전파한다.
  예상한 유니크 위반으로 변환하는 경우, 다른 DB 작업 없이 바로 `CorteException`을 던진다(트랜잭션은 롤백된다).
- Entity: `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, ID는 `GenerationType.IDENTITY`.
  setter 대신 의미 있는 이름의 변경 메서드를 둔다. 연관관계는 `FetchType.LAZY`.
- `ProblemSolvedEvent`는 Service 트랜잭션 안에서 발행한다. 리스너가 `@TransactionalEventListener`라서
  트랜잭션 밖에서 발행하면 실행되지 않는다.
