# 리더보드 조회

- 이슈: #79
- 기준 SHA: 455f49008e41b9d784b3a6cb392a1e020d04b19d

## 기능: 리더보드 조회
- 목적: 코딩테스트를 준비하는 사용자가 실제 정답 기록으로 다른 사람과 순위를 비교해 꾸준히 풀 동기를 얻는다.
  지금 프론트 리더보드는 하드코딩된 가짜 데이터라 이를 서버 데이터로 바꾼다
- API: `GET /api/leaderboard`, 누구나 조회(공개 경로). 토큰이 있으면 내 항목에 `me: true`
  - 쿼리
    - `page`: 0부터, 기본 0 / `size`: 1~100, 기본 20
    - `keyword`: 닉네임 부분 일치, 대소문자 무시. 비거나 공백뿐이면 조건 없음
    - `minTier`, `maxTier`: `UserTier` 이름(예: `GOLD_5`). 없으면 조건 없음
    - `minSolved`, `maxSolved`: 0 이상 정수. 없으면 조건 없음
    - `sort`: `RANK`·`XP`·`SOLVED`·`ACCURACY`·`STREAK`, 기본 `RANK`
    - `direction`: `ASC`·`DESC`. 없으면 `RANK`는 `ASC`, 나머지는 `DESC`
  - 성공 200:
    ```json
    {
      "content": [
        {"rank": 1, "userId": 3, "nickname": "민수", "tier": "SILVER_5", "xp": 560,
         "solvedCount": 41, "submissionCount": 70, "accuracy": 58.6, "streak": 4, "me": false}
      ],
      "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
    }
    ```
- 데이터: 새 테이블·컬럼 없음. `judge_jobs`, `problems`, `users`를 읽기만 한다. 스키마 영향 없음
  - 사용자 티어 enum `UserTier`: rank.md 31단계(`BRONZE_5`~`BRONZE_1`, `SILVER_*`, `GOLD_*`, `PLATINUM_*`,
    `EMERALD_*`, `DIAMOND_*`, `MASTER`)와 단계별 필요 누적 XP
