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

## 검증 결과

구현 후 명령·종료 코드·결과·SHA, 독립 리뷰 지적과 처리 결과를 기록한다.
