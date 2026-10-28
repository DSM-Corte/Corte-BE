# Judge0 채점 Provider

- 이슈: #37
- 기준 SHA: ef9c7c1a40eca829b7425d398f153f304dba2612
- 참고 구현: Zukbam-BE `feature/#23-submit-grading`의 `global/judge`

## 기능: Judge0 채점 Provider
- 목적: 앞으로 만들 제출·채점 기능이 코드와 테스트케이스를 넘기면 Judge0에서 실행해 판정을 받는다.
  전체 판정과 테스트케이스별 결과를 함께 돌려줘 결과 화면과 AI 리뷰에서 몇 번 케이스가 왜 틀렸는지 쓸 수 있게 한다
- API: HTTP API 없음. `JudgeProvider.judge(JudgeRequest) → JudgeResult`
  - `JudgeRequest`(@Builder record): `language`, `code`, `cases`(`JudgeCase(input, expectedOutput)` 목록), `timeLimitMs`, `memoryLimitMb`
  - `JudgeResult`(@Builder record): `status`(전체 판정), `timeMs`(최댓값), `memoryKb`(최댓값), `cases`(요청 순서의 `CaseResult(status, timeMs, memoryKb)` 목록).
    측정값이 없으면 null
  - `JudgeLanguage`와 Judge0 1.13.1 ID: C99 50(`-std=c99`), CPP17 54(`-std=c++17`), JAVA 62, PYTHON 71, JAVASCRIPT 63, TEXT 43
  - `JudgeStatus`: ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILE_ERROR, INTERNAL_ERROR
- 데이터: 엔티티·스키마 변경 없음
- 규칙
  - 테스트케이스를 20개씩 `POST /submissions/batch?base64_encoded=true`로 제출한다. 코드·입력·기대 출력은 UTF-8 base64
  - `cpu_time_limit = timeLimitMs / 1000.0`(초), `memory_limit = memoryLimitMb × 1024`(KB)
  - `poll-interval`(기본 1s)마다 끝나지 않은 토큰만 20개씩 `GET /submissions/batch?tokens=...&base64_encoded=true&fields=token,status,time,memory`로 조회한다
  - Judge0 상태 1·2는 채점 중, 3~14는 판정으로 바꾼다(3 ACCEPTED, 4 WRONG_ANSWER, 5 TIME_LIMIT_EXCEEDED, 6 COMPILE_ERROR,
    7~12 RUNTIME_ERROR, 13·14 INTERNAL_ERROR). 모르는 ID는 INTERNAL_ERROR
  - 전체 판정은 요청 순서에서 처음 나온 ACCEPTED가 아닌 판정, 모두 맞으면 ACCEPTED
  - Judge0 시간("0.012" 초)은 반올림한 ms 정수로 바꾼다. 메모리는 Judge0 값(KB) 그대로
  - 테스트케이스가 비어 있으면 `IllegalArgumentException`(호출 쪽 실수, 빈 목록이 ACCEPTED가 되는 것을 막는다)
- 오류(접두사 `J`)
  - 제출 실패(연결·HTTP 오류, 토큰 누락, 개수 불일치) → `JUDGE_SUBMIT_FAILED` J001 / 500
  - 결과 조회 실패(연결·HTTP 오류, 빈 응답) → `JUDGE_FETCH_FAILED` J002 / 500
  - 끝난 케이스가 `idle-timeout`(기본 2m) 동안 늘지 않거나 대기 중 인터럽트 → `JUDGE_TIMEOUT` J003 / 500
- 트랜잭션: 없음. 몇 분까지 기다릴 수 있어 트랜잭션 밖에서 호출해야 한다 / 동시성: 없음(상태 없음) / 성능: 20개씩 묶어 제출·조회
- 기술 선택: OpenFeign `Judge0Client`(`${judge0.url}`), 연결·읽기 timeout 5s(`spring.cloud.openfeign.client.config.judge0`).
  `Judge0Properties`(`judge0.url`, `poll-interval`, `idle-timeout`).
  `compose.yml`에 `judge0/judge0:1.13.1` 서버·워커·Postgres·Redis 추가(포트 미공개, app에 `JUDGE0_URL`)
- 위치: `domain/judge/` 루트(`JudgeLanguage`, `JudgeStatus`, `Judge0Properties`), `client`(`Judge0Client`, `Judge0Language`, `Judge0Status`),
  `client/dto`(`Judge0Dto`), `dto`(`JudgeRequest`, `JudgeCase`, `JudgeResult`), `service`(`JudgeProvider`, `Judge0JudgeProvider`), `exception`
- 범위 밖: 테스트케이스 엔티티, 제출 API, 판정 저장, 점수, stdout·컴파일 메시지 반환, 메모리 초과 판정(Judge0가 구분하지 않음)
- 새 컨벤션: 오류 코드 접두사 `J`(채점) 추가

## 테스트 대응
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 모두 맞으면 ACCEPTED, 시간·메모리 최댓값, 케이스별 결과 순서 | | |
| 하나라도 틀리면 처음 실패한 판정이 전체 판정 | | |
| 채점 중인 케이스는 끝날 때까지 다시 조회 | | |
| 21개 이상이면 20개씩 나눠 제출·조회 | | |
| base64·언어 ID·컴파일 옵션·단위 변환 | | |
| 모르는 상태 ID는 INTERNAL_ERROR | | |
| 제출 HTTP 오류·토큰 누락이면 J001 | | |
| 조회 HTTP 오류면 J002 | | |
| 진척 없이 idle-timeout이 지나면 J003 | | |
| 테스트케이스가 비어 있으면 IllegalArgumentException | | |
