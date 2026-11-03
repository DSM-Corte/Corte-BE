# 잔디 조회

- 이슈: #44
- 기준 SHA: d0eb9e596e6117e62433ba8f6d9c5653e2a24f49

## 기능: 내 잔디 조회
- 목적: 회원이 최근 1년 동안 매일 꾸준히 문제를 풀었는지 한눈에 확인한다
- API: `GET /api/users/me/grass`, 로그인 필요(토큰 없으면 401 A001)
  - 성공 200: `[{"date":"2026-11-02","count":2},{"date":"2026-11-03","count":1}]`
  - 1문제 이상 푼 날만 날짜 오름차순으로 준다. 푼 날이 없으면 `[]`
- 데이터: 새 테이블·컬럼 없음. 기존 `judge_jobs`를 읽기만 한다. 스키마 영향 없음
- 규칙
  - 셀 대상: 내 제출(`submission = true`) 중 `verdict = ACCEPTED`
  - count: 그날 맞힌 서로 다른 문제 수. 같은 날 같은 문제를 여러 번 맞혀도 1, 다른 날 다시 맞히면 그날도 1
  - 날짜: 제출 시각(`createdAt`, 서버 시간대)을 한국 시간(`Asia/Seoul`)으로 바꾼 날짜
  - 기간: 오늘(한국 시간) 포함 최근 365일
- 오류: 새 오류 없음
- 트랜잭션: 읽기 전용 / 동시성: 해당 없음(읽기만)
- 성능: 문제 ID와 제출 시각만 interface projection으로 한 번에 조회. 페이징 대신 1년 기간으로 제한(응답 최대 365개)
- 범위 밖: 다른 회원 잔디 조회, 연속 일수, 0인 날 채우기, 서버 시간대 변경

## 테스트 대응
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 셀 대상(내 제출·ACCEPTED만) | `JudgeJobRepositoryTests.내_정답_제출만_조회한다` | `findSolvedSubmissions` 없음 컴파일 실패 |
| 기간 시작 시각 이전 제외 | `JudgeJobRepositoryTests.from_이전에_낸_제출은_조회하지_않는다` | `findSolvedSubmissions` 없음 컴파일 실패 |
| count(같은 날 같은 문제 1번, 다른 날 다시 셈), 날짜 오름차순 | `GrassServiceTests.날짜별로_맞힌_서로_다른_문제_수를_센다` | `GrassService`·`GrassResponse` 없음 컴파일 실패 |
| 날짜(한국 시간 자정 기준) | `GrassServiceTests.날짜는_한국_시간_자정을_기준으로_나눈다` | 위와 같음 |
| 기간(오늘 포함 최근 365일) | `GrassServiceTests.오늘_포함_최근_365일의_첫날_0시부터_조회한다` | 위와 같음 |
| 푼 날이 없으면 `[]` | `GrassServiceTests.푼_문제가_없으면_빈_목록을_준다` | 위와 같음 |
