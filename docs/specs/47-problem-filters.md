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
구현 전 실패 증거와 최종 검증은 동작별로 채운다.
