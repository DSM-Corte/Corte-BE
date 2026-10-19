# Project Overview

- 목적: AI 코드 리뷰를 받으며 성장하는 코딩테스트 사이트의 백엔드.
- 현재 단계: 애플리케이션 부트스트랩, DB 설정, 공통 예외 처리만 구현되어 있다.
- 문제, 제출, 채점, AI 리뷰는 제품의 주요 도메인이며 아직 구현되지 않았다.
  점수·랭킹, 코드 실행 환경, AI 연동 방식도 현재 코드에서 확인되지 않는다.
- 단일 Gradle 모듈 `Corte-BE`, 기본 패키지 `com.example.cortebe`.
- Java 21(toolchain), Spring Boot 4.1.1, Gradle Wrapper 9.7.1,
  Spring dependency-management 플러그인 1.1.7.
- Spring MVC, Spring Data JPA, Jakarta Validation, Lombok, Jackson 3 사용.
  개별 라이브러리 버전은 직접 고정하지 않고 Spring Boot 의존성 관리에 따른다.
- 운영/개발 DB: MySQL(Docker Compose 이미지 8.4). 테스트 DB: H2.
  H2 버전도 Spring Boot 의존성 관리에 따른다.
- `src/main/java/com/example/cortebe`:
  - `CorteBeApplication`: Spring Boot 진입점.
  - `global/config`: 필터 등록 등 공통 설정.
  - `global/error`: Servlet 필터 및 MVC 예외 처리.
  - `global/error/exception`: 예외, 오류 코드 및 공통 인터페이스.
  - `global/error/response`: 일반 오류·검증 오류 응답 record.
- `src/main/resources/application.yml`: DB 연결, JPA 설정.
  `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JPA_DDL_AUTO`로 설정을 덮어쓴다.
  현재 기본값은 `ddl-auto: update`, `open-in-view: false`이다.
- `src/test/java`: 테스트. `src/test/resources/application-test.yml`: H2 설정.
- `compose.yml`: MySQL과 애플리케이션. `Dockerfile`: Java 21 빌드/실행 다단계 이미지.
- 현재 아키텍처: Servlet 기반 MVC 애플리케이션.
  최우선 순서의 `GlobalExceptionFilter`가 필터 체인 예외를 처리하고,
  `GlobalExceptionHandler`가 MVC 요청·검증 예외를 HTTP 응답으로 변환한다.
  도메인 Controller/Service/Repository와 엔티티는 아직 없다.

# Conventions

## 기존 코드에서 확인한 관례

- 패키지는 소문자, 타입은 PascalCase, 메서드·필드는 camelCase,
  enum 상수는 UPPER_SNAKE_CASE를 사용한다. Java 들여쓰기는 공백 4칸이다.
- 공통 설정은 `global.config`, 공통 오류 처리는 `global.error` 하위에 둔다.
- 주입받는 의존성은 `final` 필드와 생성자 주입을 사용한다.
  기존 필터는 Lombok `@RequiredArgsConstructor`를 사용한다.
- 오류 계약은 `ErrorProperty`의 HTTP 상태·문자열 코드·메시지로 표현한다.
  `CorteException`이 이 계약을 보유하고 `GlobalErrorCode`가 공통 오류를 정의한다.
- 공통 오류 응답은 `record`와 Lombok builder를 사용한다.
  `timestamp`, `status`, `code`, `message`를 포함하고 검증 응답은 `errors`를 추가한다.
- 기존 공통 오류 응답 변환은 응답 타입의 `of/from` 정적 팩터리에 있다.
  이는 아래 서비스 DTO 변환 규칙과 구분되는 기존 공통 인프라 구조다.
- 예상 가능한 오류는 WARN, 처리하지 못한 오류는 ERROR와 예외 스택으로 기록한다.
  예기치 않은 오류 응답에는 내부 예외 메시지 대신 공통 오류 메시지를 사용한다.
- 테스트는 `src/test/java`의 대응 패키지에 두고 JUnit Jupiter `@Test`를 사용한다.
  애플리케이션 컨텍스트 테스트는 `@SpringBootTest`, Gradle은 `useJUnitPlatform()`이다.
