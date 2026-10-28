# 테스트케이스·실행·제출 API

이슈: https://github.com/DSM-Corte/Corte-BE/issues/27

백엔드 API와 Docker Linux 격리 실행을 제공한다. 프론트엔드, 리뷰, 문제집, 랭킹,
점수, GitHub 자동 저장은 이번 변경에 포함하지 않는다. 기존 프론트엔드의 로그인 제한과
브라우저 실행은 추후 API 연결 작업이 필요하다.

## 실행 준비

Docker Desktop의 Linux 엔진을 실행하고 공식 이미지를 먼저 준비한다.
실행 요청 중 이미지를 자동 다운로드하지 않는다.

```powershell
docker pull gcc:14-bookworm
docker pull eclipse-temurin:21-jdk
docker pull mcr.microsoft.com/dotnet/sdk:8.0
docker pull node:22-bookworm-slim
docker pull python:3.13-slim
docker compose up -d db

$env:JUDGE_ENABLED = 'true'
$env:JUDGE_DOCKER = (Get-Command docker).Source
# 기존 JWT_SECRET, DB 설정을 준비한 뒤 실행한다.
.\gradlew.bat bootRun
```

PATH가 아직 반영되지 않았다면 JUDGE_DOCKER에 설치된 docker.exe 절대 경로를 지정한다.
이 PC 경로는 `C:\Users\user\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe`이다.
이미지 다운로드에는 Docker credential helper도 PATH에 필요하다.
기본 JUDGE_ENABLED=false이며 꺼진 상태에서 실행·제출은 503 J005이다.
기본 Compose의 app 컨테이너에는 Docker 제어 연결을 추가하지 않았다.
DB만 Compose로 실행하고 백엔드는 호스트에서 실행하는 구성이다.

## 언어

| language | 환경 | 소스 형식 |
| --- | --- | --- |
| C | GCC 14, C17 | Main.c, main 함수 |
| CPP | GCC 14, C++20 | Main.cpp, main 함수 |
| JAVA | Java 21 | 패키지 없는 Main.java, Main.main |
| CSHARP | .NET 8 | Main.cs, Main 메서드 또는 최상위 문 |
| JAVASCRIPT | Node.js 22 | Main.js, 표준 입출력 |
| PYTHON | Python 3.13 | Main.py, 표준 입출력 |

코드는 UTF-8 최대 100KB, 단일 소스이다. 추가 패키지 설치는 제공하지 않는다.
JavaScript의 solve(input)을 자동 호출하지 않는다. 실제 Node.js 프로그램이어야 한다.
이미지 태그는 갱신될 수 있으므로 운영에서는 검증한 digest 고정을 권장한다.

## 테스트케이스

| API | 권한 | 응답 |
| --- | --- | --- |
| POST /api/problems/{problemId}/test-cases | ADMIN | 201 + Location + 생성 데이터 |
| GET /api/problems/{problemId}/test-cases | 공개, ADMIN만 비공개 케이스 포함 | 200 페이지 |
| PATCH /api/problems/{problemId}/test-cases/{id} | ADMIN | 200 수정 데이터 |
| DELETE /api/problems/{problemId}/test-cases/{id} | ADMIN | 204 |

```json
{"input":"1 2\n","expectedOutput":"3\n","publicExample":true,"position":0}
```

입력·예상 출력 각각 UTF-8 최대 1MB, 문제당 100개, position은 0 이상이다.
빈 문자열·공백만인 문자열도 허용한다. 수정의 null·생략 필드는 유지한다.
다른 문제의 케이스 ID를 지정하면 404이다. 채점은 position, ID 오름차순이다.
목록은 page=0, size=20 기본값, size 1~100, ID 내림차순으로 조회한다.

```json
{"content":[{"id":1,"input":"1 2\n","expectedOutput":"3\n","publicExample":true,"position":0}],
 "page":0,"size":20,"totalElements":1,"totalPages":1}
```

## 직접 실행과 터미널

POST /api/executions → 201. JWT는 선택이다.

```json
{"problemId":1,"language":"PYTHON","code":"name=input()\nprint(name)"}
```

```json
{"id":10,"verdict":"QUEUED","passedCount":0,"totalCount":0,"runtimeMs":0,
 "message":null,"accessToken":"임시_비밀값","streamPath":"/api/executions/10/stream"}
```

회원에게도 실행별 임시 연결 토큰을 발급한다. 토큰을 URL에 넣지 않는다.
ws://localhost:8080/api/executions/10/stream에 연결한 뒤 다음 메시지를 보낸다.

```json
{"type":"auth","token":"임시_비밀값"}
{"type":"stdin","data":"홍길동\n"}
{"type":"eof"}
```

stdin은 여러 번 가능하며 줄바꿈은 클라이언트가 data에 명시한다.
eof는 입력을 닫고, `{"type":"cancel"}` 또는 연결 종료는 실행을 중단한다.
한 실행에는 한 연결만 허용한다. 첫 auth는 10초 안에 보내야 한다.
모든 연결은 최대 15분 후 종료하며, 완료된 실행의 출력 버퍼는 1분 후 정리한다.

