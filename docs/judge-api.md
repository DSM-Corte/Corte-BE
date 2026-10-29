# 테스트케이스·실행·제출 API

이슈: https://github.com/DSM-Corte/Corte-BE/issues/27

기존 #37의 JudgeProvider/Judge0JudgeProvider를 재사용한다. 컴파일·실행·격리는 Judge0가 담당하며
백엔드는 테스트케이스, 요청 제한, 제출 스냅샷, 결과 저장과 권한을 관리한다.
자체 DockerRunner와 WebSocket 터미널은 제거했다. 프론트엔드·AI 리뷰·점수·랭킹·GitHub 저장은 범위 밖이다.

## 실행 준비

Docker Desktop Linux 엔진과 JWT_SECRET을 준비한 뒤 `docker compose up -d --build`로 시작한다.
Judge0 CE 1.13.1의 실행 호스트는 cgroup v1과 호환되어야 한다. 이 PC의 Docker Desktop(cgroup v2)에서는
API 연결까지 가능하지만 샌드박스 초기화가 실패하는 것을 실제 확인했다.
`Failed to create control group /sys/fs/cgroup/memory/box-...`와 INTERNAL_ERROR가 나오면 실행 호스트 호환성을 먼저 해결해야 한다.
격리·메모리 제한을 끄는 우회는 적용하지 않는다.
기존 #37의 Judge0 서버·워커·Postgres·Redis 구성을 사용한다. app은 내부 주소
`http://judge0-server:2358`로 연결한다. Judge0 API의 호스트 포트는 열지 않으며 앱에 Docker 소켓을 마운트하지 않는다.
Judge0 공식 구성에 따른 privileged 권한은 서버·워커에만 있다.

호스트에서 앱을 실행할 경우 별도로 접근 가능한 Judge0 주소를 JUDGE0_URL로 지정하고
JUDGE_ENABLED=true를 설정한다. main의 기본값은 false(503/J005), Compose app의 기본값은 true다.
이번 API는 입력 전체를 먼저 보내는 방식이다. 실행 중 추가 입력·실시간 출력·WebSocket은 지원하지 않는다.

| language | Judge0 CE 1.13.1 ID | 환경 |
| --- | --- | --- |
| C | 50 | GCC 9.2, C99 |
| CPP | 54 | GCC 9.2, C++17 |
| JAVA | 62 | OpenJDK 13, 패키지 없는 Main 클래스 |
| CSHARP | 51 | Mono 6.6, 명시적인 Main 메서드 |
| JAVASCRIPT | 63 | Node.js 12, 표준 입출력 |
| PYTHON | 71 | Python 3.8, 표준 입출력 |

언어 환경은 자체 실행기의 최신 버전과 다르다. .NET 8 최상위 문은 Mono 환경의 지원 대상이 아니다.
설치한 Judge0의 /languages를 기준으로 확인한다. API의 언어 이름은 기존대로 유지한다.
코드는 UTF-8 100KB 이하의 단일 소스다. 추가 패키지 설치나 solve(input) 자동 호출은 제공하지 않는다.

## 테스트케이스

| API | 권한 | 성공 |
| --- | --- | --- |
| POST /api/problems/{id}/test-cases | ADMIN | 201 + Location |
| GET /api/problems/{id}/test-cases | 공개, ADMIN만 비공개 포함 | 200 페이지 |
| PATCH /api/problems/{id}/test-cases/{caseId} | ADMIN | 200 |
| DELETE /api/problems/{id}/test-cases/{caseId} | ADMIN | 204 |

```json
{"input":"1 2\n","expectedOutput":"3\n","publicExample":true,"position":0}
```

입력·정답 각각 UTF-8 1MB 이하, 문제당 100개, position은 0 이상이다.
빈 문자열도 허용한다. PATCH에서 null·생략은 유지한다.
다른 문제의 caseId는 404다. 채점 순서는 position, ID 오름차순이다.
목록은 page=0, size=20(1~100), ID 내림차순의 기존 페이지 응답을 사용한다.

## 직접 실행

POST /api/executions → 201 + Location: /api/executions/{id}

```json
{"problemId":1,"language":"PYTHON","code":"name=input()\nprint(name)","stdin":"홍길동\n"}
```

stdin은 선택이며 생략·null은 빈 입력이다. UTF-8 1MB 이하이고 입력의 줄바꿈을 직접 지정한다.
생성 응답에는 id, QUEUED 상태와 비회원용 accessToken이 있다.
기존 streamPath 필드는 null로 유지하며 연결 주소로 사용하지 않는다.

GET /api/executions/{id} → 200. 완료까지 상태를 조회한다.
비회원은 X-Judge-Token 헤더, 회원은 본인 Authorization Bearer 헤더를 사용한다.

```json
{"id":10,"verdict":"FINISHED","passedCount":0,"totalCount":0,"runtimeMs":12,
 "message":"","accessToken":null,"streamPath":null,"stdout":"홍길동\n","stderr":""}
```

stdout·stderr는 JSON 문자열이다. 컴파일 진단은 message에 제공한다.
프로그램이 입력을 모두 소비하면 EOF를 받으며 추가 입력을 기다리지 않는다.

## 제출과 비회원 정책

