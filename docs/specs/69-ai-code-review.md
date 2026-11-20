# 제출 AI 코드 리뷰

- 이슈: #69
- 기준 SHA: aefc8113b6f722f46157a1f6b447931d88466045

## 기능: 제출 AI 코드 리뷰
- 목적: 풀이를 제출한 회원이 자기 제출 코드에 대한 AI 리뷰(요약, 줄 코멘트, 개선 제안)를 받아 성장한다.
  FE가 키를 들고 Gemini를 직접 부르던 것을 BE로 옮긴다(FE 연동은 범위 밖)
- API (모두 로그인 필요)

| API | 설명 | 성공 |
| --- | --- | --- |
| `POST /api/submissions/{id}/ai-review` | 본인 제출(`submission=true`)에 Gemini 리뷰를 생성·저장. 요청 본문 없음 | 201 + Location(`/api/submissions/{id}/ai-review`) + 본문 |
| `GET /api/submissions/{id}/ai-review` | 저장된 리뷰 조회 | 200 + 본문 |

- 응답: `{id, submissionId, model, summary, comments:[{line, body}], suggestion:{title, description, code}, createdAt}`
- 데이터 (신규 테이블 `ai_reviews`, 기존 데이터 영향 없음. `ddl-auto: update`가 새 테이블만 만든다)
  - `AiReview`: submission(`JudgeJob`, ManyToOne LAZY, 유니크 `uk_ai_reviews_submission_id`), model(50), summary(`@Lob`), comments(`@Lob`, JSON 배열 문자열), suggestion(`@Lob`, JSON 객체 문자열), createdAt, updatedAt
- 규칙
  - 제출이 없거나 `submission=false`(실행)이면 404, 작성자가 아니면 403(기존 `JudgeNotFoundException`·`JudgeForbiddenException` 재사용)
  - 제출당 AI 리뷰 1개. 이미 있으면 409. 재생성 없음
  - 프롬프트·요청 형식은 FE `GeminiApi.js`를 따른다(JSON 응답, temperature 0.25). 줄 코멘트는 최대 4개, 줄 번호는 1~코드 줄 수로 보정, 빈 본문 코멘트는 버린다
  - 모델은 `GEMINI_MODEL`(기본 `gemini-2.5-flash`) 하나만 쓴다(폴백 모델 없음)
  - `GEMINI_API_KEY`가 비었으면 외부 호출 없이 503 `I003`
  - 외부 호출은 DB 트랜잭션 밖에서 한다(연결을 붙들지 않는다)
- 오류(접두사 `I`): AI 리뷰 없음 I001 404, 이미 생성됨 I002 409, 키 미설정 I003 503, Gemini 호출 실패 I004 502, 응답 해석 실패 I005 502
- 트랜잭션: 조회·저장을 각각 짧은 트랜잭션(`AiReviewStore`)으로, 외부 호출은 트랜잭션 없는 `AiReviewService`에서
- 동시성: `submission_id` 유니크 제약(`saveAndFlush`, 유니크 위반만 I002로 변환). 사전 조회로 대부분의 중복 호출은 외부 호출 전에 막지만,
  동시 요청이 둘 다 통과하면 둘 다 Gemini를 부르고 늦게 저장하는 쪽이 409를 받는다(호출 비용 중복은 허용)
- 성능: 단건 조회만, 목록 없음
- 기술 선택: Spring Cloud OpenFeign(`GeminiClient`, `gemini.api-url`로 주소 변경 가능), 설정은 `@ConfigurationProperties` record
- 범위 밖: FE 연동, 재생성, 비동기 생성, 폴백 모델, 사용량 제한, 리뷰 삭제, 비회원
- 새 컨벤션: 오류 접두사 `I`(AI 리뷰)를 `src/main/AGENTS.md` 목록에 추가

## 테스트 대응
| 명세 항목 | 테스트 |
| --- | --- |
| 생성 성공·저장·조회 200/201 | `AiReviewApiTests` |
| 줄 코멘트 보정(최대 4개, 줄 범위, 빈 본문), 기본값, 요청 형식 | `GeminiReviewerTests` |
| 키 미설정 503 I003(외부 호출 없음) | `AiReviewNotConfiguredApiTests`, `GeminiReviewerTests` |
| 중복 409 I002, 유니크 제약 | `AiReviewApiTests` |
| 남의 제출 403, 없는 제출·실행 404, 리뷰 없음 404 I001, 토큰 없음 401 | `AiReviewApiTests` |
| 호출 실패 502 I004, 해석 실패 502 I005 | `GeminiReviewerTests`, `AiReviewApiTests` |
