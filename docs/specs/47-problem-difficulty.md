# 기능: 문제 난이도 관리

- 이슈: #47 / 브랜치: `feature/#47-problem-difficulty`
- 기준 SHA: `d0eb9e596e6117e62433ba8f6d9c5653e2a24f49` (develop)
- 기존 통합 PR #46에서 난이도 기능을 분리한다. 문제집 #42는 이 기능을 선행 의존성으로 사용한다.

## 목적·API·데이터

- 관리자가 문제 난이도를 선택하고 누구나 문제의 난이도를 확인한다.
- 기존 POST/PATCH `/api/problems`, GET `/api/problems` 및 `/api/problems/{id}`에 difficulty 필드를 추가한다. 기존 상태 코드·권한·응답 필드는 유지한다.
- problems.difficulty: 문자열 enum, NOT NULL, DB 기본값 UNRATED. 기존 문제 데이터는 유지하며 미지정으로 채운다.
- 값은 UNRATED, BRONZE/SILVER/GOLD/PLATINUM/DIAMOND/RUBY 각 5~1이다.
- 생성 시 생략/null은 미지정. 수정 시 생략/null은 유지, UNRATED 명시는 미지정으로 초기화한다.
- 잘못된 값은 C001/400. 쓰기는 기존 ADMIN 권한, 조회는 공개다.
- 기존 Service 트랜잭션과 변경 감지를 사용한다. 별도 동시성 정책은 추가하지 않는다.
- 문제집 CRUD·문제 연결·난이도순 문제집 정렬은 #42 범위이며 이 PR에 포함하지 않는다. 새 컨벤션 없음.

## 테스트 대응

| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 기본값·선택·null 유지·초기화·목록 반영·잘못된 enum | ProblemControllerTests#기본_난이도는_미지정이고_관리자가_선택하거나_초기화한다 | 기존 통합 구현 전 difficulty JSON 경로 없음. 분리 시 동일 단언을 기존 문제 테스트로 이동 |
| DB 기본값 | ProblemRepositoryTests#DB에서_난이도를_생략해도_미지정으로_저장한다 | 보강 테스트의 되돌림 검사는 미실행. 분리 시 같은 SQL·단언 이동 |
| 권한·기존 HTTP 계약 | 기존 ProblemControllerTests | 동작 변경 없음. 기존 테스트 유지 |

## 검증·한계

- 기존 통합 구현의 전체 빌드·CI가 통과했으며, 분리된 브랜치는 별도로 빌드·CI를 확인한다.
- MySQL 실행 및 기존 데이터 컬럼 추가 검증, 되돌림 검사, 독립 리뷰는 미실행이다. 사용자의 기존 push·PR 요청과 이번 분리 요청에 따라 원격 이슈·브랜치·PR도 분리한다.
- 테스트를 삭제하거나 건너뛰지 않고 난이도 테스트의 위치만 문제 도메인으로 옮긴다.
- 분리 후 `./gradlew.bat build --console=plain`: 종료 0, tests=172 skipped=7 failures=0 errors=0. `git diff --check`: 종료 0. 커밋 후에도 빌드를 다시 확인한다.

## PR #49 후속 리뷰 수정 (2026-11-05)

- 기존 숫자 JSON `difficulty: 0`이 BRONZE_5로 저장되는 문제를 재현했다(expected 400, actual 201). 숫자·숫자 문자열·boolean·빈 문자열·배열·객체를 생성/PATCH 양쪽에서 거부한다.
- 필드 전용 DifficultyDeserializer를 사용하며 다른 enum·공통 ObjectMapper는 바꾸지 않는다. 명시적 null·생략은 기존 기본값/유지 규칙을 따른다.
- 추가 테스트: ProblemControllerTests#잘못된_JSON_타입의_난이도는_생성과_수정에서_거부한다, #생성시_선택과_null_기본값을_지원하고_일반_카테고리_목록에도_반영한다.
- 레드: `./gradlew.bat test --tests '*ProblemControllerTests.*JSON*'`, 종료 1, 숫자 입력의 201 응답 확인.
- 수정 후 전체 빌드: 종료 0, tests=174 skipped=7 failures=0 errors=0. 기존 테스트 삭제·건너뛰기·단언 약화 없음.
- 실제 MySQL 8.4: 난이도 컬럼이 없는 기존 problems 테이블과 문제 행을 먼저 만든 뒤 앱을 시작했다. 기존 제목·설명은 유지됐고 난이도는 UNRATED로 채워졌다. 선택 생성·PATCH 생략 유지·카테고리 목록·null 기본값과 모든 잘못된 타입의 400을 실제 API로 확인했다.
- MySQL 명령: `python -u <임시 폴더>/corte-review-smoke.py 49`, 종료 0. 기존 DB가 아닌 작업 전용 임시 컨테이너에서 검증했다.
- 독립 사전 리뷰는 입력 타입과 기존 MySQL 데이터 검증 공백을 지적했으며 둘 다 재현·보완했다. 수정 diff는 커밋 후 독립 재검토한다. 최종 SHA·CI 결과는 PR 및 최종 보고에 기록한다.
