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
| 고른 적 없으면 LIGHT·BLUE | `ThemeServiceTests.테마를_고른_적이_없으면_LIGHT와_BLUE를_준다`, `UserControllerTests.테마를_고른_적이_없으면_LIGHT와_BLUE를_준다` | 서비스: `ThemeService`·`ThemeResponse` 없음 컴파일 실패 / HTTP: 엔드포인트 없음 404 |
| 고른 테마 조회 | `ThemeServiceTests.고른_테마가_있으면_그_테마를_준다` | 서비스 컴파일 실패(위와 같음) |
| PATCH 200, 바뀐 값 유지 | `UserControllerTests.테마를_바꾸면_다음_조회에도_유지된다` | 엔드포인트 없음 404 |
| null은 변경 없음(모드만·강조색만) | `ThemeServiceTests.모드만_보내면_강조색은_그대로다`, `ThemeServiceTests.강조색만_보내면_모드는_그대로다` | 서비스 컴파일 실패 |
| 둘 다 없음 400 `C001` | `ThemeServiceTests.모드와_강조색이_둘_다_없으면_입력_오류`, `UserControllerTests.바꿀_값이_없으면_400을_준다` | 서비스 컴파일 실패 / HTTP: 400 기대에 404 |
| 목록에 없는 값 400 `C001` | `UserControllerTests.목록에_없는_강조색이면_400을_준다` | 400 기대에 404 |
| 회원 없음 401 `A001` | `ThemeServiceTests.조회할_때_회원이_없으면_인증_실패`, `ThemeServiceTests.변경할_때_회원이_없으면_인증_실패` | 서비스 컴파일 실패 |
| 토큰 없음 401 `A001` | `UserControllerTests.토큰_없이_테마를_조회하면_401을_준다` | 보안 설정이 이미 막아서 구현 전부터 통과(레드 없음) |
