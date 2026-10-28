# src/main 작업 지침

`src/main` 아래 코드를 수정하기 전에 읽는다. 저장소 루트의 `AGENTS.md`를 먼저 읽었다는 전제다.
아래 항목은 기존 코드에서 확인했거나 사용자와 합의한 것이다. 새 규칙을 합의하면 이 파일에 추가한다.
기존 코드가 아래 규칙과 다르더라도 요청 없이 고치지 않는다. 새로 쓰는 코드만 따른다.
새 코드는 `docs/code-templates.md`의 템플릿을 복사해 만든다.

## Conventions

### 네이밍·코드 스타일

- 패키지 소문자, 타입 PascalCase, 메서드·필드 camelCase, 상수·enum 값 UPPER_SNAKE_CASE. 들여쓰기 공백 4칸.
- import 순서: 프로젝트·서드파티 → 빈 줄 → `java.*`/`javax.*` → 빈 줄 → static. 와일드카드 import는 쓰지 않는다.
- 의존성은 `private final` 필드 + Lombok `@RequiredArgsConstructor`로 주입한다.
  생성자에 `@Value`가 필요한 경우(`JwtProvider`)만 생성자를 직접 쓴다.
- 로깅은 Lombok `@Slf4j`. 새로 쓰는 로그·예외 메시지·주석은 한글로 쓴다. 주석은 '왜'가 필요한 곳에만 단다.
- 공개 메서드로 검증하기 어려울 때만 테스트용으로 메서드·상수를 package-private으로 연다(`MAX_ATTEMPTS`).
- 설정값 묶음은 `@ConfigurationProperties` record로 받는다. 값은 `application.yml`에서 `${ENV:기본값}`으로 받는다.
- ID는 `Long`, `GenerationType.IDENTITY`.

### 계층별 규칙

- Controller: 경로는 `/api/<복수형>`(인증만 `/auth`). 로그인 사용자는 `@AuthenticationPrincipal Long userId`로 받는다.
  요청 본문·경로의 userId로 인증 주체를 대신하지 않는다. Entity를 응답으로 반환하지 않는다.
  - 생성: `201` + `Location` 헤더 + 본문(`ResponseEntity.created(...)`). 조회·수정: `200` + 본문. 삭제: `204`.
  - 수정은 `PATCH` 부분 수정이다. 요청에서 `null`인 필드는 바꾸지 않는다.
  - 목록: `page`(기본 0), `size`(기본 20, 최대 100) 쿼리. 범위를 벗어나면 `INVALID_INPUT`. 정렬은 `id` 내림차순.
- Service: 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에 `@Transactional`.
  자원 소유권 확인, 연관 조회, DTO 변환을 트랜잭션 안에서 끝낸다.
  소유권은 엔티티 메서드(`post.isWrittenBy(userId)`)로 확인하고, 아니면 도메인 `*_FORBIDDEN`(403)을 던진다.
- Repository: 단건 연관 조회는 `@EntityGraph`, 목록은 필요한 컬럼만 interface projection(`Summary`) + `countQuery`로 조회한다.
  컬렉션 연관은 ID 목록으로 한 번에 따로 조회한다(`findTagsByProblemIds`).
- Entity: `@Table(name = "<복수형>")`, `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`,
  생성자에 `@Builder`. setter 대신 의미 있는 이름의 변경 메서드(`update`)를 둔다.
  연관관계는 `FetchType.LAZY`. 시각은 `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate`/`@LastModifiedDate`.
  이름 규칙: 인덱스 `idx_<테이블>_<컬럼>`, 유니크 `uk_<테이블>_<컬럼>`. 삭제는 실제 삭제다.
- 유니크 제약으로 보호하는 쓰기는 `saveAndFlush`로 저장한다. 사전 조회(`exists*`)만으로 막지 않는다.
  `DataIntegrityViolationException`은 예상한 유니크 제약 위반일 때만 도메인 오류로 바꾸고, 다른 무결성 오류는 전파한다.
  판별: 원인인 `org.hibernate.exception.ConstraintViolationException`의 `getKind() == UNIQUE`이고
  `getConstraintName()`이 정한 이름과 같은지 본다. 값은 DB마다 형태가 달라(H2: `public.<이름> INDEX ...`)
  첫 공백 앞까지 자르고 마지막 `.` 뒤만 남겨 대소문자 무시로 정확히 비교한다. 식별하지 못하면 전파한다.
  변환하는 경우 다른 DB 작업 없이 바로 예외를 던진다(트랜잭션은 롤백된다).
