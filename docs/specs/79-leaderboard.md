# 리더보드 조회

- 이슈: #79
- 기준 SHA: 455f49008e41b9d784b3a6cb392a1e020d04b19d
- 변경: develop에 합쳐진 #74(문제 풀이 점수와 회원 티어)와 같은 점수·티어를 쓰도록 바꿨다. 처음 명세의 rank.md XP·31단계 티어는 쓰지 않는다

## 기능: 리더보드 조회
- 목적: 코딩테스트를 준비하는 사용자가 실제 정답 기록으로 다른 사람과 순위를 비교해 꾸준히 풀 동기를 얻는다.
  지금 프론트 리더보드는 하드코딩된 가짜 데이터라 이를 서버 데이터로 바꾼다
- API: `GET /api/leaderboard`, 누구나 조회(공개 경로). 토큰이 있으면 내 항목에 `me: true`
  - 쿼리
    - `page`: 0부터, 기본 0 / `size`: 1~100, 기본 20
    - `keyword`: 닉네임 부분 일치, 대소문자 무시. 비거나 공백뿐이면 조건 없음
    - `minTier`, `maxTier`: #74의 `UserTier` 이름(`BRONZE_3`~`DIAMOND_1`). 없으면 조건 없음
    - `minSolved`, `maxSolved`: 0 이상 정수. 없으면 조건 없음
    - `sort`: `RANK`·`SCORE`·`SOLVED`·`ACCURACY`·`STREAK`, 기본 `RANK`
    - `direction`: `ASC`·`DESC`. 없으면 `RANK`는 `ASC`, 나머지는 `DESC`
  - 성공 200:
    ```json
    {
      "content": [
        {"rank": 1, "userId": 3, "nickname": "민수", "tier": "SILVER_3", "score": 120,
         "solvedCount": 41, "submissionCount": 70, "accuracy": 58.6, "streak": 4, "me": false}
      ],
      "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
    }
    ```
