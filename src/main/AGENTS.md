# src/main 작업 지침

`src/main` 아래 코드를 수정하기 전에 읽는다. 저장소 루트의 `AGENTS.md`를 먼저 읽었다는 전제다.
아래 항목은 기존 코드에서 확인했거나 사용자와 합의한 것이다. 새 규칙을 합의하면 이 파일에 추가한다.

## Conventions

### 네이밍·코드 스타일

- 패키지 소문자, 타입 PascalCase, 메서드·필드 camelCase, 상수·enum 값 UPPER_SNAKE_CASE. 들여쓰기 공백 4칸.
- import 순서: 프로젝트·서드파티 → 빈 줄 → `java.*`/`javax.*` → 빈 줄 → static. 와일드카드 import는 쓰지 않는다.
- 의존성은 `private final` 필드 + Lombok `@RequiredArgsConstructor`로 주입한다.
  생성자에 `@Value`가 필요한 경우(`JwtProvider`)만 생성자를 직접 쓴다.
- 로깅은 Lombok `@Slf4j`. 로그·예외 메시지·주석은 한글로 쓰고, 주석은 '왜'가 필요한 곳에만 단다.
- 테스트에서 접근해야 하는 메서드·상수는 `private` 대신 package-private으로 연다(`createJwt`, `MAX_ATTEMPTS`).
- 설정값 묶음은 `@ConfigurationProperties` record로 받는다. 값은 `application.yml`에서 `${ENV:기본값}`으로 받는다.

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

### 아직 정해지지 않은 것

- Entity, Repository, Controller는 이 브랜치에 선례가 없다.
  처음 만드는 기능에서는 명세 단계에서 방식을 제안해 사용자 승인을 받고, 승인된 규칙을 이 섹션 위에 추가한다.