- `ProblemSolvedEvent`는 Service 트랜잭션 안에서 발행한다. 리스너가 `@TransactionalEventListener`라서
  트랜잭션 밖에서 발행하면 실행되지 않는다.

### 예외 처리

- 클라이언트에 보이는 오류는 도메인 오류 코드 enum + 전용 예외 클래스로 던진다. `new CorteException(...)`을 직접 던지지 않는다.
  공통 오류도 전용 클래스(`InvalidInputException`, `UnauthorizedException`, `InvalidTokenException`)를 쓰고, 없으면 같은 모양으로 만든다.
  - `domain.<기능>.exception.<기능>ErrorCode`: `ErrorProperty` 구현, `status`·`code`·`message`, `@Getter @AllArgsConstructor`.
  - 예외: `<상황>Exception extends CorteException`, 인자 없는 생성자에서 `super(<기능>ErrorCode.X)`(`PostNotFoundException`).
  - 코드는 접두사 + 세 자리 순번, 메시지는 "~습니다." 존댓말이다.
  - 사용 중인 접두사: `C` 공통, `A` 인증, `U` 회원, `B` 게시판, `J` 채점. 새 도메인은 사용하지 않은 대문자로 만들고 여기에 추가한다.
- `CorteException`(또는 cause가 `CorteException`인 예외)은 `GlobalExceptionFilter`가 `ErrorResponse` JSON으로 바꾼다.
  Spring MVC 표준 예외는 `GlobalExceptionHandler`가 `GlobalErrorCode`로 바꾼다.
- 오류 응답 형태: `{timestamp, status, code, message}`. 검증 오류는 `errors[{field, value, reason}]`가 붙는다.
- 처리하지 못한 예외는 `INTERNAL_ERROR`로 응답하고 내부 메시지를 노출하지 않는다.
  응답이 이미 전송됐으면 덮어쓰지 않고 원래 예외를 다시 던진다.
- 로그 레벨: 예상한 오류는 WARN, 처리하지 못한 오류는 ERROR와 스택 트레이스.
- HTTP 응답으로 나가지 않는 내부·설정 오류(GitHub 키 파싱 등)는 `IllegalStateException`에 원인을 담아 던진다.

### DTO

- 요청 DTO: 필드와 검증 어노테이션만 담은 `record`(`SignupRequest`). Lombok `@Getter`는 record에 쓸 수 없다.
- 응답 DTO: `@Builder` record + 정적 팩터리 `from(Entity)`(`PostResponse.from`). 변환 호출은 Service에서 한다.
  목록 응답은 `{content, page, size, totalElements, totalPages}` record와 중첩 `Summary` record로 만든다.
- 이벤트처럼 값을 묶는 record도 `@Builder`를 쓴다(`ProblemSolvedEvent`, `ErrorResponse`).
- 외부 API DTO는 `client/dto`의 holder 클래스(`GithubDto`, `final` + private 생성자)에 중첩 record로 모은다.
  snake_case 필드는 `@JsonProperty`로 매핑하고 `@Builder`는 쓰지 않는다.

## 기존 코드에서 정해진 것

- 인증: access token만 쓴다. subject는 userId 문자열, `role` 클레임은 `USER`/`ADMIN`. 리프레시 토큰은 없다.
  ADMIN은 `ADMIN_EMAILS`에 있는 이메일(공백 제거, 대소문자 무시)로 로그인한 회원이다.
- 비동기 작업은 실패해도 재시도하지 않고 ERROR 로그만 남긴다(best-effort).
- 외부 API 주소는 프로퍼티로 바꿀 수 있게 한다(`github.api-url`). 테스트는 이 값을 스텁 서버 주소로 바꾼다.
- GitHub 저장 형식: 디렉터리 `"{problemId}. {제목}"`(`/`·`\`·제어문자는 `_`), 파일 `README.md`·`Main.{확장자}`,
  커밋 메시지 `[{problemId}] {제목} 풀이`, 레포의 기본 브랜치에 저장한다.
- 풀이 저장 이벤트는 GitHub App `installationId`, `owner`, `repo`를 요구한다. 회원에 이 연동 정보가 아직 없다.
- 언어 이름(`language`)과 파일 확장자(`fileExtension`)는 따로 다룬다.