- 규칙
  - 집계 대상 제출: 회원 제출(`submission = true`) 중 판정이 채점 완료 7종
    (`ACCEPTED`, `WRONG_ANSWER`, `COMPILE_ERROR`, `RUNTIME_ERROR`, `TIME_LIMIT`, `MEMORY_LIMIT`, `OUTPUT_LIMIT`). 내 통계(#57)와 같다
  - 순위표 대상: 정답 문제가 1개 이상인 회원
  - XP: 정답 제출이 있는 서로 다른 문제마다 그 문제의 현재 난이도 XP를 한 번씩 더한다
    - Bronze 5~1: 1, 2, 3, 4, 5 / Silver 5~1: 7, 8, 10, 12, 14 / Gold 5~1: 18, 22, 26, 31, 36
    - Platinum 5~1: 45, 55, 67, 80, 95 / Diamond 5~1: 310, 380, 470, 580, 720
    - Ruby 5~1: 모두 900(rank.md의 Master 값) / Unrated: 0
  - 티어: 누적 XP 이상인 가장 높은 단계. 기준 XP는 rank.md 3절(Bronze 5 0 ~ Master 160000)
  - solvedCount: 정답 제출이 있는 서로 다른 문제 수
  - submissionCount: 집계 대상 제출 수
  - accuracy: 정답 제출 수 ÷ submissionCount × 100, 소수 첫째 자리 반올림(내 통계와 같은 계산)
  - streak: 잔디(#44)와 같은 기준으로 정답 제출이 있는 날(제출 시각을 한국 시간 날짜로 바꾼 날)이
    끊기지 않고 이어진 일수. 마지막 날이 오늘이나 어제(한국 시간)일 때만 세고, 그보다 전이면 0
  - 순위: XP 내림차순 → solvedCount 내림차순 → 회원 ID 오름차순으로 1부터. 공동 순위 없음
  - 처리 순서: 전체 순위를 매긴 뒤 검색·필터 → 정렬 → 페이지. 필터를 걸어도 순위 번호는 전체 기준을 유지한다
  - 정렬 값이 같으면 순위 오름차순으로 둔다. `RANK` 정렬은 순위 그대로(방향만 적용)
  - 범위 필터(`minTier`~`maxTier`, `minSolved`~`maxSolved`)는 양 끝을 포함한다
- 오류
  - `page < 0`, `size`가 1~100 밖 → C001 / 400
  - `minSolved`·`maxSolved`가 음수, `minSolved > maxSolved`, `minTier`가 `maxTier`보다 높음 → C001 / 400
  - 없는 티어·정렬·방향 이름, 정수가 아닌 숫자 값 → C001 / 400
  - 새 오류 코드 없음
- 트랜잭션: 읽기 전용 / 동시성: 해당 없음(읽기만)
- 성능: 조회할 때마다 계산한다. 회원 ID·닉네임·문제 ID·난이도·판정·제출 시각만 interface projection으로
  한 번에 조회하고 집계·필터·정렬·페이지는 Java에서 한다. 반복 조회 없음
- 가정: 관리자 여부는 DB에 없어(`ADMIN_EMAILS`로만 판단) 관리자도 일반 회원처럼 순위에 들어간다
- 범위 밖: 난이도 투표·리뷰 채택·문제 제작 XP, XP 저장 컬럼, 다른 사용자 프로필 API, 프론트 연결,
  문제 난이도 enum을 rank.md(Emerald·Master)에 맞추는 일
- 새 컨벤션: 없음

## 테스트 대응
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 집계 대상 제출(회원 제출, 주어진 판정만, 비회원·직접 실행 제외) | `JudgeJobRepositoryTests#리더보드용_조회는_모든_회원의_제출_중_주어진_판정만_회원과_문제_정보와_함께_가져온다` | 컴파일 실패: `findLeaderboardRows`·`LeaderboardRow` 없음 |
| 채점 완료 7종 판정으로 조회, 결과 없으면 빈 페이지(totalPages 0) | `LeaderboardServiceTests#채점이_끝난_7가지_판정만_조회한다` | 컴파일 실패: `LeaderboardService` 없음 |
| XP(서로 다른 정답 문제마다 한 번, 오답 제외, Ruby 900, Unrated 0), solvedCount | `LeaderboardServiceTests#XP는_서로_다른_정답_문제마다_난이도_XP를_한_번씩_더한다` | 위와 같음 |
| 난이도별 XP 표 | `LeaderboardServiceTests#난이도별_XP는_rank_md_표를_따른다` | 위와 같음 |
| 티어(누적 XP 이상인 가장 높은 단계, 경계값), 31단계 | `UserTierTests#누적_XP_이상인_가장_높은_단계를_고른다`, `UserTierTests#단계는_브론즈_5부터_마스터까지_31개다` | 컴파일 실패: `UserTier` 없음 |
| 순위표 대상(정답 1개 이상) | `LeaderboardServiceTests#정답_문제가_없는_회원은_순위표에_넣지_않는다` | 컴파일 실패: `LeaderboardService` 없음 |
| submissionCount, accuracy | `LeaderboardServiceTests#제출_수와_정답률은_채점이_끝난_제출_기준으로_센다` | 위와 같음 |
| streak(오늘까지 이어진 정답 날, 오답만 낸 날 제외) | `LeaderboardServiceTests#연속_풀이는_오늘까지_이어진_정답_날짜_수다` | 위와 같음 |
| streak(어제에서 끝나도 셈) | `LeaderboardServiceTests#오늘_정답이_없어도_어제까지_이어졌으면_센다` | 위와 같음 |
| streak(그제 이전에 끝나면 0) | `LeaderboardServiceTests#마지막_정답이_그제_이전이면_연속_풀이는_0이다` | 위와 같음 |
| streak 날짜(한국 시간 자정 기준) | `LeaderboardServiceTests#연속_풀이_날짜는_한국_시간_자정을_기준으로_나눈다` | 위와 같음 |
| 순위(XP → 정답 수 → 회원 ID, 공동 순위 없음) | `LeaderboardServiceTests#순위는_XP_정답_수_회원_ID_순으로_매긴다` | 위와 같음 |
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
| 쿼리 파라미터 바인딩(keyword, tier, solved, sort, direction, page, size) | `LeaderboardControllerTests#검색_필터_정렬_파라미터를_받는다` | `expected 200 but was 401` |
| 트랜잭션·동시성 | 해당 없음: 읽기 전용 조회만 한다 | 해당 없음 |
