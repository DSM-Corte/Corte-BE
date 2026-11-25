# 로그인 상태 유지 토큰 (#89)

기준 SHA: `784aa88`

## 기능: 로그인 상태 유지를 켜면 긴 토큰 발급
- 목적: 회원이 "로그인 상태 유지"를 켜고 로그인하면 30분마다 다시 로그인하지 않고 7일 동안 사이트를 쓴다
- API: `POST /auth/login` 요청에 선택 필드 `rememberMe`(boolean)를 추가한다. 응답은 그대로 `200` + `{accessToken}`
  - `rememberMe: true` → 만료 7일(`jwt.remember-expiration`, 환경 변수 `JWT_REMEMBER_EXPIRATION`, 기본 `7d`)
  - `false` 또는 생략 → 지금처럼 `jwt.access-expiration`(기본 30분)
- 데이터: 없음. 리프레시 토큰은 계속 두지 않는다
- 규칙: 토큰 내용(subject, `role`)은 같고 만료 시각만 다르다. 관리자(ADMIN)는 `rememberMe`와 상관없이 항상 짧은 토큰(30분)을 받는다. 권한이 토큰 `role`로 정해져서 `ADMIN_EMAILS`에서 빼도 기존 토큰 만료 전까지 권한이 남기 때문이다(팀 결정)
- 오류: 기존과 같다(`U002` 401, 검증 오류 `C001` 400)
- 보안: 토큰을 도난당하면 최대 7일 동안 쓸 수 있다. 사용자가 직접 켠 경우에만 긴 토큰을 준다
- 트랜잭션 / 동시성: 없음 / 성능: 없음
- 범위 밖: 리프레시 토큰, 토큰 폐기(로그아웃 시 서버 무효화)
- 재현 조건: `rememberMe: true`로 로그인해도 토큰 만료가 발급 30분 뒤다

## 테스트 대응

| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| `rememberMe: true`면 7일 토큰 | `AuthControllerTests.로그인_상태_유지를_켜면_7일_토큰을_받는다` | 단언 실패: expected 604800 but was 1800 (항상 30분 토큰) |
| `false`·생략이면 30분 토큰 | `AuthControllerTests.로그인_상태_유지를_끄거나_빼면_30분_토큰을_받는다` | 구현 전에도 통과(기존 동작 보호용 회귀 테스트) |
| 관리자는 `rememberMe`여도 30분 | `AuthControllerTests.관리자는_로그인_상태_유지를_켜도_30분_토큰을_받는다` | 단언 실패: expected 1800 but was 604800 |
