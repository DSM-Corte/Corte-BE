# 기능: 개념별 문제집

- 이슈: #42 / 브랜치: `feature/#42-workbook-api`
- 선행 이슈: #47 문제 난이도 / 기준 SHA: `d74eda7` (`feature/#47-problem-difficulty`)
- 기존 통합 PR #46을 난이도 PR과 문제집 PR로 분리한다. 문제집 PR은 난이도 브랜치를 base로 하며, 난이도 병합 후 develop으로 바꾼다.
- 목적: 누구나 알고리즘·자료구조별 문제를 찾아 쉬운 문제부터 학습한다.

## API·권한

조회 GET·HEAD는 공개, 쓰기는 ADMIN 전용이다.

| 기능 | API·요청 | 성공 응답 |
| --- | --- | --- |
| 생성 | POST /api/workbooks `{name}` | 201 + Location, `{id,name,problemCount}` |
| 목록 | GET /api/workbooks?page=0&size=20 | 200, `{content,page,size,totalElements,totalPages}`, id 내림차순 |
| 상세 | GET /api/workbooks/{id}?page=0&size=20 | 200, `{id,name,problemCount,problems:{content,page,size,totalElements,totalPages}}` |
| 이름 수정 | PATCH /api/workbooks/{id} `{name}` | 200, `{id,name,problemCount}` |
| 삭제 | DELETE /api/workbooks/{id} | 204 |
| 문제 추가 | POST /api/workbooks/{id}/problems `{problemId}` | 204 |
| 문제 제거 | DELETE /api/workbooks/{id}/problems/{problemId} | 204 |


- 목록 content는 `{id,name,problemCount}`, 상세 problems.content는 `{id,title}`. 프론트가 id로 문제에 이동한다.
- page 기본 0, size 기본 20·범위 1~100. 목록은 id 내림차순, 상세 문제는 난이도 오름차순·미지정 마지막·동률 문제 id 오름차순이다.
- 이름은 strip 후 1~100자. 이름 중복 및 빈 문제집 허용. PATCH null은 유지한다.
- 난이도 값·기본값·관리자 선택·기존 문제 API 변경은 #47 명세를 따른다. 이 브랜치의 변경 범위에 포함하지 않는다.

## 데이터·규칙·오류

- workbooks: id, name, createdAt, updatedAt.
- workbook_problems: id, workbook FK, problem FK, createdAt, updatedAt. FK 조합 유니크.
- 같은 문제집에 동일 문제 중복 금지. 여러 문제집에는 같은 문제를 담을 수 있다.
- 문제집 삭제 시 연결만 지우며 원본 문제를 유지한다. 원본 문제 삭제 시 모든 연결을 제거하고 기존 제출 이력 보호 규칙을 유지한다.
- W001/404 문제집 없음, W002/409 중복 문제, W003/404 포함되지 않은 문제.
- 원본 문제 없음은 기존 C004/404, 잘못된 이름·페이지·문제 ID는 C001/400. 인증·권한은 기존 401/403 형식.

## 트랜잭션·동시성·성능

- 쓰기는 트랜잭션. 문제 연결 변경은 문제 → 문제집 순서로 비관적 잠금, 문제집 수정·삭제는 문제집 잠금.
- 중복은 FK 조합 유니크와 saveAndFlush로 보호하며, 예상한 유니크 위반만 W002로 변환한다.
- 목록·상세 문제 목록은 projection + 페이징, 목록 문제 수는 ID 일괄 COUNT. 개수 컬럼 없음.
- 기존 문제 삭제의 잠금과 연동하여 원본 삭제와 연결 추가의 경쟁을 보호한다.

## 범위·컨벤션

- 백엔드 API만 구현. 화면·자동 추천·진도·복사·검색은 범위 밖.
- 오류 접두사 W. 문제집 문제 목록은 일반 id 내림차순 대신 난이도순, 문제 연결 POST는 합의한 204를 사용한다.

## 테스트 대응

기존 통합 구현의 레드 증거를 유지한다. 난이도 API·DB 기본값 테스트는 #47의 문제 도메인 테스트로 이동한다.

| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 빈 문제집 생성·공개 GET/HEAD | WorkbookControllerTests#관리자가_빈_문제집을_생성하고_누구나_조회한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 목록 최신순·페이징·개수 | WorkbookControllerTests#목록은_최신순으로_페이징하고_문제수를_반환한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 이름 수정·null 유지·삭제 시 원본 보존 | WorkbookControllerTests#관리자가_이름을_수정하고_문제집을_삭제한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 중복 금지·여러 문제집 허용·제거·W002/W003 | WorkbookControllerTests#같은_문제집_중복은_거부하고_다른_문제집에는_추가한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 원본 삭제 연동 | WorkbookControllerTests#원본_문제를_삭제하면_모든_문제집에서_빠진다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 난이도 정렬·동률 ID·미지정 마지막·상세 페이징 | WorkbookControllerTests#난이도_오름차순과_동률_ID순으로_정렬하고_미지정은_마지막이다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음). 구현 후 wildcard JSONPath가 올바른 응답에서 null을 반환해 동일한 전체 순서를 인덱스별로 검증하도록 수정 |
| 모든 쓰기 경로 401/403 | WorkbookControllerTests#모든_쓰기_경로는_관리자만_접근한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 이름·페이지·문제 ID 경계·W001/C004/C001 | WorkbookControllerTests#잘못된_입력과_없는_자원은_계약한_오류를_반환한다 | 구현 전 실행: createWorkbook의 201 단언 실패(API 없음) |
| 모든 31등급의 DB 정렬 | WorkbookProblemRepositoryTests#모든_등급과_단계를_쉬운순으로_정렬한다 | 추가 보강 테스트, 되돌림 검사 미실행 |
| DB 유니크 제약 | WorkbookProblemRepositoryTests#같은_문제집과_문제_조합은_DB에서도_중복을_막는다 | 추가 보강 테스트, 되돌림 검사 미실행 |
| 동시 중복 요청 | WorkbookConcurrencyTests#동시에_같은_문제를_추가하면_하나만_성공한다 | 추가 보강 테스트, 되돌림 검사 미실행 |


## 검증·한계

- 기존 통합 구현은 전체 빌드·CI가 통과했다. 분리된 문제집 브랜치는 별도로 빌드를 확인한다.
- MySQL 실행 및 기존 데이터 검증, 되돌림 검사, 독립 리뷰는 미실행이다. 사용자의 기존 push·PR 요청과 이번 분리 요청에 따라 원격 이슈·브랜치·PR도 분리한다.
- 난이도 브랜치를 base로 한 문제집 PR은 현재 CI의 develop/main 대상 필터에 해당하지 않는다. 난이도 병합 후 base를 develop으로 바꾸어 CI를 확인해야 한다.
- 난이도 기본값·API 테스트 두 개를 #47로 이동했으며, 문제집 관련 단언은 유지한다.
- 분리 후 `./gradlew.bat build --console=plain`: 종료 0, tests=183 skipped=7 failures=0 errors=0. `git diff --cached --check`: 종료 0.
- `git diff cf7b5f7 --quiet -- src/main`: 종료 0. 원본 통합 구현과 최종 운영 코드가 동일하다.
- 난이도 선행 PR은 #49이며, develop 대상 CI가 통과했다.
