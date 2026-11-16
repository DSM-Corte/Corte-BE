# 테마 설정 저장

- 이슈: #63
- 기준 SHA: 766aa161489bc4042c935ca3cedff961b6daa99a

## 기능: 테마 설정 저장
- 목적: 로그인한 회원이 고른 테마(라이트·다크 모드 + 강조색)를 서버에 저장해 다른 컴퓨터나 브라우저에서 로그인해도 같은 테마로 보이게 한다
- API (모두 로그인 필요, 토큰 없으면 401 `A001`)
  - `GET /api/users/me/theme` → 200 `{"mode":"DARK","color":"PINK"}`
  - `PATCH /api/users/me/theme` 요청 `{"mode":"DARK"}` / `{"color":"PINK"}` / 둘 다 → 200 `{"mode":"DARK","color":"PINK"}`(바뀐 뒤 전체 값)
- 데이터: `users`에 `theme_mode`, `theme_color` 컬럼 추가(enum 문자열, length 20, 빈 값 허용)
  - 스키마 영향: `ddl-auto: update`로 빈 값 허용 컬럼만 추가된다. 기존 회원 데이터는 그대로다
- 규칙
  - mode: `LIGHT`, `DARK`
  - color: `RED`, `ORANGE`, `YELLOW`, `GREEN`, `BLUE`, `INDIGO`, `VIOLET`, `PINK`
  - 한 번도 고르지 않았으면(빈 값) mode는 `LIGHT`, color는 `BLUE`로 응답한다
  - PATCH에서 빠진(`null`) 값은 바꾸지 않는다
- 오류
  - mode·color가 둘 다 없음 → 400 `C001`
  - 목록에 없는 값(예: `"PURPLE"`) → 400 `C001`
  - 토큰 없음 → 401 `A001`
  - 토큰은 유효한데 회원이 없음 → 401 `A001`
- 트랜잭션: 조회 readOnly, 변경은 쓰기 트랜잭션 / 동시성: 없음(본인 설정이라 마지막 요청이 이긴다) / 성능: 회원 1건 조회
- 범위 밖: 비로그인 테마(프론트 브라우저 저장 유지), 프론트 화면 변경, SYSTEM(기기 설정 따름) 모드, 내 정보 API

## 테스트 대응
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
