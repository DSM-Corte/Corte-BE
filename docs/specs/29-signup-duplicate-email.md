# 회원가입 이메일 중복 판별 수정

- 이슈: #29
- 기준 SHA: f2098c0df134f4a2d7d5a71965b36dc2160cc061

## 기능: 회원가입 이메일 중복 판별
- 목적: 동시에 같은 이메일로 가입해도 409로 막고, 이메일 중복이 아닌 DB 오류를 이메일 중복으로 숨기지 않는다
- API: 변경 없음 (`POST /auth/signup`)
- 데이터: `users.email` 유니크 제약에 이름 `uk_users_email`을 붙인다
  - 스키마 영향: `ddl-auto: update`로 이미 만들어진 DB에는 이름 없는 기존 유니크 인덱스가 남고 `uk_users_email`이 추가된다
    기존 인덱스가 먼저 걸리면 동시 가입에서 500이 날 수 있어 기존 DB는 이름 없는 email 유니크 인덱스를 지워야 한다
- 규칙: 저장은 `saveAndFlush`로 한다. `DataIntegrityViolationException`의 원인이 UNIQUE 종류이고 제약 이름이 `uk_users_email`일 때만
  `DuplicateEmailException`(409 U001)으로 바꾸고 나머지는 그대로 전파한다
- 오류: 이메일 중복 → U001 / 409 (변경 없음)
- 트랜잭션: 변경 없음 / 동시성: 유니크 제약 / 성능: 해당 없음
- 범위 밖: `AuthService` 트랜잭션 어노테이션, `LoginRequest` record 전환
