# 내 통계 조회

- 이슈: #57
- 기준 SHA: 432f62f8f1114ffbc087c736651d98372a16999f

## 기능: 내 통계 조회
- 목적: 회원이 자기 풀이 기록을 한눈에 보고 어떤 언어·난이도·분야에 강하고 약한지 파악한다
- API: `GET /api/users/me/stats`, 로그인 필요(토큰 없으면 401 A001)
  - 성공 200:
    ```json
    {
      "solvedCount": 2, "submissionCount": 3, "acceptedCount": 2, "accuracy": 66.7,
      "verdicts": {"ACCEPTED": 2, "WRONG_ANSWER": 1, "COMPILE_ERROR": 0, "RUNTIME_ERROR": 0,
                   "TIME_LIMIT": 0, "MEMORY_LIMIT": 0, "OUTPUT_LIMIT": 0},
      "languages": [{"name": "JAVA", "solvedCount": 2, "submissionCount": 3, "acceptedCount": 2, "accuracy": 66.7}],
      "difficulties": [{"difficulty": "BRONZE_5", "solvedCount": 1}, "... 31개"],
      "tags": [{"name": "구현", "solvedCount": 1, "submissionCount": 2, "acceptedCount": 1, "accuracy": 50.0}],
      "categories": [{"name": "기초", "solvedCount": 2, "submissionCount": 3, "acceptedCount": 2, "accuracy": 66.7}],
      "hardestProblem": {"problemId": 7, "title": "A+B", "difficulty": "BRONZE_5"}
    }
    ```
- 데이터: 새 테이블·컬럼 없음. 기존 `judge_jobs`, `problems`, `problem_tags`를 읽기만 한다. 스키마 영향 없음
- 규칙
  - 집계 대상: 내 제출(`author = 나`, `submission = true`) 중 판정이 채점 완료 7종
    (`ACCEPTED`, `WRONG_ANSWER`, `COMPILE_ERROR`, `RUNTIME_ERROR`, `TIME_LIMIT`, `MEMORY_LIMIT`, `OUTPUT_LIMIT`)인 것.
    `QUEUED`, `RUNNING`, `SYSTEM_ERROR`, `CANCELLED`, `FINISHED`와 직접 실행·비회원 제출은 제외한다. 기간은 전체
  - 푼 문제 수: `ACCEPTED` 제출이 있는 서로 다른 문제 수
  - 정답률: `acceptedCount / submissionCount × 100`, 소수 첫째 자리 반올림. 제출이 0건이면 0.0
  - verdicts: 7종을 항상 모두, enum 순서로 준다(없으면 0)
  - languages, tags, categories: 같은 모양 `{name, solvedCount, submissionCount, acceptedCount, accuracy}`.
    제출이 있는 항목만, 푼 문제 수 내림차순 → 제출 수 내림차순 → 이름 오름차순
  - tags: 태그가 여러 개인 문제는 태그마다 센다. 태그 없는 문제는 tags에서만 빠진다
  - difficulties: `Difficulty` 31개(UNRATED 포함)를 enum 순서로 모두 준다(없으면 0)
  - hardestProblem: 푼 문제 중 UNRATED를 뺀 가장 높은 난이도, 같으면 문제 ID가 작은 것. 없으면 `null`
  - 태그·분류·난이도는 제출 당시가 아니라 현재 문제 값 기준이다
- 오류: 새 오류 없음(401 A001만)
- 트랜잭션: 읽기 전용 / 동시성: 해당 없음(읽기만)
- 성능: 제출마다 문제 ID·제목·분류·난이도·언어·판정만 projection으로 한 번에 조회하고,
  태그는 시도한 문제 ID 목록으로 기존 `findTagsByProblemIds`를 한 번 호출한다(제출 0건이면 호출하지 않음). 집계·정렬은 Java에서 한다
- 범위 밖: 시도한 문제 수, 연속 일수, 다른 회원 통계, 기간 필터, 레이팅·티어·랭킹
- 새 컨벤션: 없음

## 테스트 대응
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