- import 정렬, 세부 줄바꿈 등 일관된 근거가 없는 서식은 새 규칙으로 추정하지 않는다.

## 사용자와 합의한 신규 규칙

- 도메인은 `domain.<기능>.controller/service/repository/entity/dto`로 나눈다.
  필요한 계층만 만들며 빈 패키지나 미래 기능을 미리 생성하지 않는다.
- 도메인 DTO 변환은 Service에서 수행한다. 별도 Mapper나 DTO 변환 팩터리를
  자동으로 도입하지 않는다. 기존 공통 오류 응답 팩터리는 유지한다.
- 로직은 JUnit 단위 테스트, HTTP 계약은 MVC 테스트, DB 동작은 JPA 테스트로 검증한다.
- DB를 사용하는 테스트는 H2를 사용하고 `test` 프로필을 활성화한다.
  테스트 설정은 메모리 DB와 `create-drop`을 사용해 로컬 MySQL과 분리한다.
- H2의 MySQL 모드는 MySQL의 락·격리 수준·SQL 동작을 완전히 보장하지 않는다.
  해당 동작을 변경하면 MySQL 검증 여부와 미검증 범위를 따로 보고한다.
- 검증 오류에 입력 원문을 응답하거나 로그로 남기지 않는다.
  기존 `value` 응답 필드는 호환성을 위해 null로 유지한다.
- 이미 전송이 시작된 응답은 초기화하거나 오류 JSON으로 덮어쓰지 않는다.

# Working Rules

- 작업 전 이 문서, 대상 코드, 작업 트리 변경 사항, 현재 브랜치를 확인한다.
- 기존 파일 수정을 우선하고 새 파일은 꼭 필요할 때만 만든다.
- 사용자 변경을 보존하고 요청과 무관한 포맷팅·리네이밍·리팩터링을 하지 않는다.
- 현재 요구사항을 만족하는 가장 단순한 구현을 우선한다.
  미구현 도메인이나 불확실한 관례는 사실로 기록하지 말고 사용자에게 확인한다.
- 커밋은 하나의 논리적 작업 단위로 나눈다.
  형식: `type: 한글 요약` (`feat`, `fix`, `refactor`, `test`, `docs`, `chore`).
- push하지 않는다. 임의의 push도 금지한다.
- main 브랜치에서 직접 작업하지 않는다. main이면 작업 브랜치를 만든 후 수정한다.
- 수정한 버그에는 원인과 재현 조건을 밝히고 필요한 회귀 테스트를 추가한다.
- 코드와 테스트로 확인한 사실, 추론, 실행하지 못한 검증을 구분해 보고한다.

# Verification Checklist

모든 작업 완료 전에 아래 항목을 확인하고 각각 통과·실패·해당 없음과 근거를 보고한다.
실행하지 못한 항목은 통과로 표시하지 말고 이유와 남은 검증을 명시한다.

- [ ] 컨벤션: Conventions 섹션의 모든 항목을 준수한다.
- [ ] 빌드: `./gradlew build`가 통과한다(테스트 포함).
      실패 시 원인을 수정한 뒤 재실행한다. Windows에서는 `./gradlew.bat build`를 사용한다.
      Java 21 도구 체인과 의존성 다운로드 환경을 확인한다.
- [ ] 성능: 연관 엔티티를 반복문 안에서 조회하지 않는다.
      필요 시 fetch join, `@EntityGraph`, batch size를 사용한다.
      목록 조회에는 페이징을 적용한다.
- [ ] 동시성: 동시 요청이 가능한 제출, 채점 결과 갱신, 점수·랭킹 등에
      락, 유니크 제약, 원자적 업데이트 중 무엇을 적용했는지 명시한다.
      대상 로직이 없으면 해당 없음으로 보고하고 보호됐다고 추정하지 않는다.
- [ ] 변경 범위: `git diff`와 새 파일을 확인해 작업과 무관한
      포맷팅·리네이밍·리팩터링이 없음을 확인한다.
