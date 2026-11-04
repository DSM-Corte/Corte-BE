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
| 최초 저장 201·Location·공백/개행 보존·DB 복원 | CodeDraftControllerTests#처음_저장하면_201과_위치를_반환하고_DB에서_복원한다 | HTTP 사전 실행: API 부재로 최초 저장 201 단언 실패 |
| 덮어쓰기 200·마지막 코드 복원 | CodeDraftControllerTests#기존_코드를_덮어쓰면_200과_마지막_코드를_반환한다 | HTTP 사전 실행: 최초 저장 단언 실패 |
| 기록 없음 빈 코드·null 시각 | CodeDraftControllerTests#저장하지_않은_코드는_빈_문자열과_null_시각으로_반환한다 | HTTP 사전 실행: 조회 200 단언 실패 |
| 다른 회원·ADMIN의 코드 격리 | CodeDraftControllerTests#다른_회원과_관리자는_본인_코드만_조회하고_수정한다 | HTTP 사전 실행: 최초 저장 단언 실패 |
| 문제별·6개 언어별 분리 | CodeDraftControllerTests#회원의_문제별_언어별_코드는_서로_분리된다 | HTTP 사전 실행: 최초 저장 단언 실패 |
| 빈 코드·공백 코드 저장 | CodeDraftControllerTests#빈_코드와_공백만_있는_코드를_그대로_저장한다 | HTTP 사전 실행: 최초 저장 단언 실패 |
| 최대 102400자·초과·null·누락·실패 시 기존 코드 유지 | CodeDraftControllerTests#최대_길이는_저장하고_초과하거나_null이면_기존_코드를_유지한다 | HTTP 사전 실행: 최초 저장 단언 실패 |
| 미인증 401 A001 | CodeDraftControllerTests#인증하지_않으면_저장과_조회에_401을_반환한다 | 기존 공통 보안 동작이라 사전 실행부터 통과, 권한 규칙 변경 없음 |
| 유효 토큰의 회원 없음 401 | CodeDraftControllerTests#유효한_토큰이어도_회원이_없으면_401을_반환한다 | HTTP 사전 실행: 회원 확인이 없어 401 단언 실패 |
| 없는 문제 C004/404 | CodeDraftControllerTests#없는_문제는_저장과_조회에서_C004를_반환한다 | 사전 실행은 공통 미등록 경로 404로 통과, 기존 오류 계약 유지. 성공 경로 레드와 함께 검증 |
| 언어·문제 ID 경계 400 C001 | CodeDraftControllerTests#잘못된_언어와_문제_ID는_400을_반환한다 | HTTP 사전 실행: 요청 검증 API가 없어 400 단언 실패 |
| 검증·JSON 파싱 오류 응답/로그 원문 비노출 | CodeDraftControllerTests#검증과_JSON_오류는_코드_원문을_응답이나_로그에_남기지_않는다 | HTTP 사전 실행: 요청 검증 API가 없어 400 단언 실패 |
| JSON 숫자·boolean·배열 입력 거부 | CodeDraftControllerTests#코드는_JSON_문자열만_허용한다 | 독립 리뷰 후 추가한 사전 테스트: 숫자 입력에서 expected 400 but was 201. 필드 전용 역직렬화로 수정 |
| 문제 삭제 시 모든 회원·언어 기록 삭제 | CodeDraftControllerTests#원본_문제를_삭제하면_저장된_코드도_삭제한다 | HTTP 사전 실행: 최초 저장 단언 실패. 삭제 연동 단독 되돌림 검증은 별도 기록 |
| 회원·문제·언어 DB 유니크 | CodeDraftRepositoryTests#같은_회원_문제_언어는_DB에서도_중복을_막는다 | 구현 전 컴파일 실패: CodeDraft·CodeDraftRepository 없음 |
| 최대 길이 유니코드·감사 시각 | CodeDraftRepositoryTests#최대_길이의_유니코드와_시각을_DB에_저장한다 | 구현 전 컴파일 실패: CodeDraft·CodeDraftRepository 없음 |
| 동시 최초 저장 하나·다음 저장 덮어쓰기 | CodeDraftConcurrencyTests#동시에_처음_저장해도_기록은_하나이고_다음_저장이_덮어쓴다 | 구현 전 컴파일 실패: CodeDraftService·DTO 없음 |
| 저장·문제 삭제 경쟁 | CodeDraftConcurrencyTests#저장과_문제_삭제가_겹쳐도_코드_기록이_남지_않는다 | 구현 전 컴파일 실패: CodeDraftService·예외 없음 |

