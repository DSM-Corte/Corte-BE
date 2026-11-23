# 다른 사용자 프로필 조회

- 이슈: #81
- 기준 SHA: 4f1346af7e77fae477c91aa3ea72c58eb02ac0c7

## 기능: 다른 사용자 프로필 조회
- 목적: 리더보드 등에서 다른 사용자를 눌렀을 때 그 사람의 실제 닉네임과 풀이 통계를 보여준다.
  지금 프론트 프로필 화면은 브라우저 저장소의 가짜 데이터라 실제 회원은 "찾을 수 없음"이 나온다
- API: `GET /api/users/{userId}/profile`, 누구나 조회(공개 경로)
  - 성공 200: `{"userId": 3, "nickname": "민수", "stats": { ...GET /api/users/me/stats와 같은 모양... }}`
  - 이메일은 응답에 넣지 않는다
- 데이터: 새 테이블·컬럼 없음. `users`, `judge_jobs`, `problems`, `problem_tags`를 읽기만 한다. 스키마 영향 없음
- 규칙
  - `stats`는 내 통계(#57)와 같은 기준이다. 기존 `UserStatsService` 계산을 그대로 쓴다
- 오류
  - 없는 회원 → U003 / 404 "회원을 찾을 수 없습니다."
  - 숫자가 아닌 `userId` → C001 / 400
- 트랜잭션: 읽기 전용 / 동시성: 해당 없음(읽기만)
- 성능: 회원 1번 조회 + 기존 통계 조회(제출 projection 1번, 태그 1번)
- 범위 밖: 프로필의 티어·점수·순위·잔디, 프론트 화면(프론트 저장소에서 따로)
- 새 컨벤션: 없음

## 테스트 대응
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 회원이 있으면 닉네임과 그 회원의 통계(내 통계와 같은 계산) | `UserProfileServiceTests#회원이_있으면_닉네임과_그_회원의_통계를_준다` | 컴파일 실패: `UserProfileService`·`UserProfileResponse` 없음 |
| 없는 회원이면 실패, 통계 계산 안 함 | `UserProfileServiceTests#회원이_없으면_프로필_조회에_실패한다` | 컴파일 실패: `UserNotFoundException` 없음 |
| API 200 응답(userId, nickname, stats, 이메일 없음), 토큰 없이 공개 조회 | `UserControllerTests#다른_회원의_프로필을_토큰_없이_조회한다` | `expected 200 but was 401`(공개 경로·엔드포인트 없음) |
| 없는 회원 → 404 U003 | `UserControllerTests#없는_회원의_프로필을_조회하면_404_U003을_준다` | `expected 404 but was 401` |
| 숫자가 아닌 userId → 400 C001 | `UserControllerTests#회원_ID가_숫자가_아니면_프로필_조회에_400_C001을_준다` | `expected 400 but was 401` |
| 트랜잭션·동시성 | 해당 없음: 읽기 전용 조회만 한다 | 해당 없음 |
