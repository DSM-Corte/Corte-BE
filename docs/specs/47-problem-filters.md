# 기능: 문제 난이도 등급과 내 해결 상태 필터

- 기준 SHA: aefc8113b6f722f46157a1f6b447931d88466045 (origin/develop)
- 프론트 기준 SHA: 8afd4bf (origin/develop), 연결 이슈 DSM-Corte/Corte-BE#47.
- 목적: 난이도와 본인의 정답 여부에 맞는 문제를 찾는다. 백엔드와 기존 문제 목록 UI를 함께 변경한다.
- API: GET /api/problems, 기존 page/size/category/tag와 선택 difficultyTier, solveStatus를 AND로 결합한다. 성공 200, 기존 페이지 응답 유지.
- difficultyTier: 생략=전체, UNRATED, BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, RUBY. 등급은 5~1을 모두 포함한다.
- solveStatus: ALL(기본), SOLVED, UNSOLVED. 정식 제출의 ACCEPTED가 한 번이라도 있으면 해결함. 미해결은 오답만 있는 문제와 미제출 문제 모두 포함한다. 다른 회원/비회원/직접 실행은 제외한다.
- 권한: 공개 난이도 조회. SOLVED/UNSOLVED는 로그인 필수, 미인증 또는 회원 없음 401 A001. 잘못된 enum/페이징 입력은 400 C001.
- UI: 등급 단일 선택, 전체/해결함/미해결. 비로그인 상태 선택 제한과 로그인 안내. 필터 변경 시 첫 페이지. 로딩/빈 결과/오류 구분, 오래된 응답 무시.
- 데이터: 기존 Problem/Difficulty/JudgeJob/User 재사용, 스키마 변경 없음. 고정 범주는 enum.
- 트랜잭션: 읽기 전용. 동시성: 쓰기가 없으므로 추가 락 없음. 목록은 DB에서 조건과 페이징 적용, 태그 일괄 조회.
- 범위 밖: 제목 검색 API, 세부 난이도 단계 선택, 점수/티어/리더보드.
- 가정: 기존 프론트의 키워드/복수 태그/정렬/문제집 기능은 유지한다. 이 조합은 기존 방식의 클라이언트 후처리를 유지하되 서버에서 난이도/해결 상태를 먼저 필터링한다.

## 테스트 대응
레드 실행: `gradlew.bat test --tests '*ProblemControllerTests'` (구현 전), exit 1, 23개 중 신규 6개 실패. 기존 테스트 17개는 통과했다.