## 검증 결과

구현 후 명령·종료 코드·결과·SHA, 미실행 항목과 독립 리뷰 결과를 기록한다.

- 사전 HTTP 테스트: 종료 1, tests=13 failures=11. 기존 인증·공통 404 계약 두 항목은 사전부터 통과했다.
- 사전 DB·동시성 테스트: 종료 1, CodeDraft·Repository·Service·DTO·예외 부재로 컴파일 실패(8 errors).
- 구현 후 `./gradlew.bat test --tests '*CodeDraft*Tests' --console=plain`: 종료 0, 새 테스트 17개 모두 통과.
- 구현 후 `./gradlew.bat build --console=plain`: 종료 0, tests=187 skipped=7 failures=0 errors=0. 새 건너뛰기 없음.
- 프론트 연동 문서: `docs/code-draft-api.md`. 프론트 구현·브라우저 자동 저장 동작은 범위 밖이므로 백엔드 검증과 구분한다.
- 삭제 연동 되돌림: 커밋 e8bc22e에서 ProblemService의 코드 정리 한 줄만 제거하고 HTTP 테스트 실행, 종료 1 (13개 중 삭제 연동 1개 실패, FK 위반). 원본 파일을 바이트 단위로 복구했고 작업 트리가 다시 깨끗한 것을 확인했다.
- 독립 리뷰: 커밋 e8bc22e를 새 맥락 리뷰어가 기준 SHA와 비교했다. 테스트 약화·범위 밖 변경은 없었다.

| 리뷰 지적 | 처리 |
| --- | --- |
| String 필드에 대한 Jackson 숫자·boolean 자동 변환 가능성 및 타입 경계 테스트 누락 | 실제 실패 테스트로 숫자 입력의 201 응답을 확인. CodeDraftCodeDeserializer로 문자열 토큰만 허용하고 숫자·boolean·배열을 C001/400으로 거부. 공통 ObjectMapper 설정은 유지 |
| 검증 결과의 SHA·실제 MySQL·CI 증거 보완 | 아래 최종 검증 기록과 최종 보고에 실행 명령·결과·커밋을 명시 |

- 리뷰 수정 후 전체 빌드: 종료 0, tests=188 skipped=7 failures=0 errors=0. 새 테스트 18개 통과.
- 독립 재검토: d7dc513의 변경을 검토했고, 문자열 외 입력 거부·원문 보존·적용 범위·테스트가 기존 지적을 해결했으며 남은 코드 지적은 없었다.

### 실제 MySQL 실행 증거

