# AGENTS.md

이 저장소에서 작업하는 AI 에이전트의 작업 지침이다. 작업 시작 전에 읽는다(자동으로 읽지 않는 도구는 "AGENTS.md를 먼저 읽고 시작해"로 요청).
코드와 이 문서가 다르면 코드를 사실로 보고, 차이를 사용자에게 알린다.

## 1. Project Overview

목적: AI 코드 리뷰를 받으며 성장하는 코딩테스트 사이트의 백엔드(Corte-BE).

### 기술 스택

| 구분 | 내용 |
| --- | --- |
| 언어·빌드 | Java 21 (Gradle toolchain), Gradle Wrapper 9.7.1, 단일 모듈 `Corte-BE` |
| 프레임워크 | Spring Boot 4.1.1, io.spring.dependency-management 1.1.7 |
| 웹/보안 | Spring MVC, Spring Security(stateless, BCrypt), Jakarta Validation, springdoc-openapi 3.1.1 |
| 영속성 | Spring Data JPA(Auditing), MySQL 8.4(compose), 테스트는 H2(MySQL 모드) |
| 외부 연동 | Spring Cloud 2025.1.3 OpenFeign + feign-hc5 (GitHub REST API) |
| 인증 | jjwt 0.13.0 (HS 서명 access token) |
| 기타 | Lombok, Jackson 3(`tools.jackson`), Docker(eclipse-temurin 21) |

- 버전은 Spring Boot·Spring Cloud BOM을 따르고 jjwt·springdoc만 직접 고정한다.
  Spring Cloud 2025.1.x는 Boot 4.0 대상이라 `spring.cloud.compatibility-verifier.enabled=false`로 둔다.

### 패키지 구조 (`src/main/java/com/example/cortebe`)

```
domain/<기능>/{controller, service, entity, repository, dto, exception}   # 필요한 계층만
  auth/      회원가입·로그인          user/     회원 엔티티·오류 코드
  board/     게시판                   problem/  문제
  github/    풀이를 사용자 GitHub 레포에 커밋 (client, client/dto, event, service)
global/
  config/    Async, Filter, JpaAuditing, OpenApi, Security
  error/     GlobalExceptionFilter, GlobalExceptionHandler, exception/, response/
  security/  Role, exception/, jwt/(JwtProvider, JwtAuthenticationFilter)
  swagger/   API별 오류 응답 예시 자동 생성
```