- 데이터: 새 테이블·컬럼 없음. `judge_jobs`, `problems`, `users`, `problem_rewards`(#74)를 읽기만 한다. 스키마 영향 없음
  - 티어는 #74의 `UserTier`(15단계)와 `UserTier.fromScore`를 그대로 쓴다
- 규칙
  - 집계 대상 제출: 회원 제출(`submission = true`) 중 판정이 채점 완료 7종
    (`ACCEPTED`, `WRONG_ANSWER`, `COMPILE_ERROR`, `RUNTIME_ERROR`, `TIME_LIMIT`, `MEMORY_LIMIT`, `OUTPUT_LIMIT`). 내 통계(#57)와 같다
  - 순위표 대상: 정답 문제가 1개 이상인 회원
  - score: `GET /api/users/me/tier`의 score와 같은 값이다
    - 그 회원의 `problem_rewards` 기록 points 합계
    - 더하기, 정답 제출은 있지만 아직 지급 기록이 없는 문제마다 현재 난이도 점수(#74: 브론즈 10, 실버 20, 골드 30,
      플래티넘 40, 다이아 50, 루비 60, 미지정 0). #74는 이런 문제를 첫 티어 조회 때 같은 값으로 기록한다
    - 리더보드 조회는 기록을 새로 쓰지 않는다(읽기 전용)
    - 0점 기록이 있는 문제는 지금 난이도가 매겨져 있어도 0점이다(#74와 같다)
    - 채점된 제출이 하나도 없는 회원의 지급 기록은 순위표 대상이 아니라 무시한다. 지금은 이런 상태를 만드는 경로가 없다
  - 티어: `UserTier.fromScore(score)`
  - solvedCount: 정답 제출이 있는 서로 다른 문제 수
  - submissionCount: 집계 대상 제출 수
  - accuracy: 정답 제출 수 ÷ submissionCount × 100, 소수 첫째 자리 반올림(내 통계와 같은 계산)
  - streak: 잔디(#44)와 같은 기준으로 정답 제출이 있는 날(제출 시각을 한국 시간 날짜로 바꾼 날)이
    끊기지 않고 이어진 일수. 마지막 날이 오늘이나 어제(한국 시간)일 때만 세고, 그보다 전이면 0
  - 순위: score 내림차순 → solvedCount 내림차순 → 회원 ID 오름차순으로 1부터. 공동 순위 없음
  - 처리 순서: 전체 순위를 매긴 뒤 검색·필터 → 정렬 → 페이지. 필터를 걸어도 순위 번호는 전체 기준을 유지한다
  - 정렬 값이 같으면 순위 오름차순으로 둔다. `RANK` 정렬은 순위 그대로(방향만 적용)
  - 범위 필터(`minTier`~`maxTier`, `minSolved`~`maxSolved`)는 양 끝을 포함한다
- 오류
  - `page < 0`, `size`가 1~100 밖 → C001 / 400
  - `minSolved`·`maxSolved`가 음수, `minSolved > maxSolved`, `minTier`가 `maxTier`보다 높음 → C001 / 400
  - 없는 티어·정렬·방향 이름, 정수가 아닌 숫자 값 → C001 / 400
  - 새 오류 코드 없음
- 트랜잭션: 읽기 전용 / 동시성: 해당 없음(읽기만)
- 성능: 조회할 때마다 계산한다. 제출(회원 ID·닉네임·문제 ID·난이도·판정·제출 시각)과 지급 기록(회원 ID·문제 ID·points)을
  interface projection으로 각각 한 번에 조회하고 집계·필터·정렬·페이지는 Java에서 한다. 반복 조회 없음
- 가정: 난이도별 점수는 #74의 `UserTierService.points`를 공개(static)로 바꿔 그대로 쓴다. 팀원 코드 위치를 옮기지 않으려고 고른 가장 작은 변경이다
- 가정: 관리자 여부는 DB에 없어(`ADMIN_EMAILS`로만 판단) 관리자도 일반 회원처럼 순위에 들어간다
- 범위 밖: 난이도 투표·리뷰 채택·문제 제작 점수, rank.md XP 체계, 다른 사용자 프로필 API, 프론트 연결
- 새 컨벤션(`src/main/AGENTS.md`에 반영)
  - 경로는 하나뿐인 순위표라 단수 `/api/leaderboard`를 쓴다(복수형 규칙의 예외)
  - 목록 정렬은 `id` 내림차순이 아니라 위 순위 규칙을 따르고, 목록 항목 record 이름은 `Summary` 대신 `Entry`다

## 테스트 대응
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 집계 대상 제출(회원 제출, 주어진 판정만, 비회원·직접 실행 제외) | `JudgeJobRepositoryTests#리더보드용_조회는_모든_회원의_제출_중_주어진_판정만_회원과_문제_정보와_함께_가져온다` | 컴파일 실패: `findLeaderboardRows`·`LeaderboardRow` 없음 |
| 채점 완료 7종 판정으로 조회, 결과 없으면 빈 페이지(totalPages 0) | `LeaderboardServiceTests#채점이_끝난_7가지_판정만_조회한다` | 컴파일 실패: `LeaderboardService` 없음 |
| score(지급 기록 합계 + 기록 없는 정답 문제의 현재 난이도 점수, 같은 문제 한 번, 오답 제외, 제출 행 없는 기록 포함), solvedCount, 티어(`UserTier.fromScore`) | `LeaderboardServiceTests#점수는_지급_기록_합계에_기록이_없는_정답_문제의_현재_난이도_점수를_더한다` | #74 기준으로 바꿀 때 컴파일 실패: `findAllRewardRows`·`RewardRow`·`score` 없음 |
| 0점 기록은 지금 난이도와 상관없이 0점 | `LeaderboardServiceTests#영점_지급_기록이_있으면_지금_난이도가_매겨져_있어도_영점이다` | 구현 뒤 리뷰 지적으로 추가. 현재 구현에서 바로 통과(레드 없음) |
| 채점된 제출이 없는 회원의 지급 기록 무시 | `LeaderboardServiceTests#채점된_제출이_없는_회원의_지급_기록은_무시한다` | 구현 뒤 리뷰 지적으로 추가. 현재 구현에서 바로 통과(레드 없음) |
| 리더보드 score = 회원 티어 조회 score(지급 기록 반영 전후) | `LeaderboardControllerTests#리더보드_점수는_회원_티어_조회_점수와_같다` | 구현 뒤 리뷰 지적으로 추가. 현재 구현에서 바로 통과(레드 없음) |
| 다른 회원의 지급 기록을 섞지 않음 | `LeaderboardServiceTests#다른_회원의_지급_기록은_섞지_않는다` | 위와 같음 |
| 지급 기록 조회(모든 회원, 회원·문제·점수) | `ProblemRewardRepositoryTests#리더보드용_조회는_모든_회원의_지급_기록을_회원_문제_점수만_가져온다` | 위와 같음 |
| 난이도별 점수 표, 티어 경계 | #74의 `UserTierServiceTests`, `UserTierTests`가 검증한다(같은 `UserTierService.points`·`UserTier.fromScore`를 호출) | 해당 없음: 기존 테스트 |
| 순위표 대상(정답 1개 이상) | `LeaderboardServiceTests#정답_문제가_없는_회원은_순위표에_넣지_않는다` | 컴파일 실패: `LeaderboardService` 없음 |
| submissionCount, accuracy | `LeaderboardServiceTests#제출_수와_정답률은_채점이_끝난_제출_기준으로_센다` | 위와 같음 |
| streak(오늘까지 이어진 정답 날, 오답만 낸 날 제외) | `LeaderboardServiceTests#연속_풀이는_오늘까지_이어진_정답_날짜_수다` | 위와 같음 |
| streak(어제에서 끝나도 셈) | `LeaderboardServiceTests#오늘_정답이_없어도_어제까지_이어졌으면_센다` | 위와 같음 |
| streak(그제 이전에 끝나면 0) | `LeaderboardServiceTests#마지막_정답이_그제_이전이면_연속_풀이는_0이다` | 위와 같음 |
| streak 날짜(한국 시간 자정 기준) | `LeaderboardServiceTests#연속_풀이_날짜는_한국_시간_자정을_기준으로_나눈다` | 위와 같음 |
| 순위(점수 → 정답 수 → 회원 ID, 공동 순위 없음) | `LeaderboardServiceTests#순위는_점수_정답_수_회원_ID_순으로_매긴다` | 위와 같음 |
| 검색(닉네임 부분 일치·대소문자 무시·공백 제거), 필터 후 순위 유지 | `LeaderboardServiceTests#검색과_필터를_걸어도_순위_번호는_전체_기준을_유지한다` | 위와 같음 |
| 티어·정답 수 범위(양 끝 포함) | `LeaderboardServiceTests#티어와_정답_수_범위는_양_끝을_포함한다` | 위와 같음 |
| 정렬 기본 방향(RANK 오름, 나머지 내림), 방향 지정 | `LeaderboardServiceTests#정렬_방향이_없으면_순위는_오름차순_나머지는_내림차순이다` | 위와 같음 |
| 정렬 값이 같으면 순위 오름차순 | `LeaderboardServiceTests#정렬_값이_같으면_순위_오름차순으로_둔다` | 위와 같음 |
| 페이지 자르기, page·size·totalElements·totalPages | `LeaderboardServiceTests#페이지를_잘라서_돌려준다` | 위와 같음 |
| 로그인 회원 항목에만 `me: true` | `LeaderboardServiceTests#로그인한_회원의_항목에만_me를_표시한다`, `LeaderboardControllerTests#로그인하면_내_항목에_me를_표시한다` | 컴파일 실패 / `expected 200 but was 404`(엔드포인트 없음) |
| 오류: page 음수, size 1~100 밖 | `LeaderboardServiceTests#페이지_범위를_벗어나면_조회에_실패한다`, `LeaderboardControllerTests#페이지_크기가_범위를_벗어나면_400_C001을_반환한다` | 컴파일 실패 / `expected 400 but was 401`(공개 경로 아님) |
| 오류: 정답 수 음수, min > max | `LeaderboardServiceTests#정답_수_범위가_잘못되면_조회에_실패한다` | 컴파일 실패: `LeaderboardService` 없음 |
| 오류: minTier가 maxTier보다 높음 | `LeaderboardServiceTests#최소_티어가_최대_티어보다_높으면_조회에_실패한다` | 위와 같음 |
| 오류: 없는 정렬·티어 이름, 정수가 아닌 숫자 | `LeaderboardControllerTests#없는_정렬_이름이면_400_C001을_반환한다`, `#없는_티어_이름이면_400_C001을_반환한다`, `#정수가_아닌_정답_수면_400_C001을_반환한다` | `expected 400 but was 401`(공개 경로·엔드포인트 없음) |
| API 200 응답 필드, 토큰 없이 공개 조회 | `LeaderboardControllerTests#토큰_없이도_리더보드를_조회한다` | `expected 200 but was 401` |
| 쿼리 파라미터 바인딩(keyword, tier, solved, sort, direction, page, size) | `LeaderboardControllerTests#검색_필터_정렬_페이지_파라미터를_받는다` | `expected 200 but was 401`(첫 작성 때). 리뷰 지적으로 회원 2명 데이터로 보강 |
| 정렬 기준마다 그 값으로 정렬(SCORE·SOLVED·ACCURACY·STREAK) | `LeaderboardServiceTests#정렬_기준마다_그_값으로_정렬한다` | 구현 뒤 리뷰 지적으로 추가해 구현 전 레드는 없다. 변형 검사: `sortValue`의 SOLVED·STREAK를 `rank()`로 바꾸면 `expected [1, 2, 3] but was [3, 1, 2]`로 실패 |
| 오류: 없는 방향 이름 | `LeaderboardControllerTests#없는_정렬_방향_이름이면_400_C001을_반환한다` | 구현 뒤 리뷰 지적으로 추가. 공개 경로 설정 전이면 401이므로 같은 레드(`expected 400 but was 401`)에 해당 |
| 트랜잭션·동시성 | 해당 없음: 읽기 전용 조회만 한다 | 해당 없음 |