- 기존 DB 대신 이 작업 전용 `mysql:8.4` 임시 컨테이너를 localhost 임의 포트로 실행했다. `/auth/signup` → `/auth/login`으로 실제 회원·관리자 토큰을 발급받았다.
- 검증 명령: `python -u <임시 폴더>/corte51-smoke.py`, 종료 0, 실행 시점 코드 SHA `d7dc513`.
- `GET /api/code-drafts/1/JAVA` 미인증 401, 저장 전 본인 조회 200 `{code:"",updatedAt:null}`.
- 최초 PUT 201 + Location, GET 200 원문 보존, 기존 PUT 200. 다른 회원·ADMIN의 조회는 본인 기록이 없으므로 빈 코드였다.
- 102400자 한글 코드 저장·조회 일치. information_schema에서 실제 컬럼 타입 `mediumtext` 확인.
- code null·숫자·boolean·배열·길이 초과·파손 JSON은 400 C001. 없는 문제는 404 C004. 오류 응답 및 앱 로그에 검증용 코드 표식이 없었다.
- 같은 회원·문제·언어의 동시 최초 PUT 두 요청은 201/200, DB 기록 수 1. 이후 PUT이 최종 코드로 복원됐다.
- 같은 MySQL DB를 유지한 채 앱 프로세스를 종료·재시작했고 102400자 코드와 마지막 저장 코드가 그대로 복원됐다.
- 빈 코드로 갱신하면 빈 문자열·비어 있지 않은 updatedAt을 반환했다.
- DELETE /api/problems/1은 204, 이후 GET은 404, 해당 문제의 code_drafts DB 기록 수 0.
- 앱 프로세스와 작업 전용 임시 MySQL 컨테이너를 종료·제거했다. 기존 DB·컨테이너는 변경하지 않았다.

### Verification Checklist 증거

아래 실행 코드는 `d7dc513`이며, 이후 검증 문서만 추가한다. 최종 커밋에서도 빌드를 다시 확인하고 PR의 CI 결과는 최종 보고에 남긴다.

| 항목 | 실행한 명령·검토 | 종료 코드 | 결과 | 실행 시점 SHA |
| --- | --- | --- | --- | --- |
| 컨벤션 | 경로별 AGENTS와 최종 diff 검토 | 해당 없음 | DTO·트랜잭션·지연 연관·감사 시각·기존 오류 형식 준수. PUT은 합의한 예외 | d7dc513 |
| 빌드 | `./gradlew.bat build --console=plain` | 0 | tests=188 skipped=7 failures=0 errors=0 | d7dc513와 동일 실행 코드 |
| 명세-테스트 대응 | 위 대응표와 레드 로그·최종 테스트 결과 | 0/1 | 신규 동작 실패 후 통과. 기존 인증·404 계약은 원래 동작 유지 | ecdcfc9, e8bc22e, d7dc513 |
| 테스트 무결성 | `git diff <기준SHA>...HEAD -- src/test` 및 삭제/건너뛰기/단언 약화 검사 | 0 | 기존 테스트 수정은 새 의존성 mock 추가뿐. 삭제·건너뛰기·단언 약화 없음. 삭제 연동 단독 되돌림은 기대대로 실패 | d7dc513 |
| 실행 검증 | `python -u <임시 폴더>/corte51-smoke.py` | 0 | 실제 MySQL 성공·오류·앱 재시작 복원·유니코드·동시성·삭제 확인 | d7dc513 |
| 독립 리뷰 | 새 맥락 리뷰어의 커밋 diff 검토 및 수정 후 재검토 | 해당 없음 | 지적 1건 재현·수정, 남은 코드 지적 없음 | e8bc22e, d7dc513 |
| CI | develop 대상 PR 생성 후 확인 | 미실행 | push 전 기록이며, PR 생성 후 결과는 최종 보고에 기재 | 해당 없음 |
| 성능 | 유니크 키 단건 조회·DTO 변환 경로 검토 | 해당 없음 | 반복 연관 조회·목록 없음, 페이징 해당 없음. 같은 회원 및 같은 문제의 쓰기는 잠금으로 직렬화 | d7dc513 |
| 동시성 | CodeDraftConcurrencyTests 및 MySQL 동시 PUT | 0 | 회원→문제 잠금과 유니크 제약, 최초 201/200·단일 기록 확인 | d7dc513 |
| 변경 범위 | `git diff <기준SHA>...HEAD --check`, `git status --short` | 0 | 무관한 변경 없음, 검증 커밋 전 작업 트리 깨끗함. 문서 커밋 후 다시 확인 | d7dc513 |
