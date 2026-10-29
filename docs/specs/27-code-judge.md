# 기능: 문제 테스트케이스 및 격리 실행·제출·채점

> 현재 명세는 아래의 **Judge0 통합 확정 변경**을 우선 적용한다. 이전 Docker·WebSocket 설계와 검증은 변경 이력이다.

## Judge0 통합 확정 변경

- 사용자 승인: 기존 #37 Judge0를 실행·채점 모두에 재사용하며 입력을 먼저 작성하는 방식으로 통일한다.
  프론트엔드는 수정하지 않는다. 별도 실행기·격리 계층을 만들지 않는다.
- 통합 기준: 29bff2ddb3b552bc45e038656bdea5765f235ec4, 재사용 브랜치 origin/feature/#37-judge0-provider(31ea217).
- POST /api/executions는 선택 stdin(UTF-8 1MB)을 받고 201 + /api/executions/{id}를 반환한다.
  GET /api/executions/{id}에서 상태·stdout·stderr·컴파일 메시지를 조회한다. 기존 WS 주소는 제거한다.
  streamPath 응답 필드는 null이다. 회원은 본인 JWT, 비회원은 X-Judge-Token으로 소유권을 검사한다.
- POST/GET /api/submissions와 테스트케이스 CRUD는 유지한다. 케이스·시간·선택 언어의 메모리를 고정한다.
  제출의 비공개 출력은 반환하지 않으며 비회원 작업을 계정의 풀이 기록으로 연결하지 않는다.