서버 메시지는 `{type,data}`이다. ready는 컴파일 후 입력 준비,
stdout/stderr의 data는 **원본 바이트의 Base64**, compileError는 컴파일 진단 문자열,
exit는 FINISHED·CANCELLED 등 최종 판정이다. 출력 바이트를 합치거나 스트리밍 UTF-8
디코더를 사용해야 한다. 한글 바이트가 메시지 사이에서 나뉠 수 있다.
출력은 프로그램이 flush할 때 전달된다. 입력 합계 1MB, 메시지 16KB,
대기 입력 64개, 열린 WebSocket 64개로 제한한다.
허용 Origin은 localhost:5173과 localhost:3000의 HTTP 주소이다.

## 제출·결과와 비회원 정책

POST /api/submissions → 201 + Location. 요청은 직접 실행과 같다.
비회원은 accessToken을 발급하고, 회원은 본인 JWT를 사용하므로 accessToken이 null이다.
GET /api/submissions/{id} → 200. 비회원은 X-Judge-Token 헤더, 회원은 Authorization Bearer 헤더가 필요하다.

```json
{"id":11,"verdict":"ACCEPTED","passedCount":2,"totalCount":2,"runtimeMs":180,
 "message":null,"accessToken":null,"streamPath":null}
```

QUEUED → RUNNING → 최종 판정 순서로 진행한다. 공개·비공개 케이스 모두 검사하며 첫 실패에서 멈춘다.
케이스가 없는 문제는 직접 실행만 가능하고 제출은 409이다.
공백, CRLF/LF, 마지막 줄바꿈까지 원본 UTF-8 바이트가 같아야 정답이다.
관리자는 예제·정답의 마지막 줄바꿈 여부도 정확히 등록해야 한다.
비공개 입력·정답·실제 출력·실행 stderr는 결과로 반환하지 않는다.
컴파일 오류에는 사용자 소스에 대한 진단만 제공하며, 테스트 입력은 컴파일 이후에 전달한다.

판정은 ACCEPTED, WRONG_ANSWER, COMPILE_ERROR, RUNTIME_ERROR, TIME_LIMIT,
MEMORY_LIMIT, OUTPUT_LIMIT, SYSTEM_ERROR, CANCELLED이다. 직접 실행 성공은 FINISHED이다.
runtimeMs는 검사한 실행들의 경과 시간 합계이며 성공 케이스의 컴파일 시간은 제외한다.
메모리 사용량을 추정해 반환하지 않는다.

비회원도 실행·제출·채점이 가능하다. author_id=null인 임시 작업이므로 푼 문제·회원 이력에
반영하지 않고, 나중에 로그인해도 계정에 연결하지 않는다. 완료 이벤트도 발행하지 않는다.
비회원 데이터는 발급 시점부터 1시간 후 접근을 차단하고, 1분 주기의 정리 작업으로 삭제한다.
회원 제출은 보존하며 본인만 조회한다. 직접 실행은 회원 여부와 무관하게 임시 데이터이다.

## 저장·동시성·자원 제한

- test_cases, judge_jobs, judge_case_snapshots를 추가한다. 기존 문제 컬럼은 변경하지 않는다.
- 접수 시 문제 행을 잠그고 테스트 데이터를 DB 안에서 스냅샷으로 복사한다.
  전체 케이스를 접수 메모리에 올리지 않는다. 채점은 projection으로 8개씩 읽는다.
  조회·갱신만 짧은 트랜잭션에서 수행하고 실제 실행 중에는 DB 트랜잭션을 유지하지 않는다.
- 시간·메모리 제한도 고정하므로 접수 후 케이스 수정·삭제가 진행 중인 채점에 영향을 주지 않는다.
  스냅샷은 완료·실패·만료·재시작 때 삭제한다.
- 문제 단위 잠금으로 100개 제한과 케이스 변경을 보호한다. 조건부 UPDATE로 중복 실행·결과 갱신을 막는다.
- 제출이 남은 문제 삭제는 409이다. 제출이 없으면 직접 실행과 케이스를 먼저 삭제한다.
- 전체 실행 2개, 대기 16개, IP·회원별 분당 요청 30개. 임의의 프록시 헤더는 신뢰하지 않는다.
- CPU 1개, 프로세스(스레드 포함) 128개, 열린 파일 1024개, stdout+stderr 합계 1MB.
- 컴파일은 768MB·60초. 실행은 문제의 제한을 적용한다. 지원 상한은 메모리 1024MB·시간 60000ms이며 초과하면 409.
  직접 실행은 입력 대기를 포함해 120초이다. 전체 제출은 다음 케이스 시작 전 10분 경과 여부도 검사한다.
- 실행별 새 컨테이너. 비루트, network=none, read-only root, cap-drop=ALL, no-new-privileges,
  swap 금지, /work 128MB·/tmp 64MB tmpfs를 사용한다. 쓰기 공간도 메모리 제한에 포함된다.