POST /api/submissions → 201 + Location: /api/submissions/{id}.
problemId, language, code를 보낸다. 제출 요청의 stdin은 사용하지 않고 등록한 테스트케이스를 사용한다.
GET /api/submissions/{id}에서 상태와 passedCount, totalCount, runtimeMs를 조회한다.
비회원은 X-Judge-Token, 회원은 본인 JWT를 사용한다. 잘못된 토큰·소유자는 403이다.

QUEUED → RUNNING → 최종 상태 순서다. 공개·비공개 케이스를 스냅샷으로 고정하고 8개씩 읽어
기존 Provider에 넘긴다. Provider 내부에서는 최대 20개씩 제출·조회한다.
한 배치의 케이스는 모두 실행될 수 있지만 결과는 첫 실패까지 집계하고 이후 배치는 실행하지 않는다.
Judge0는 케이스별 컴파일·실행을 담당한다. 케이스가 없으면 제출은 409, 직접 실행은 가능하다.

정답은 Judge0로 보내지 않는다. Judge0에서 실행 성공한 stdout 원본 바이트를 서버에서 비교한다.
공백, CRLF/LF, 마지막 줄바꿈까지 같아야 정답이며 trim·정규화를 하지 않는다.
제출 결과에 비공개 입력·정답·stdout·stderr를 반환하지 않는다.

비회원은 author_id=null인 임시 데이터이며 푼 문제·이력·점수에 반영하지 않고 로그인 후에도 연결하지 않는다.
비회원 작업과 모든 직접 실행은 1시간 후 접근 차단되고 1분 주기 정리로 삭제한다.
회원 제출은 보존하며 본인만 조회한다. 풀이 저장 이벤트는 발행하지 않는다.

## 제한·판정·저장

- 문제의 timeLimitMs(1~60000), memoryLimitMb와 선택 언어의 languageMemoryLimitsMb를 접수 시 고정한다.
  언어별 값은 6~1024MB, 미지정 언어는 기본값이다. PATCH null·생략은 유지, {}는 초기화다.
- CPU 시간은 초, 메모리는 KB로 변환해 Judge0에 전달한다. 벽시계 상한은 CPU 제한+5초(최소5초)다.
  네트워크는 명시적으로 차단하고 파일당 출력 한도는 1024KB다.
- runtimeMs는 Judge0가 보고한 CPU 시간이다. 직접 실행은 해당 시간, 제출은 첫 실패까지 검사한 케이스 시간의 합이다.
  Docker 대기·HTTP 조회·컴파일 시간은 포함하지 않는다. 언어 런타임 초기화도 Judge0 측정 정책을 따른다.
- 판정: ACCEPTED, WRONG_ANSWER, COMPILE_ERROR, RUNTIME_ERROR, TIME_LIMIT, OUTPUT_LIMIT, SYSTEM_ERROR.
  직접 실행 성공은 FINISHED다. Judge0가 메모리 초과를 별도 상태로 구분하지 않아 임의로 MEMORY_LIMIT으로 추정하지 않는다.
  기존 MEMORY_LIMIT·CANCELLED enum은 저장된 데이터 호환을 위해 유지한다.
- Judge0 통신 실패·응답 오류·대기 초과는 작업을 SYSTEM_ERROR로 종료한다.
  내부 Provider 오류 코드는 J006(제출), J007(조회), J008(대기)이며 기존 J001~J005와 겹치지 않는다.
- 단일 백엔드 인스턴스다. 기본 동시2, 대기16, IP·회원당 동시1(대기 포함), 분당30회다.
  완료·오류·접수 실패 시 자리를 반환한다. 상태 전이는 조건부 UPDATE로 중복 처리를 막는다.
- 실행 중 DB 트랜잭션을 유지하지 않는다. 문제 잠금·DB 내부 스냅샷 복사와 8개 단위 projection을 유지한다.
  시작 복구 전에는 접수하지 않고 재시작 시 미완료 작업을 SYSTEM_ERROR로 바꾼다.
- judge_jobs에 nullable stdin/stdout/stderr LOB 컬럼을 추가한다. 기존 행·컬럼은 삭제하지 않는다.
  실행 완료 후 stdin은 지운다. 제출 스냅샷은 완료·실패·만료·재시작 시 정리한다.

설정: JUDGE_ENABLED, JUDGE_CONCURRENCY, JUDGE_QUEUE_CAPACITY, JUDGE_REQUESTS_PER_MINUTE,
JUDGE_MAX_CONCURRENT_PER_IDENTITY, JUDGE0_URL, JUDGE0_POLL_INTERVAL(1s),
JUDGE0_IDLE_TIMEOUT(2m), JUDGE0_REQUEST_TIMEOUT_MS(5000).
삭제한 자체 실행기의 JUDGE_DOCKER·연결/기동/컴파일 제한 설정은 사용하지 않는다.

## 검증

기본 `.\gradlew.bat build`는 외부 HTTP 스텁·H2·MockMvc로 검증한다.
실제 Judge0 E2E는 JUDGE0_TESTS=true, JUDGE0_URL을 지정한다.
실제 MySQL 검증은 JUDGE_MYSQL_TESTS=true 및 전용 테스트 DB 환경을 사용한다.
과거 DockerRunner의 테스트는 제거된 구현 전용이라 삭제했고, 새 입력·결과·제한·권한 검증으로 대체했다.
최신 실행 결과와 SHA는 별도 검증 보고서를 따른다.