- Judge0의 C99/CPP17/JAVA/CSHARP/JAVASCRIPT/PYTHON 매핑을 사용한다(C# Mono ID51 추가).
  격리·컴파일·CPU/메모리 제한은 Judge0가 수행한다. 네트워크 false, 파일당 한도1024KB,
  CPU 제한+5초(최소5초)의 벽시계 상한을 전달한다. 실행시간은 Judge0 보고 CPU 시간이며
  이전 자체 진입점 측정 방식과 다르다. 메모리 초과는 Judge0가 구분하지 않아 RUNTIME_ERROR 등 엔진 판정을 따른다.
- 정답을 엔진에 보내지 않고 실행 성공 stdout의 UTF-8 원본 바이트를 비교한다. 줄바꿈·공백 정규화 없음.
- 기존 오류 J001~J005 유지, Provider 오류는 J006~J008로 병합한다. 작업 중 통신 실패는 SYSTEM_ERROR로 저장한다.
- DB에는 nullable stdin/stdout/stderr 컬럼만 추가한다. 기존 데이터 삭제·필수 컬럼 변경은 없다.
- 기존 원자적 상태 전이·문제 잠금·8개 단위 projection·유한 큐·IP/사용자 제한을 유지한다.
  외부 HTTP 호출 중 DB 트랜잭션은 없다. 컴파일 재사용은 직접 구현하지 않고 케이스별 처리를 Judge0에 맡긴다.
- 가정: 단일 인스턴스, Judge0 CE1.13.1 환경. 기존 최신 언어 이미지 대신 해당 엔진 언어 버전을 사용한다.
- 상세 요청·응답·설정: docs/judge-api.md.

### 통합 테스트 대응

| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 정확한 줄바꿈 비교 | Judge0JudgeProviderTests#엔진이_성공해도_출력의_줄바꿈이_다르면_오답이다 | 기존 Provider는 ACCEPTED 반환(2개 회귀 테스트 실패 확인) |
| 정답 미전송·네트워크 차단 | Judge0JudgeProviderTests#예상출력을_엔진에_보내지_않고_네트워크를_차단한다 | 기존 expected_output 존재, enable_network 누락 |
| 다른 토큰 응답 거절 | Judge0JudgeProviderTests#다른_제출의_토큰을_받으면_조회_오류로_거절한다 | JudgeFetchFailedException 미발생으로 실패 |
| C#·입력·출력·제한 매핑 | Judge0JudgeProviderTests#직접실행은_정답없이_입력을_보내고_표준출력과_오류를_반환한다; JobManagerTests#직접실행은_미리_받은_입력과_언어별_제한을_전달하고_출력을_저장한다 | 기존 Work.stdin·CaseResult.stdout·JudgeLanguage.CSHARP 없음(컴파일 불가) |
| 원자적 출력 저장·입력 바이트 제한 | JobStoreTests#실행입력은_고정하고_출력은_완료시에만_한번_저장한다; JobStoreTests#실행입력이_UTF8_1MB를_넘으면_거절한다 | 기존 CreateJobRequest.stdin·finishExecution 없음(컴파일 불가) |
| HTTP 실행201·조회200·토큰403·입력400 | ExecutionApiTests | 기존 실행 Location은 /stream이고 GET 실행 조회 없음 |
| 비공개 보호·비회원 제출 | ExecutionApiTests#비회원_제출도_기존_공급자에_연결하고_비공개_출력을_노출하지_않는다; JudgeEndToEndTests | 기존 Provider 연결 없음(DockerRunner 직접 호출) |
| 큐·IP 제한·장애 시 반환·복구 전 거절 | JobManagerTests | 자체 실행기 전용 테스트를 동일 정책의 Provider 경로 테스트로 교체 |
| 통신·누락 응답·시간 초과·상태 매핑 | 기존 Judge0JudgeProviderTests 전체 | #37의 기존 테스트와 레드 증거를 유지. 상태8은 SIGXFSZ 의미에 따라 OUTPUT_LIMIT으로 변경 |
| 오류·거절·취소·복구 시 stdin 삭제 | JobStoreTests#실행환경_오류로_종료해도_원본_입력은_지운다; JobStoreTests#접수거절_취소_재시작_복구도_실행입력을_정리한다 | 오류 종료 후 stdin="private input"이 남아 isNull 단언 실패 확인 후 공통 종료·복구 UPDATE에서 삭제 |

자체 DockerRunner/ExecutionClock/컨테이너 복구 전용 테스트는 구현 제거에 따라 삭제한다.
WS 입력·취소 테스트는 승인된 HTTP 사전 입력·출력 조회 및 권한 테스트로 대체한다. 임의 테스트 건너뛰기나 단언 약화가 아니다.

### 958bcd9 리뷰 수정

- Ruby Base64.encode64 응답의 줄바꿈을 MIME 디코더로 처리한다. 문자열 표시와 정답 바이트 비교 모두 같은 복원 경로를 사용한다.
- 재시작 복구 UPDATE에서 updatedAt도 현재 시각으로 갱신한다.
- 큰 응답을 고려해 읽기 기본30초와 JUDGE0_READ_TIMEOUT_MS를 추가한다. 기존 REQUEST 환경값을 명시한 경우 호환한다.
- 프록시 헤더 신뢰의 기본값은 none이며 배포 시 신뢰 프록시만 지정하도록 문서화한다.
  회원 제출 보존·문제 삭제409·10000개 제한 키 상한은 기존 안전 정책을 유지한다.

| 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 짧은·한글·60자 초과 줄바꿈 Base64의 stdout/stderr/compile_output | Judge0JudgeProviderTests#Judge0의_줄바꿈이_포함된_Base64_출력과_진단을_복원한다 | 3개 매개변수 모두 IllegalArgumentException 실패 확인 |
| 복구 시 updatedAt 변경 | JobStoreTests#재시작_복구는_실패_처리_시각도_갱신한다 | 이전 날짜의 updatedAt이 그대로라 isAfter 단언 실패 확인 |
| 읽기 제한의 새 설정·기존 설정·기본값 순서, 프록시 기본값 none | JudgeConfigTests 전체 | 설정 계약 확인. 연결 로직 변경이 아닌 YAML 설정 추가이므로 별도 기능 코드 되돌림은 해당 없음 |

- 이슈: https://github.com/DSM-Corte/Corte-BE/issues/27
- 기준 SHA: f2098c0df134f4a2d7d5a71965b36dc2160cc061
- 작업 브랜치: feature/#27-code-judge
- 기준 검증: 2026-10-28 gradlew.bat build 성공 (기능 수정 전).
- 목적: 회원과 비회원이 6개 언어로 코드를 직접 실행하고 제출하여 서버에서 채점받는다.
- 프론트엔드는 수정하지 않는다. 리뷰, 문제집, 랭킹, 점수, GitHub 자동 저장은 범위 밖이다.

## API 및 권한

| API | 요청 / 응답 | 권한 |
| --- | --- | --- |
| POST /api/problems/{id}/test-cases | input, expectedOutput, 공개 여부, 순서 / 201 + Location + 데이터 | ADMIN |
| GET /api/problems/{id}/test-cases | page, size / 200 페이지 | 공개 케이스는 누구나, ADMIN만 비공개 포함 |
| PATCH /api/problems/{id}/test-cases/{caseId} | 부분 수정 / 200 데이터 | ADMIN |
| DELETE /api/problems/{id}/test-cases/{caseId} | 204 | ADMIN |
| POST /api/executions | problemId, language, code / 201 실행 ID 및 연결 정보 | 비회원 가능 |
| WS /api/executions/{id}/stream | 입력, EOF, 중단 / stdout, stderr, 종료 | 실행 소유자 |
| POST /api/submissions | problemId, language, code / 201 제출 ID 및 대기 상태 | 비회원 가능 |
| GET /api/submissions/{id} | 200 상태 및 결과 | 제출 소유자 |

회원은 인증 주체로 소유권을 검사한다. 비회원 결과에는 임시 비밀 토큰이 필요하다.
비회원 코드·결과는 1시간 후 삭제한다. 정답이어도 푼 문제·이력·점수·랭킹에 반영하지 않고,
이후 로그인해도 계정에 연결하지 않는다. 회원 제출은 보존한다.

## 데이터와 규칙

- 새 테스트케이스·제출 테이블을 추가하고 기존 문제 데이터는 유지한다.
- 테스트케이스는 문제 FK, 원본 입력, 원본 예상 출력, 공개 여부, 순서, 감사 시각을 가진다.
- 코드 100KB, 입력 및 예상 출력 각각 1MB, 문제당 케이스 최대 100개.
- 지원 언어: C#, C, Java, C++, JavaScript(Node.js), Python. 단일 소스 파일의 표준 입출력 프로그램.
- 출력은 UTF-8 기준 공백, CR/LF, 마지막 줄바꿈까지 그대로 비교한다. trim 및 줄바꿈 정규화 없음.
- 제출 시 케이스·시간·메모리 제한을 스냅샷으로 고정한다. 공개·비공개 케이스 모두 검사한다.
- 비공개 케이스의 입력, 예상 출력, 실제 출력, stderr는 사용자 결과에 노출하지 않는다.
- 케이스 없는 문제는 직접 실행만 가능하고 채점 요청은 409로 거절한다.
- 제출 기록이 있는 문제 삭제는 409. 테스트케이스는 부모 삭제 시 함께 삭제한다.
- 결과: 정답, 오답, 컴파일 오류, 실행 오류, 시간·메모리·출력 제한 초과, 시스템 오류.

## 격리 및 자원

- 컴파일부터 Docker Linux 컨테이너 안에서 수행하고 실행 종료 후 환경을 폐기한다.
- 사용자 코드에 네트워크, 호스트 폴더, Docker 소켓, 서비스 비밀값을 제공하지 않는다.
- 비루트, 권한 제거, 권한 상승 금지, 읽기 전용 루트와 제한된 임시 작업 공간을 사용한다.
- CPU, 메모리, 프로세스 수, 출력량, 컴파일 시간, 실행 시간, 세션 수명을 제한한다.
- 직접 입력 세션은 사람의 입력 대기를 위한 별도 수명을 사용한다. EOF와 취소를 지원한다.
- 전체 동시 실행 기본 2개. 유한 대기열과 사용자/IP별 요청 제한을 둔다.
- Docker는 완전한 보안 경계가 아니다. 실제 인터넷 서비스 운영 전 별도 실행 호스트/VM 격리를 검토한다.

## 트랜잭션·동시성·성능

- 실행 중 DB 트랜잭션을 유지하지 않는다. 접수와 상태 갱신만 짧은 트랜잭션에서 처리한다.
- 원자적 상태 전이로 중복 실행을 막는다. 케이스 개수 및 스냅샷에는 문제 단위 락을 사용한다.
- 목록은 페이징, 케이스는 일괄 조회하며 반복문 내 DB 조회를 하지 않는다.
- HTTP 오류: 400 입력, 403 권한, 404 대상 없음, 409 채점 불가/삭제 충돌,
  429 요청 제한, 503 실행 환경 장애. 도메인 접두사 J를 사용한다.

## 검증

- 규칙·권한·경계값 단위/MockMvc/H2 테스트 및 전체 build.
- 6개 언어 Docker 실제 실행, EOF, 중단, 정확한 출력 비교, 오류·시간/출력 제한,
  파일/네트워크 격리와 컨테이너 정리를 확인한다.
- H2와 MySQL의 락 동작 차이는 별도로 보고한다. 미실행 검증은 통과로 표시하지 않는다.

## 구현 시 정한 운영 기본값

- 단일 백엔드 인스턴스와 호스트 실행 방식이다. Compose의 app에 Docker 소켓 연결을 추가하지 않는다.
- 컴파일 메모리 768MB·60초, 문제 설정 지원 상한 1024MB·60000ms, 전체 제출의 다음 케이스 시작 전 10분 경과를 확인한다.
- 대기열 16개, IP·회원별 분당 접수 30개, 열린 파일 1024개, 프로세스(스레드 포함) 128개.
- 스냅샷은 `judge_case_snapshots`로 DB 내부 복사하고 8개씩 조회한다. 실행 중 트랜잭션은 없다.
- WebSocket은 기본 localhost:5173/3000 Origin, 첫 인증 10초, 메시지 16KB, 입력 합계 1MB,
  연결 64개·15분 수명으로 제한한다. 출력은 UTF-8 바이트 손실을 막기 위해 Base64로 전달한다.

## PR #38 리뷰 반영 명세

- 리뷰: https://github.com/DSM-Corte/Corte-BE/pull/38#issuecomment-6462000109
- 제출당 한 번만 컴파일한다. 결과물만 새 케이스 컨테이너로 전달하여 파일·프로세스 격리를 유지한다.
  호스트에 결과물을 추출하거나 호스트 폴더를 마운트하지 않는다.
- 직접 실행은 인증된 WebSocket 연결 후에만 대기열에 넣는다. 접수 후 기본 10초 이내 미연결이면 취소한다.
- IP·회원별 동시 접수(대기 포함)는 기본 1개이며 완료·취소·접수 실패 때 반환한다.
- 연결 전 출력 기록을 제거한다. 실행은 연결 이후 시작하고, 연결 후 전송 버퍼·출력 바이트 제한을 유지한다.
- 시작 복구는 준비 단계에서 끝내고 복구 완료 전에 접수하지 않는다.
- 컴파일의 시간·메모리·출력 제한 초과는 COMPILE_ERROR와 해당 사유로 반환한다.
- 실행 시간은 Docker 준비·컴파일·Java/.NET 기본 기동을 제외하고 사용자 진입 신호부터 측정한다.
  사용자 코드의 초기화·라이브러리 로딩·입출력 대기는 실행 시간에 포함한다. 준비에는 별도 유한 제한을 적용한다.
- 승인한 API 확장: 기존 memoryLimitMb는 기본값으로 유지하고, 생성·수정에 선택 Map languageMemoryLimitsMb를 추가한다.
  키는 기존 Language enum, 값은 6~1024MB이다. 해당 언어 설정이 있으면 우선 적용하고 제출 스냅샷에 고정한다.
  null·생략은 유지, 빈 Map은 언어별 설정 제거이다. 기존 클라이언트·기존 문제는 기본값을 계속 사용한다.
- PR 제목·본문도 저장소 형식에 맞춘다. 연결 권한이 없는 경우 사용자가 적용할 내용을 제공하며 로컬 Git 인증을 읽지 않는다.

### 리뷰 요구사항-테스트 대응

| 항목 | 테스트 | 레드·회귀 증거 |
| --- | --- | --- |
| 컴파일 재사용과 케이스 파일 격리 | DockerRunnerTests#한_번_준비한_코드로_여러_케이스를_실행하고_파일은_격리한다; JobManagerTests#여러_케이스를_채점할_때_준비는_한_번만_수행한다 | 최초 compileTestJava: prepare 메서드 없음. 이후 coordinator에서 prepare 1회·execute 2회·close 1회 확인 |
| 연결 전 실행 금지와 출력 기록 제거 | JobManagerTests#직접_실행은_연결하기_전에는_작업자를_차지하지_않는다 | 기존 JobManager로 되돌리면 연결 전 claim이 호출되어 기대 false가 true로 실패 |
| 미연결 만료와 IP별 동시 작업 제한 | JobManagerTests#미연결_직접실행은_만료되고_동일_IP의_자리를_반환한다 | 기존 JobManager에서 두 번째 동일 IP 접수가 거절되지 않아 실패 |
| 대기열 취소의 종료 이벤트와 자리 반환 | JobManagerTests#대기중인_직접실행도_취소하면_종료이벤트와_자리를_반환한다 | 수정 전 이벤트 목록에 exit/CANCELLED가 없어 실패 |
| 복구 전 접수 차단 | JobManagerTests#복구가_끝나기_전에는_작업을_접수하지_않는다 | 기존 JobManager에서 503 전용 예외 대신 접수 경로로 진행하여 실패 |
| 사용자 진입 기준 시간·준비 상한·중복 진입 신호 | ExecutionClockTests의 준비시간 제외·준비상한·반복진입 테스트 | 최초 compileTestJava: ExecutionClock 없음 |
| 종료 후 시간 고정 | ExecutionClockTests#종료후_결과조회와_정리시간을_포함하지_않는다 | 종료 시계 고정을 제거하는 되돌림 검사로 검증 |
| 컴파일 실패의 단계·사유·실행시간 0 | CompilationResultTests#컴파일_자원초과와_실패는_컴파일오류와_사유를_반환한다 | 최초 compileTestJava: asCompilation 없음 |
| 언어별 메모리 선택과 스냅샷 고정 | JobStoreTests#제출_스냅샷에는_선택한_언어의_메모리_제한을_고정한다; MySqlConcurrencyTests | 최초 compileTestJava: languageMemoryLimitsMb builder 메서드 없음 |
| 메모리 등록·유지·교체·초기화·범위·키 | ProblemControllerTests의 언어별 메모리 테스트 2개 | 범위 검증을 제거하는 되돌림 검사로 400 계약 검증 |
| 복구 삭제 실패 차단·준비 중 취소 보존 | DockerRecoveryTests 2개 | 복구 정상 플래그 조건을 되돌리는 검사. 취소 분기는 Docker 프로세스 스텁으로 준비 중 CANCELLED를 재현 |
| 기존 6개 언어 입출력·자원 제한·비회원·비공개 보호 | DockerRunnerTests; JudgeEndToEndTests; 기존 JobStoreTests | 기존 회귀 테스트 유지. 프론트엔드 수정 없음 |
| PR 제목·본문 | GitHub 메타데이터 | 코드 테스트 해당 없음. 연결의 쓰기 권한에 따름 |

되돌림 검사는 위 조건만 잠시 제거하여 실패를 확인한 뒤 수정본으로 즉시 복구한다.
과거 명세의 테스트를 삭제하거나 단언을 약화하지 않는다. 전역 큐 테스트는 새 IP별 한도와 구분되도록
서로 다른 IP 3개를 사용한다. Docker 옵션 테스트의 건너뛰기 조건은 기존 것과 같고,
추가한 재사용 통합 테스트도 실제 Docker 검증에 포함한다.