- 소스를 명령 문자열에 넣지 않고 고정된 파일 이름으로 stdin을 통해 전달한다.
  호스트 폴더, Docker 소켓, DB·JWT·GitHub 비밀값을 사용자 컨테이너에 제공하지 않는다.
- 종료·오류·취소 시 컨테이너를 제거한다. 정리 실패 시 새 실행을 차단한다.
  재시작하면 corte.runner=true 라벨의 남은 컨테이너를 정리하고 미완료 작업을 시스템 오류로 처리한다.
- **단일 백엔드 인스턴스용**이다. 같은 Docker 엔진에서 중복 실행하면 안 된다.
  인터넷 서비스 배포 시 실행 전용 VM/호스트를 별도로 구성해야 한다. 컨테이너 탈출까지 완전히 방어한다고 보장하지 않는다.

설정은 application.yml의 judge와 JUDGE_* 환경 변수로 관리한다.
.NET 공유 메모리 경로는 사용자 소유의 /work/runtime으로 지정한다.
[관련 .NET 이슈](https://github.com/dotnet/runtime/issues/135726),
[Docker 실행 옵션](https://docs.docker.com/engine/containers/run/).

## 오류와 검증

| HTTP / code | 의미 |
| --- | --- |
| 400 C001 | 요청·크기·페이지 검증 |
| 401 A001 | 관리자 쓰기에 인증 없음 |
| 403 J002 | 작업 소유권 없음 |
| 404 J001 | 대상 없음 또는 만료 |
| 409 J003 | 케이스 없음·개수 초과·지원 상한 초과·제출이 있는 문제 삭제·중복 연결 |
| 429 J004 | 요청·대기열·입력 제한 초과 |
| 503 J005 | 실행 비활성화·환경 정리 실패 |

일반 회원의 관리자 쓰기는 기존 SecurityConfig의 403을 유지한다.
WebSocket의 잘못된 메시지·토큰은 연결 종료(1008)로 처리한다.

기본 `gradlew.bat build`는 H2·HTTP·서비스 테스트를 실행한다.
JUDGE_DOCKER_TESTS=true와 JUDGE_DOCKER를 지정한 뒤 build하면 실제 6개 언어,
네트워크·파일·비밀값 격리, 메모리·시간·출력 제한, HTTP 제출, WebSocket 입력·EOF·취소도 검증한다.
선택 실행하지 않은 환경 테스트는 기본 빌드 통과만으로 검증됐다고 판단하지 않는다.
MySQL 검증은 임시 전용 DB와 JUDGE_MYSQL_TESTS=true, JUDGE_MYSQL_URL을 지정한 뒤
`--tests '*MySqlConcurrencyTests'`로 실행한다. create-drop이므로 운영 DB를 지정하면 안 된다.

흐름 확인: JobController → JobManager → JobStore/DockerRunner를 따라가 보자.
EOF를 보내지 않으면 sys.stdin.read()가 왜 기다리는지 설명하고,
정답의 마지막 줄바꿈 하나를 제거해 오답으로 바뀌는지 직접 확인해보자.

## 이번 작업의 검증 기록 (2026-10-28)

JUDGE_DOCKER_TESTS=true와 JUDGE_MYSQL_TESTS=true를 지정한 `gradlew.bat build`:
**112개 통과, 실패 0, 오류 0, 건너뜀 0.** 6개 언어 실행은 메모리 256MB로 확인했다.
MySQL은 별도 임시 컨테이너의 전용 DB에서 검증했으며 기존 DB에는 접속하지 않았다.
검증 후 실행 컨테이너 잔여 0개를 확인하고 임시 MySQL과 익명 볼륨을 제거했다.

| Checklist | 결과와 근거 |
| --- | --- |
| 컨벤션 | 통과. main/test 지침 적용. JobManager 생성자·트랜잭션 분리는 main 지침에 기록 |
| 빌드 | 통과. 테스트를 포함한 전체 build 성공 |
| 성능 | 통과. 페이지·projection, DB 내부 스냅샷 복사, 8개 단위 일괄 조회. 연관 엔티티 N+1 없음. 부하 벤치마크는 범위 밖 |
| 동시성 | 통과. H2·실제 MySQL의 동시 케이스 생성 한도 및 조건부 상태 전이 검증 |
| 변경 범위 | 프론트엔드와 기존 DB를 변경하지 않음. 변경 내용과 공백 오류를 Git diff로 검토 |

커밋 단위는 확정 명세 추가(47c455f), 테스트케이스·격리 실행·제출·채점 및 검증 추가이다.
주요 리뷰 지점은 JobStore.create의 스냅샷·비회원 소유권,
JobManager.execute의 배치 채점·큐 해제, DockerRunner.execute의 격리 옵션·정리,
ExecutionSocket.handleTextMessage의 연결 인증·입력 전달이다.
기존 문제 API의 C004 오류 계약은 유지했다.
운영 배포, 별도 실행 호스트 구성, 도메인·TLS 설정, 프론트엔드 연결은 아직 실행하지 않은 후속 범위이다.
