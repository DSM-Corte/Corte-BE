# 기능: 개념별 문제집과 문제 난이도

- 이슈: #42 / 브랜치: `feature/#42-workBook`
- 기준 SHA: `d0eb9e596e6117e62433ba8f6d9c5653e2a24f49`
- 목적: 누구나 알고리즘·자료구조별 문제를 찾아 쉬운 문제부터 학습한다.
- 기준 빌드: `./gradlew.bat build`, 종료 0. 기존 코드는 변경하지 않고 시작했다.

## API·권한

조회(GET·HEAD)는 공개, 쓰기는 ADMIN 전용이다.

| 기능 | API·요청 | 성공 응답 |
| --- | --- | --- |
| 생성 | POST /api/workbooks `{name}` | 201 + Location, `{id,name,problemCount}` |
| 목록 | GET /api/workbooks?page=0&size=20 | 200, `{content,page,size,totalElements,totalPages}`, id 내림차순 |
| 상세 | GET /api/workbooks/{id}?page=0&size=20 | 200, `{id,name,problemCount,problems:{content,page,size,totalElements,totalPages}}` |
| 이름 수정 | PATCH /api/workbooks/{id} `{name}` | 200, `{id,name,problemCount}` |
| 삭제 | DELETE /api/workbooks/{id} | 204 |
| 문제 추가 | POST /api/workbooks/{id}/problems `{problemId}` | 204 |
| 문제 제거 | DELETE /api/workbooks/{id}/problems/{problemId} | 204 |

- 목록 content: `{id,name,problemCount}`. 상세 problems.content: `{id,title}`. 프론트는 id로 문제에 이동한다.
- page >= 0, size 1~100. 이름은 strip 후 1~100자, 이름 중복 허용. PATCH null은 변경 없음.
- 문제 생성·수정 요청과 단건·목록 응답에 `difficulty`를 추가한다.
- 난이도 값: `UNRATED`, `BRONZE_5`~`BRONZE_1`, `SILVER_5`~`SILVER_1`, `GOLD_5`~`GOLD_1`, `PLATINUM_5`~`PLATINUM_1`, `DIAMOND_5`~`DIAMOND_1`, `RUBY_5`~`RUBY_1`.
- 기존 문제 및 생성 시 생략/null은 UNRATED. 관리자가 선택하며 PATCH null은 유지, UNRATED 명시는 미지정으로 변경한다.
- 문제집 정렬: 브론즈 5부터 루비 1까지, UNRATED 마지막, 동률은 문제 id 오름차순. 수동 정렬 없음.

## 데이터·규칙·오류

- workbooks: id, name, createdAt, updatedAt.
- workbook_problems: id, workbook FK, problem FK, createdAt, updatedAt. 두 FK 조합 유니크.
- 동일 문제집 중복 금지, 여러 문제집에 같은 문제 포함 가능, 빈 문제집 허용.
- 문제집 삭제는 연결만 함께 삭제하고 원본 문제는 유지. 원본 문제 삭제는 기존 제출 이력 보호 규칙을 유지하며 연결을 먼저 삭제한다.
- problems.difficulty: 문자열 enum, DB 기본값 UNRATED로 기존 행을 보존하며 채운다. MySQL에서 기존 데이터에 대한 컬럼 추가를 검증한다.
- W001 404 문제집 없음 / W002 409 중복 문제 / W003 404 포함되지 않은 문제.
- 원본 문제 없음: 기존 C004 404. 잘못된 이름·난이도·페이지·문제 ID: C001 400. 인증·권한은 기존 401/403 형식.

## 트랜잭션·동시성·성능

- 쓰기는 트랜잭션. 문제 연결 쓰기는 문제 → 문제집 순서로 비관적 잠금, 문제집 변경·삭제는 문제집 잠금.
- FK 조합 유니크 및 saveAndFlush. 예상한 유니크 제약만 W002로 변환한다.
- 목록과 상세 문제 목록은 projection + 페이징. 목록 문제 수는 ID 일괄 COUNT, 개수 컬럼 없음.
- 기존 원본 문제 삭제의 문제 행 잠금과 연동하여 삭제와 추가의 경쟁을 보호한다.

## 범위·컨벤션

- 백엔드 API만 구현. 화면, 자동 추천·추가, 진도, 복사, 검색은 범위 밖.
- 새 오류 접두사 W. 문제집 문제 목록만 일반 id 내림차순 대신 위 난이도 정렬을 사용한다.
- 문제 연결 POST는 합의한 204 응답을 사용한다.

## 테스트 대응

구현 전에 동작별 테스트를 실행하고 아래에 테스트 이름과 실제 레드 증거를 기록한다.

| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 빈 문제집 생성·공개 GET/HEAD | WorkbookControllerTests#관리자가_빈_문제집을_생성하고_누구나_조회한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 목록 최신순·페이징·개수 | WorkbookControllerTests#목록은_최신순으로_페이징하고_문제수를_반환한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 이름 수정·null 유지·삭제 시 원본 보존 | WorkbookControllerTests#관리자가_이름을_수정하고_문제집을_삭제한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 중복 금지·여러 문제집 허용·제거·W002/W003 | WorkbookControllerTests#같은_문제집_중복은_거부하고_다른_문제집에는_추가한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 원본 삭제 연동 | WorkbookControllerTests#원본_문제를_삭제하면_모든_문제집에서_빠진다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 난이도 정렬·동률 ID·미지정 마지막·상세 페이징 | WorkbookControllerTests#난이도_오름차순과_동률_ID순으로_정렬하고_미지정은_마지막이다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음). 구현 후 wildcard JSONPath가 올바른 응답에서 null을 반환해 동일한 전체 순서를 인덱스별로 검증하도록 수정 |
| 기본 미지정·선택·null 유지·초기화·잘못된 enum·문제 목록 반영 | WorkbookControllerTests#기본_난이도는_미지정이고_관리자가_선택하거나_초기화한다 | 구현 전 실행: $.difficulty 없음(PathNotFoundException) |
| 모든 쓰기 경로 401/403 | WorkbookControllerTests#모든_쓰기_경로는_관리자만_접근한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 이름·페이지·문제 ID 경계·W001/C004/C001 | WorkbookControllerTests#잘못된_입력과_없는_자원은_계약한_오류를_반환한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 모든 31등급의 DB 정렬 | WorkbookProblemRepositoryTests#모든_등급과_단계를_쉬운순으로_정렬한다 | 추가 보강 테스트, 되돌림 검사 미실행 |
| DB 유니크 제약 | WorkbookProblemRepositoryTests#같은_문제집과_문제_조합은_DB에서도_중복을_막는다 | 추가 보강 테스트, 되돌림 검사 미실행 |
| DB 기본값 | WorkbookProblemRepositoryTests#DB에서_난이도를_생략해도_미지정으로_저장한다 | 추가 보강 테스트, 되돌림 검사 미실행 |
| 동시 중복 요청 | WorkbookConcurrencyTests#동시에_같은_문제를_추가하면_하나만_성공한다 | 추가 보강 테스트, 되돌림 검사 미실행 |

## 검증 결과

- 사용자가 미실행 검증에 대한 중단 보고 후 현재 작업 브랜치의 push를 명시적으로 요청했다. 해당 요청에 따라 커밋·빌드 확인 후 push한다. MySQL 검증·되돌림 검사·독립 리뷰·CI의 미실행 상태는 유지하며, 기능 전체 검증 완료로 간주하지 않는다.

구현 후 명령·종료 코드·결과·SHA, 독립 리뷰 지적과 처리 결과를 기록한다.

- 최초 레드: `./gradlew.bat test --tests '*WorkbookControllerTests' --console=plain`, 종료 1, tests=9 failures=9.
- MySQL 검증 차단: `docker info --format '{{.ServerVersion}}'` 종료 1. `dockerDesktopLinuxEngine` 명명된 파이프가 없어 엔진에 연결할 수 없다.
- Docker 환경 문제로 DB 실행 검증·기존 데이터 컬럼 추가 검증·독립 리뷰·push·PR·CI는 아직 진행하지 않았다. 완료 상태가 아니다.

### 중간 검증 기록 (환경 문제로 중단)

아래 빌드는 커밋 전 작업 트리의 결과다. 최종 커밋의 검증을 대신하지 않는다.

| 항목 | 실행한 명령 | 종료 코드 | 결과 | 실행 시점 SHA |
| --- | --- | --- | --- | --- |
| 빌드 | `./gradlew.bat build --console=plain` | 0 | tests=183 skipped=7 failures=0 errors=0. 최종 커밋 검증은 미실행 | `42e57c7` + 작업 트리 |
| 공백 검사 | `git diff --check` | 0 | 오류 없음 | `42e57c7` + 작업 트리 |
| 실행 검증 | `docker info --format '{{.ServerVersion}}'` | 1 | Docker 엔진 미실행. MySQL API 및 기존 데이터 보존 검증 미실행 | `42e57c7` + 작업 트리 |
| 컨벤션 | 미실행 | — | 최종 검토 미실행 | — |
| 명세-테스트 대응 | 최초 HTTP 레드 9개 + 전체 빌드 | 0/1 | 보강 테스트의 되돌림 레드 증거 미실행 | `42e57c7` + 작업 트리 |
| 테스트 무결성 | 미실행 | — | 최종 diff·되돌림 검사 미실행. wildcard 검증 변경 사유는 위 표에 기록 | — |
| 독립 리뷰 | 미실행 | — | 환경 문제에서 중단 | — |
| CI | 미실행 | — | PR 생성 전 | — |
| 성능 | 미실행 | — | projection·페이징·일괄 COUNT 구현, 최종 검토 미실행 | — |
| 동시성 | 전체 빌드의 WorkbookConcurrencyTests | 0 | H2 동시 추가 검증 통과, MySQL 검증 미실행 | `42e57c7` + 작업 트리 |
| 변경 범위 | `git status --short` | 0 | 구현은 아직 커밋 전. 깨끗한 작업 트리 조건 미충족 | `42e57c7` + 작업 트리 |
