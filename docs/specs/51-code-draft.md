# 기능: 작성 중인 풀이 코드 저장·복원

- 이슈: #51 / 브랜치: `feature/#51-code-draft`
- 기준 SHA: `d0eb9e596e6117e62433ba8f6d9c5653e2a24f49` (origin/develop)
- 목적: 로그인 회원이 문제 풀이 중 페이지를 나가거나 다른 기기로 접속해도 마지막 저장 코드를 이어서 작성한다.
- 기준 빌드: `./gradlew.bat build --console=plain`, 종료 0.

## API·권한

모든 API는 인증이 필요하다. JWT principal의 회원 ID만 사용한다. 다른 회원과 ADMIN도 타인의 코드를 조회·수정할 수 없다.

| 동작 | 요청 | 성공 응답 |
| --- | --- | --- |
| 저장·덮어쓰기 | PUT /api/code-drafts/{problemId}/{language}, JSON `{code}` | 최초 201 + Location, 이후 200, CodeDraftResponse |
| 복원 | GET /api/code-drafts/{problemId}/{language} | 200, CodeDraftResponse |

- CodeDraftResponse: `{problemId, language, code, updatedAt}`. 저장 기록이 없으면 `code: ""`, `updatedAt: null`.
- 지원 언어는 기존 Language enum: C, CPP, JAVA, CSHARP, JAVASCRIPT, PYTHON.
- problemId는 양수 Long. code는 필수 문자열, 최대 102400자(Java String 길이). 빈 문자열·공백만 있는 코드도 허용하고 줄바꿈·공백·유니코드를 그대로 보존한다.
- 저장은 해당 회원·문제·언어의 전체 코드를 교체한다. 실행·제출 시 삭제하거나 변경하지 않는다. 이전 버전 이력은 없다.
- 미인증·삭제된 회원의 유효 토큰: A001/401. 없는 문제: 기존 C004/404. 잘못된 언어·경로·JSON·null·길이: C001/400.
- 코드 원문은 오류 응답·로그에 남기지 않는다. 기존 공통 오류 형식은 유지하며 이 컨트롤러의 JSON 파싱 오류만 원문 없이 처리한다.

## 데이터·스키마

- code_drafts: id, user_id FK, problem_id FK, language varchar(20), code MEDIUMTEXT NOT NULL, created_at, updated_at.
- 유니크: uk_code_drafts_user_problem_language. 인덱스: idx_code_drafts_problem_id (문제 삭제 시 일괄 연결 정리).
- 새 테이블만 추가하고 기존 컬럼·데이터는 변경하지 않는다. MySQL의 TEXT 용량보다 큰 최대 길이·유니코드를 보존하도록 MEDIUMTEXT를 사용한다.
- 원본 문제 삭제 시 Service에서 관련 코드 저장 기록을 먼저 일괄 삭제한다. 기존 제출 이력으로 문제 삭제를 막는 규칙은 유지한다.
- 회원 삭제 API는 현재 없다. 회원 삭제 기능은 이번 범위에 추가하지 않는다.

## 트랜잭션·동시성·성능

- 읽기는 readOnly 트랜잭션, 저장은 쓰기 트랜잭션.
- 쓰기는 회원 행 → 문제 행 순서로 비관적 잠금. 같은 회원의 최초 동시 저장을 직렬화하고, 기존 문제 삭제의 문제 행 잠금과도 충돌을 조정한다.
- 유니크 제약과 saveAndFlush를 사용한다. 나중에 잠금을 얻어 처리된 요청이 최종 코드를 덮어쓴다. 클라이언트 수정 시각에 따른 충돌 해결은 제공하지 않는다.
- 조회는 유니크 키로 본인 코드 한 건만 가져온다. 목록·페이징 및 반복 연관 조회가 없다.

## 프론트 연동·범위 밖

- 프론트 구현은 범위 밖이며 `docs/code-draft-api.md`에 연동 방법을 제공한다.
- 입력 중단 2초 후 자동 저장, 요청 순서 보장, 문제·언어 변경 시 복원, 늦은 조회 응답이 새 입력을 덮어쓰는 것 방지, 저장 상태·실패 재시도 안내를 문서화한다.
- 저장 완료 전 종료 및 오프라인 데이터 보장은 제공하지 않는다. 비회원 저장, 버전 이력, 실시간 공동 편집, 코드 실행·제출 변경은 범위 밖이다.

## 새 컨벤션

- 전체 코드 교체와 최초 생성은 합의한 PUT upsert로 처리한다. 최초 201 + Location, 기존 기록 갱신 200. PATCH 부분 수정 규칙과 구분한다.
- 빈 코드와 공백을 그대로 저장하므로 trim 및 NotBlank를 적용하지 않는다.

## 테스트 대응

구현 전 동작별 테스트를 실행해 실제 레드 증거를 채운다. 새 기능의 실패 원인이 확인된 뒤 구현한다.

| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |

## 검증 결과

구현 후 명령·종료 코드·결과·SHA, 미실행 항목과 독립 리뷰 결과를 기록한다.