| 명세 항목 | 테스트 (ProblemControllerTests#) | 레드 증거 |
| --- | --- | --- |
| 등급 5~1, 미지정, 생략 공개 조회 | 등급별_다섯_단계와_미지정을_공개_조회한다 | expected totalElements 5, actual 31 |
| 본인 정식 정답, 오답 후 정답, 중복·타인·비회원·직접 실행 제외 | 해결함은_본인의_정식_정답만_중복없이_포함한다 | expected totalElements 1, actual 4 |
| 미해결에 오답과 미제출 포함 | 미해결은_오답과_미제출을_포함하고_정답은_제외한다 | expected totalElements 2, actual 3 |
| AND 조건, 최신순, 페이지 크기·총 개수, 빈 결과 | 모든_필터를_결합하고_페이징과_최신순을_유지한다 | expected totalElements 2, actual 3 |
| 미인증·없는 회원 401 A001, ALL 공개 | 개인_상태_필터는_로그인과_존재하는_회원을_요구한다 | expected 401, actual 200 |
| 잘못된 등급/상태 400 C001 | 잘못된_필터값은_400을_반환한다 | expected 400, actual 200 |
| 기존 페이지·카테고리·태그 경계값 | 잘못된_페이지_카테고리_JSON_ID를_거절한다, 태그의_빈값_길이_개수_제한을_검증한다 | 기존 회귀 테스트, 기존 계약 유지 |
| UI 쿼리·서버 페이징·미해결·빈 결과·오류·태그 부분 검색 | FE tests/problem-filters.test.mjs 6개 | 구현 후 계약 및 리뷰 회귀 검증. FE 소스 되돌림 검사 exit 1, 6개 중 5개 실패 후 복원 |
| UI 페이지 초기화·로그인 제한·오래된 응답 | 실제 브라우저: 비로그인 제한, 2쪽→미지정 첫 페이지, 빠른 실버→루비 변경 확인 | 수동 실행 검증 |

## 검증 기록
- 기준 빌드: exit 0, tests=377 skipped=7 failures=0 errors=0. 기존 skipped 7개 유지.
- 구현 빌드: exit 0, tests=383 skipped=7 failures=0 errors=0. 마지막 커밋 후 재확인한다.
- MySQL 8.4: 전용 corte-filter-47-mysql, 127.0.0.1:13347, 앱 18047. 다른 작업의 DB와 분리.
- 실제 HTTP: 브론즈 total=11, size=5/page=1에서 5개·3페이지, 본인 해결=1, 미해결=10, 미지정=1. 미인증 401 A001, 잘못된 등급/상태/공백 400 C001. 각 응답 검증 스크립트 exit 0.
- 브라우저: 비로그인 개인 필터 disabled와 안내, 로그인 후 브론즈 해결=1/미해결=10, 페이지 이동, 조건 변경 첫 페이지, 빠른 변경 뒤 최종 루비 빈 결과, 라이트·다크 및 390px 필터 표시, 키보드 Tab 이동 확인.
- 독립 리뷰: 단일 태그의 부분 일치·대소문자 무시가 깨지는 지적을 수정. 서버 태그 최적화 대신 기존 후처리 유지, FE 회귀 테스트 추가 후 6개 통과. 테스트 대응표 미작성 지적은 이 표로 반영.
- 범위 밖: 390px에서 기존 헤더 링크가 세로로 줄바꿈되는 현상은 필터와 무관하여 변경하지 않음. 기존 번들 크기 경고 유지.
- 테스트 무결성: 기존 테스트 삭제/skip 추가/단언 약화 없음. FE 소스만 기준 SHA로 되돌리면 필터 테스트 5개 실패, HEAD 복원 후 깨끗한 상태 확인. BE src/main만 기준 SHA로 되돌린 검사도 exit 1, 23개 중 신규 6개 실패 후 HEAD 복원 및 깨끗한 작업 트리 확인.
- CI: push 승인 전이므로 미실행. 이슈 #47은 재개 상태로 유지.

## Checklist

| 항목 | 명령 또는 근거 | 종료 코드 | 결과 |
| --- | --- | --- | --- |
| 컨벤션 | 경로별 AGENTS.md 확인, 독립 리뷰 및 git diff --check | 0 | 새 enum/인증 예외/읽기 트랜잭션/DTO 규칙 준수 |
| 빌드 | gradlew.bat build | 0 | tests=383 skipped=7 failures=0 errors=0. 복원한 최종 HEAD에서 재확인 후 사용자 보고 |
| 명세-테스트 대응 | 위 테스트 대응표 | 해당 없음 | 동작별 HTTP+실제 DB 통합 테스트와 레드 증거, FE 계약 테스트 포함 |
| 테스트 무결성 | git diff aefc811...HEAD -- src/test, BE·FE 소스 되돌림 검사 | 0 / 예상 실패 1 | 기존 테스트 삭제·skip 추가·단언 약화 없음. 되돌림 시 BE 6개/FE 5개 실패 |
| 실행 검증 | python build/filter-smoke.py, 브라우저 | 0 | 전용 실제 MySQL 성공 5경로·오류 4경로, 브라우저 로그인 전후·페이지·테마 확인 |
| 독립 리뷰 | 새 맥락 리뷰 및 재리뷰 | 해당 없음 | 태그 검색 회귀와 문서 보완 지적 반영, 재리뷰 추가 지적 없음 |
| CI | push 전 | 미실행 | 사용자의 변경분별 push 허락 후 PR에서 확인 |
| 성능 | findFiltered projection/countQuery + findTagsByProblemIds | 해당 없음 | DB 필터/페이징, 연관 엔티티 반복 조회 없음. 프론트 기존 복합 검색의 전체 페이지 후처리는 유지 |
| 동시성 | 읽기 전용 조회 | 해당 없음 | 데이터 쓰기나 상태 전환이 없어 새 락/제약 불필요 |
| 변경 범위 | git status --short, git diff aefc811...HEAD | 0 | 필터와 관련 테스트·API 명세만 변경. 원래 티어 작업 공간 보존 |

실행 시점 SHA는 최종 사용자 보고에 남긴다. FE 최종 코드 커밋: 71858a5f4174a09aef4721537731ea3fd329abb6.
