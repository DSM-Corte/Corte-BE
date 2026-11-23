# 관리자 권한 응답과 GitHub 연동 조회

- 이슈: #83
- 기준 SHA: 4f1346af7e77fae477c91aa3ea72c58eb02ac0c7

## 기능: 관리자 권한 응답과 GitHub 연동 조회
- 목적: 프론트 관리자 화면이 권한 없음을 다른 오류와 같은 형식으로 구분하고, 관리자가 자기 GitHub 연동 정보를 확인할 수 있게 한다
- API
  - 권한 없음: 로그인했지만 역할이 맞지 않는 요청(예: 일반 회원의 `POST /api/problems`)은
    403 `{"timestamp", "status": 403, "code": "A004", "message": "접근 권한이 없습니다."}`. 지금은 톰캣 HTML 403 페이지다
  - `GET /api/users/me/github`: 관리자 전용(저장 `PUT`과 같은 권한). 200 `{"installationId", "owner", "repo"}`, 연동 전이면 세 값 모두 `null`
- 데이터: 새 테이블·컬럼 없음. 스키마 영향 없음
- 규칙
  - 비로그인(토큰 없음)은 지금처럼 401 A001
  - 일반 회원의 `GET /api/users/me/github`는 403 A004
- 오류: 새 오류 코드 `A004`(403, "접근 권한이 없습니다.")
- 트랜잭션: GitHub 조회는 읽기 전용 / 동시성: 해당 없음(읽기와 오류 형식만)
- 성능: 회원 1번 조회
- 범위 밖: 비인가 접근 반복 제한, GitHub 연동 해제, 프론트 화면(프론트 저장소에서 따로)
- 새 컨벤션: 없음

## 테스트 대응
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 일반 회원의 관리자 API → 403 JSON `{status, code: A004, message}` | `ProblemControllerTests#일반_사용자는_조회할_수_있지만_생성_수정_삭제할_수_없다`(단언 추가), `UserControllerTests#관리자가_아니면_GitHub_연동_정보를_저장할_수_없다`(단언 추가) | `No value at JSON path "$.status"` / `"$.code"`(본문이 JSON이 아님) |
| 관리자는 저장한 연동 정보를 조회 | `UserControllerTests#관리자는_저장한_GitHub_연동_정보를_조회한다` | `expected 200 but was 405`(엔드포인트 없음) |
| 연동 전이면 세 값 모두 null | `UserControllerTests#연동하지_않았으면_GitHub_연동_정보가_모두_null이다` | `expected 200 but was 405` |
| 일반 회원의 연동 정보 조회 → 403 A004 | `UserControllerTests#관리자가_아니면_GitHub_연동_정보를_조회할_수_없다` | `expected 403 but was 405` |
| 토큰 없이 조회 → 401 A001 | `UserControllerTests#토큰_없이_GitHub_연동_정보를_조회하면_401을_준다` | 보안 설정이 이미 막아서 구현 전부터 통과(레드 없음) |
| 서비스: 회원이 있으면 연동 정보, 없으면 401 | `GithubLinkServiceTests#회원이_있으면_저장된_연동_정보를_준다`, `#회원이_없으면_연동_정보_조회에_실패한다` | 컴파일 실패: `GithubLinkService.get` 없음 |
| 트랜잭션·동시성 | 해당 없음: 읽기와 오류 형식만 바뀐다 | 해당 없음 |