- 설정: `src/main/resources/application.yml`. 값은 `${ENV:기본값}`으로 환경 변수에서 받는다. 필수 값은 `JWT_SECRET`(Base64).
  GitHub App은 `GITHUB_APP_CLIENT_ID`, `GITHUB_APP_PRIVATE_KEY`(PKCS#8), Swagger는 `SWAGGER_ENABLED`.
- 테스트 설정은 `src/test/resources/application.yml`(H2). 실행: `compose.yml`(MySQL + app), `Dockerfile`.

### 주요 도메인

| 도메인 | 상태 |
| --- | --- |
| 인증·회원 | `POST /auth/signup`(201), `POST /auth/login`(access token). 리프레시 토큰 없음. 회원은 email(유니크)·password·nickname |
| 게시판 | `/api/posts` CRUD. 작성자만 수정·삭제 |
| 문제 | `/api/problems` CRUD. 조회는 공개, 생성·수정·삭제는 ADMIN. 상세 명세 `docs/problem-api.md` |
| GitHub 풀이 저장 | `ProblemSolvedEvent`를 받아 README와 코드를 커밋 하나로 저장. 이벤트를 발행하는 채점 코드는 없다 |
| 제출, 채점, AI 리뷰, 점수/랭킹 | 아직 없다. 코드에 없는 구조를 사실처럼 가정하지 않는다 |

### 아키텍처

- 요청 흐름: `GlobalExceptionFilter`(최우선) → Spring Security(`JwtAuthenticationFilter`) → Controller → `GlobalExceptionHandler`.
- 보안: 세션 없음, CSRF 끔. 공개 경로는 Swagger, `/auth/**`, `GET`·`HEAD /api/problems/**`.
  문제 쓰기는 `ADMIN`, 나머지는 인증 필요. `/auth/**`에서는 토큰을 검사하지 않는다.
  principal은 `Long userId`, 권한은 JWT `role` 클레임(`USER`/`ADMIN`, 없으면 `USER`)이다.
- 트랜잭션: `open-in-view: false`. 지연 로딩과 DTO 변환은 Service 트랜잭션 안에서 끝낸다.
- 스키마: 마이그레이션 도구 없음. main은 `ddl-auto: update`, 테스트는 `create-drop`.
  `update`는 기존 데이터가 있는 컬럼에 NOT NULL·유니크를 안전하게 추가하지 못한다.
- GitHub 저장: 트랜잭션 안에서 `ProblemSolvedEvent`를 발행하면 커밋 후 `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`
  리스너가 `GithubService`를 호출한다. 브랜치 ref 갱신이 409/422면 최신 ref로 다시 만든다(총 3회).
  최종 실패는 ERROR 로그만 남긴다. 종료 시 비동기 작업을 최대 30초 기다린다(`AsyncConfig`).

## 2. Conventions

컨벤션은 디렉터리별 AGENTS.md에 있다. 파일을 수정하기 전에 그 경로에서 가장 가까운 AGENTS.md를 읽는다.
도구가 하위 AGENTS.md를 자동으로 읽지 않더라도 직접 열어 읽는다.

| 경로 | 문서 | 내용 |
| --- | --- | --- |
| `src/main/` | `src/main/AGENTS.md` | 네이밍, 계층별 규칙, 예외 처리, DTO, 기존 코드에서 정해진 것 |
| `src/test/` | `src/test/AGENTS.md` | 테스트 위치·이름·구조, 테스트 범위 |
| 새 코드 전체 | `docs/code-templates.md` | 이름 규칙, 결정 기본값, 계층별·테스트 코드 템플릿 |

## 3. Working Rules

- 작업 전 이 문서, 수정할 경로의 AGENTS.md, 대상 코드, 현재 브랜치, 작업 트리 상태를 확인한다.
- 새 기능과 버그 수정은 4. Feature Workflow를 따른다. 버그는 재현 조건 위주로 묻고, 라벨 `bug`, 브랜치 `fix/#<번호>-<설명>`을 쓴다.
  진행 중인 작업 브랜치에서 받은 수정 요청은 그 브랜치에서 한다.
- 기존 파일 수정을 우선하고 새 파일은 꼭 필요할 때만 만든다. 요청과 무관한 포맷팅·리네이밍·리팩터링은 하지 않고, 범위 밖 문제는 보고만 한다.
- 커밋은 논리적 작업 단위로 나누고 `type: 한글 요약`(feat·fix·refactor·test·docs·chore)으로 쓴다.
  커밋·이슈·PR에 `Co-Authored-By`, "Generated with ..." 같은 AI 작성 표시를 넣지 않는다.
- main·develop 브랜치에서 직접 작업하거나 push하지 않는다.
- push는 4. Feature Workflow 5단계에서 그 작업 브랜치에만 한다. 그 밖에는 사용자가 요청할 때만 한다.
- 버그를 고칠 때는 재현 테스트를 먼저 작성해 실패를 확인한 뒤 수정한다.
- 확인한 사실, 추론, 실행하지 못한 검증을 구분해 보고한다.

## 4. Feature Workflow

이 폴더에서 세션을 시작하면 에이전트가 대화를 이끌어 아래 순서로 끝까지 진행한다.

### 0단계: 첫 인사

첫 응답은 아래 문장으로 시작한다. 작업 트리에 변경이 있거나 `feature/#*`·`fix/#*` 브랜치이면 그 상태를 한 줄 덧붙인다.

```
이 하네스는 원하는 기능을 말로 설명하면 질문으로 빈 부분을 채운 뒤 끝까지 개발해 주는 도구이고,
질문 → GitHub 이슈·브랜치 생성 → 구현·커밋 → push·PR → 쉬운 설명 순서로 진행할게요.

기능을 당신의 말로 설명해주세요!
원하시면 기술 스택도 질문하도록 할게요. 설명 끝에 "기술스택도 질문해줘"라고 이어붙여주세요!
```

- 사용자의 첫 메시지가 이미 기능 설명이면 인사를 생략하고 바로 1단계 질문으로 간다.
  설명에 "기술스택도 질문해줘"가 있으면 기술 스택 질문을 원한 것으로 본다.
- 재개: "#N 이어서 해줘"이고 `feature/#N-*`(또는 `fix/#N-*`) 브랜치와 `docs/specs/N-*.md`가 있으면, 그 브랜치에서
  명세를 코드·테스트·`git log <기준SHA>..HEAD`와 대조해 남은 것부터 3단계를 잇는다. 이슈·브랜치를 새로 만들지 않는다.

### 1단계: 질문 (더 물을 것이 없을 때까지)

- 사용자의 목적을 가장 중요하게 본다. 이 기능으로 누가 무엇을 이루려는지 먼저 파악하고, 불분명하면 첫 질문으로 묻는다.
  모든 결정과 추천안은 목적에 맞는지로 판단한다. 템플릿·기본값이 목적과 맞지 않으면 목적을 따르고 그 이유를 적는다.
- 설명을 아래 명세 항목에 채워 보고, 비어 있거나 모호한 항목을 묻는다. 답을 받으면 다시 채워 보고 또 묻는다.
- 한 번에 3~5개, 결과가 크게 달라지는 것부터 묻고 질문마다 추천안을 붙인다. AGENTS.md에 정해진 것은 묻지 않는다.
- 기술 스택 질문을 원했으면 매번 기술 선택(라이브러리, 저장 방식, 외부 서비스 등)도 묻고, 아니면 현재 스택과 규칙으로 정한다.
- 사용자 설명에 근거가 없는 기능을 넣지 않는다. 범위를 넓히는 생각은 "범위 밖" 후보로만 묻는다.
- 더 물을 것이 없으면 명세 전체를 보여주고 "이대로 진행할까요?"를 마지막으로 묻는다.
  "진행해", "좋아"처럼 분명한 답이면 2단계로 간다. 수정 의견이면 반영해 다시 묻는다.

```
## 기능: <이름>
- 목적(누가 무엇을 이루려는지, 모든 결정의 기준) / API: METHOD /path, 요청, 성공 응답(상태 코드, 본문), 인증·권한
- 데이터: 엔티티·필드·연관관계·제약, 스키마 영향(기존 데이터에 미치는 영향)
- 규칙: 비즈니스 규칙, 경계값, 소유권 / 오류: 상황 → 코드(<접두사>###) / HTTP 상태
- 트랜잭션 / 동시성: 보호 방법(락 / 유니크 제약 / 원자적 업데이트) 또는 "없음" / 성능: 페이징, 연관 조회
- 기술 선택(기술 스택 질문을 원했을 때) / 범위 밖 / 새 컨벤션(src/main/AGENTS.md "계층별 규칙"과 다른 것)
```

### 2단계: 이슈와 브랜치

1. 작업 트리가 깨끗한지 확인한다(`git status --short`). 변경이 있으면 멈추고 묻는다.
2. 이슈를 만든다: `gh issue create --title "<한글 기능명>" --label enhancement --assignee @me --body-file <파일>`.
   본문은 팀 형식 `### 이슈 내용` / `- <목적>` / `### 작업 내용` / `- [ ] <작업 단위>`를 따른다.
3. `git fetch origin develop` 후 `git switch -c feature/#<이슈번호>-<영문-kebab-설명> origin/develop`.
   같은 이름 브랜치가 있으면 멈추고 묻는다. `git rev-parse HEAD`를 기준 SHA로 기록한다.
4. 기준 빌드 `./gradlew build`. 코드를 바꾸기 전에 실패하면 멈추고 보고한다.
5. 확정한 명세(기준 SHA 포함)를 `docs/specs/<이슈번호>-<설명>.md`로 저장하고, 새 컨벤션은 해당 AGENTS.md에 반영해
   `docs: <기능> 명세 추가`로 커밋한다.

### 3단계: 구현

- 필요한 계층만 Entity → Repository → Service → Controller 순서로 만든다.
  `docs/code-templates.md`의 예시를 복사해 이름만 바꾸고, 이름 규칙·결정 기본값·커밋 순서를 그대로 따른다.
- TDD는 명세의 동작 단위(규칙, 경계값, 오류 경로, HTTP 계약)로 한다.
  실패하는 테스트를 먼저 쓰고, 실패 이유가 "기능이 없어서"인지 확인한 뒤 구현한다.
  getter뿐인 엔티티나 단순 위임처럼 검증할 동작이 없는 코드에는 별도 테스트를 만들지 않는다.
- 커밋은 동작하는 단위로 나눈다(예: `feat: 제출 엔티티와 저장소 추가` → `feat: 제출 생성 API 추가`). 커밋 전 `./gradlew build`가 통과해야 한다.
- 명세에 없는 결정은 가장 단순한 방법으로 정하고 보고서의 "가정"에 적는다.

### 멈추는 경우 (2단계 이후)

각 단계의 멈춤 조건에 더해 다음 경우에 멈추고 사용자에게 묻는다. 그 밖에는 끝까지 진행한다.
- 확정한 명세의 API, 데이터, 규칙을 바꿔야 할 때
- 이번 변경과 무관한 원인(기존 실패, JDK·네트워크·`gh` 인증 등 환경)으로 진행할 수 없을 때(범위 밖 코드는 고치지 않는다)
- 데이터 삭제, 기존 컬럼 변경처럼 되돌리기 어려운 작업이 필요한데, 명세에 그 대상과 영향이 적혀 있지 않을 때

### 4단계: 검증

Verification Checklist와 결과가 사용자 목적을 이루는지 확인한다. 실패나 미실행이 있으면 push하지 않고 보고한다.

### 5단계: push와 PR

1. `git push -u origin <브랜치>` 후 `gh pr create --base develop --title "<type>: <한글 요약>" --body-file <파일> --assignee @me --reviewer <리뷰어>`.
   `<리뷰어>`는 이름을 고정하지 않고 매번 조회한다: 저장소 협업자(`gh api repos/{owner}/{repo}/collaborators -q '.[].login'`) 중 현재 `gh` 사용자(`gh api user -q .login`)를 뺀 전원을 쉼표로 잇는다.
2. 본문은 짧게 쓴다. `- `로 구분한 변경 내용 최대 5줄과 이슈 연결만 넣는다.
   ```
   ### PR 내용
   - <변경 내용>

   ### 관련 이슈
   resolved #<이슈번호>
   ```

### 6단계: 보고와 쉬운 설명

1. 이슈와 PR 링크를 전달한다.
2. 쉬운 설명: 코드를 모르는 사람에게 설명하듯 이 기능이 사용자의 목적을 어떻게 이루는지, 이제 무엇을 할 수 있는지,
   어떤 경우에 막히는지를 쉬운 말로 쓴다. 전문 용어는 꼭 필요할 때만 쓰고 바로 풀어 쓴다.
   비유는 최소로 하고, 사용자가 요청하면 그때 비유로 다시 설명한다.
3. 개발자용 요약: 커밋별 변경과 리뷰 포인트(파일:줄), 가정, Checklist 결과(항목마다 통과/실패/해당 없음),
   발견했지만 고치지 않은 문제.
4. 다음에 할 일을 1~2개 제안한다.

## 5. Verification Checklist

모든 작업 완료 전에 아래 항목을 확인하고, 항목마다 통과/실패/해당 없음과 근거를 보고한다.
실행하지 못한 항목은 통과로 표시하지 않는다. 실패나 미실행이 하나라도 있으면 완료라고 하지 않는다.

- [ ] 컨벤션: 변경한 경로의 AGENTS.md에 있는 Conventions를 모두 준수한다.
- [ ] 빌드: `./gradlew build`가 통과한다(테스트 포함).
  이번 변경이 원인이면 고친 뒤 재실행한다. 아니면 고치지 않고 실패로 보고한다.
- [ ] 성능:
  - 연관 엔티티를 반복문 안에서 조회하지 않는다(필요 시 fetch join, `@EntityGraph`, batch size 사용).
  - 목록 조회에는 페이징을 적용한다.
- [ ] 동시성: 동시 요청이 가능한 로직(제출, 채점 결과 갱신, 점수/랭킹 등)에는
  락, 유니크 제약, 원자적 업데이트 중 무엇으로 보호했는지 명시한다. 대상이 없으면 해당 없음으로 적는다.
- [ ] 변경 범위: `git status --short`가 비어 있고, `git diff <기준SHA>...HEAD`에
  작업과 무관한 수정(포맷팅, 리네이밍, 리팩터링)이 없다.
