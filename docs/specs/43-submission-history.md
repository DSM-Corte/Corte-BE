# 채점 기록 조회

- 이슈: #43
- 기준 SHA: d0eb9e596e6117e62433ba8f6d9c5653e2a24f49

## 기능: 채점 기록 조회
- 목적: 로그인한 회원이 자신이 제출한 풀이의 채점 기록을 목록으로 보고, 제출했던 코드를 다시 확인한다
- API
  - `GET /api/submissions?page=0&size=20&problemId={선택}` → 200, 인증 필요(토큰 없으면 401 `A001`)
    ```json
    {"content":[{"id":7,"problemId":1,"problemTitle":"합","language":"PYTHON","verdict":"ACCEPTED",
      "passedCount":3,"totalCount":3,"runtimeMs":40,"createdAt":"2026-11-03T10:00:00"}],
     "page":0,"size":20,"totalElements":1,"totalPages":1}
    ```
  - `GET /api/submissions/{id}`, `GET /api/executions/{id}`(기존) 응답에 `problemId`, `language`, `code`, `createdAt`을 추가한다.
    기존 필드와 권한 규칙은 그대로다. 생성(`POST`) 응답은 값을 채우지 않아 새 키 4개가 `null`로 붙는다
    (기존 `message`·`stdout`이 `null`로 나가는 것과 같은 방식. 독립 리뷰 지적에 따라 문구를 실제 동작에 맞췄다)
- 데이터: 새 테이블·컬럼 없음. 기존 `judge_jobs`를 읽기만 한다. 스키마 영향 없음
- 규칙
  - 목록에는 요청한 회원 본인의 제출(`submission = true`, `author_id = 본인`)만 나온다. 직접 실행과 비회원 제출은 나오지 않는다
  - 채점 중(QUEUED, RUNNING)인 제출도 나온다
  - `problemId`를 주면 그 문제의 제출만, 생략하면 전체. 없는 문제 ID는 빈 목록이다
  - 페이지: `page` 0 이상, `size` 1~100(기본 20). 벗어나면 400 `C001`. 정렬은 ID 내림차순(최신순)
  - 목록에는 코드를 넣지 않는다. 코드는 단건 조회에서 본다
- 오류: 토큰 없음 → 401 `A001` / 페이지 범위 벗어남 → 400 `C001`. 새 오류 코드 없음
- 트랜잭션: 읽기 전용 / 동시성: 없음(조회만) / 성능: 문제 제목은 join으로 한 번에 projection 조회, `countQuery` 분리, 페이징 적용
- 범위 밖: 다른 회원·문제별 공개 채점 현황, 관리자 전체 조회, 판정별 필터, 푼 문제 통계·점수
- 새 컨벤션: 없음

## 테스트 대응
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 본인 제출만, 직접 실행·타인·비회원 제외, 최신순, 문제 제목 포함 | JudgeJobRepositoryTests#제출_목록은_본인의_제출만_문제_제목과_함께_최신순으로_가져온다 | 컴파일 실패: JudgeJobRepository.findSummaries·Summary 없음 |
| problemId 필터 | JudgeJobRepositoryTests#문제_ID를_주면_그_문제의_제출만_가져온다 | 컴파일 실패: findSummaries 없음 |
| 페이지 범위(page<0, size>100) → InvalidInputException | JobStoreTests#제출_목록_페이지_크기가_범위를_벗어나면_거절한다 | 컴파일 실패: JobStore.list 없음 |
| 단건 조회에 problemId·language·code·createdAt | JobStoreTests#제출_단건_조회는_문제_언어_코드_제출시각을_함께_반환한다 | 컴파일 실패: JobResponse.problemId() 없음 |
| GET /api/submissions 200, 페이지 응답 모양, 목록에 code 없음 | SubmissionApiTests#내_제출_목록을_최신순으로_반환한다 | Status expected:<200> but was:<405> |
| 페이지 범위 → 400 C001 | SubmissionApiTests#페이지_크기가_100을_넘으면_400_C001을_반환한다 | Status expected:<400> but was:<405> |
| 토큰 없음 → 401 A001 | SubmissionApiTests#토큰_없이_제출_목록을_조회하면_401_A001을_반환한다 | 레드 없음: 기존 보안 설정(anyRequest().authenticated())이 이미 막는다. 회귀 방지용 |
| 단건 조회 HTTP 응답에 code·language | SubmissionApiTests#제출_단건_조회는_코드와_언어를_함께_반환한다 | No value at JSON path "$.problemId" |
| 없는 problemId → 빈 목록 | JudgeJobRepositoryTests#문제_ID를_주면_그_문제의_제출만_가져온다 | 레드 없음: 리뷰 후 보강한 단언(구현 후 추가) |
| size 하한 0 거절, 상한 100 허용 | JobStoreTests#제출_목록_페이지_크기가_범위를_벗어나면_거절한다 | 레드 없음: 리뷰 후 보강한 단언(구현 후 추가) |
| 직접 실행 단건 조회에도 problemId·language·code | ExecutionApiTests#비회원_실행은_조회주소와_토큰을_반환하고_출력을_조회한다 | 레드 없음: 리뷰 후 보강한 단언(구현 후 추가) |
