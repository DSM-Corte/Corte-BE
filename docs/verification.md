# 검증 절차

루트 `AGENTS.md`의 4단계·5단계와 Verification Checklist가 이 문서를 따른다.
에이전트의 "완료했다"는 말은 증거가 아니다. 아래 증거를 남긴 항목만 통과로 본다.

## 1. 증거 형식

보고서의 Checklist 결과는 항목마다 아래 표로 쓴다.

| 항목 | 실행한 명령 | 종료 코드 | 결과 | 실행 시점 SHA |
| --- | --- | --- | --- | --- |
| 빌드 | `./gradlew build` | 0 | tests=101 skipped=0 failures=0 errors=0 | `a1b2c3d` |

- SHA는 `git rev-parse --short HEAD`. 마지막 수정 뒤의 HEAD와 다르면 그 결과는 무효이고 다시 실행한다.
- 테스트 수는 빌드 후 아래 명령으로 센다. `skipped`가 기준 빌드보다 늘었으면 이유를 적는다.
  ```
  awk -F'"' '/<testsuite /{for(i=1;i<NF;i++){if($i~/ tests=$/)t+=$(i+1);if($i~/ skipped=$/)s+=$(i+1);if($i~/ failures=$/)f+=$(i+1);if($i~/ errors=$/)e+=$(i+1)}} END{print "tests="t, "skipped="s, "failures="f, "errors="e}' build/test-results/test/*.xml
  ```
- 실행하지 못한 항목은 결과에 "미실행"과 이유를 적는다. 통과로 쓰지 않는다.

## 2. 명세-테스트 대응표

- 명세 파일(`docs/specs/<번호>-<설명>.md`)에 "테스트 대응" 절을 두고, 명세의 규칙·경계값·오류 코드·HTTP 계약마다
  그것을 검증하는 테스트를 `클래스#메서드`로 적는다. 3단계에서 테스트를 쓸 때마다 채우고 구현 커밋과 함께 커밋한다.
- 칸마다 레드 증거를 한 줄 적는다: 구현 전에 그 테스트를 돌렸을 때의 실패 이유(예: `컴파일 실패: NoteService 없음`,
  `expected 404 but was 200`). 실패 이유가 "기능이 없어서"가 아니면 테스트를 고친다.
- 빈 칸이 하나라도 있으면 실패다. 테스트할 동작이 없는 항목은 "해당 없음"과 이유를 적는다.

```
## 테스트 대응
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 작성자가 아니면 403 N002 | NoteServiceTests#작성자가_아니면_수정할_수_없다 | 예외가 발생하지 않음 |
```

## 3. 테스트 속이기 검사

`git diff <기준SHA>...HEAD -- src/test`를 직접 읽고 아래가 있는지 확인한다. 있으면 명세에 근거가 있어야 하고, 없으면 실패다.

- 삭제된 테스트 파일·메서드: `git diff --diff-filter=D --name-only <기준SHA>...HEAD -- src/test`
- 추가된 `@Disabled`, `assumeTrue` 같은 건너뛰기, 삭제되거나 약해진 단언(예: `isEqualTo` → `isNotNull`), 바뀐 기대 상태 코드·오류 코드:
  `git diff <기준SHA>...HEAD -- src/test | grep -nE '^\+.*(@Disabled|assume)|^-.*(assert|then\(|status\(\)|isEqualTo)'`

버그 수정이나 기존 동작 변경이면 되돌림 검사도 한다. 기능 코드만 되돌렸을 때 새 테스트가 실패해야 한다.
작업 트리가 깨끗한 상태(모두 커밋)에서 실행한다.

```
git diff <기준SHA> HEAD -- src/main | git apply -R
./gradlew test                                   # 실패해야 한다. 통과하면 그 테스트는 변경을 검증하지 못한다
git checkout HEAD -- src/main && git clean -fdq src/main
git status --short                               # 비어 있어야 한다
```

## 4. 실행 검증 (API를 추가하거나 바꿨을 때)

테스트(H2, MockMvc) 말고 실제 MySQL에 앱을 띄워 새 API를 호출한다.

```
docker compose up -d --wait db
export JWT_SECRET=$(head -c 32 /dev/urandom | base64)
./gradlew bootRun > <임시 폴더>/boot.log 2>&1 &
for i in $(seq 1 90); do curl -s -o /dev/null localhost:8080/v3/api-docs && break; sleep 2; done
```

- 명세의 성공 경로 1개와 오류 경로 1개 이상을 `curl -s -w "\n%{http_code}\n"`로 호출하고, 상태 코드와 본문을 증거로 남긴다.
- 인증이 필요하면 `POST /auth/signup` → `POST /auth/login`의 `accessToken`을 `Authorization: Bearer`로 보낸다.
  DB 볼륨이 남아 있으므로 이메일은 매번 새로 만든다(`smoke$RANDOM@test.com`).
  ADMIN 토큰을 발급하는 경로는 없으므로 ADMIN 전용 API는 "미실행: ADMIN 토큰 발급 경로 없음"으로 적는다.
- 끝나면 앱을 종료하고(`kill %1`, 남아 있으면 `pkill -f bootRun`) `docker compose stop db`로 DB를 멈춘다.
- Docker가 없거나 8080 포트가 사용 중이면 멈추고 사용자에게 알린다(환경 문제).

## 5. 독립 리뷰 (push 전)

구현한 맥락을 모르는 새 리뷰어에게 diff를 검토받는다.

- 리뷰어: Claude Code는 서브에이전트, Codex는 새 세션(`codex exec -c 'sandbox_mode="read-only"' "<요청>"`).
  둘 다 쓸 수 있으면 구현한 도구와 다른 도구로 리뷰한다. 같은 모델은 같은 실수를 놓치기 쉽다.
- 리뷰어에게는 명세 파일 경로, `git diff <기준SHA>...HEAD`, 저장소의 AGENTS.md들만 준다. 구현자의 설명이나 결론은 주지 않는다.
- 요청할 내용: 명세 항목별 충족 여부, 명세에 없는 변경, 테스트 속이기 의심, 버그 의심을 `항목 | 파일:줄 | 근거` 표로 받는다.
- 리뷰 결과는 판정이 아니라 확인할 목록이다. 지적마다 코드로 확인해 고치거나, 고치지 않는 이유를 적는다.
  코드를 고쳤으면 빌드부터 다시 검증한다. 지적과 처리 결과를 보고서에 남긴다.

## 6. CI

- `.github/workflows/ci.yml`이 develop·main 대상 PR마다 JDK 21로 `./gradlew build`를 실행한다.
- PR을 올린 뒤 `gh pr checks <PR번호> --watch`로 결과를 기다린다. 실패하면 원인을 고쳐 같은 브랜치에 push하고 다시 확인한다.
- 이번 변경과 무관한 원인(러너 장애 등)으로 실패하면 고치지 말고 보고한다. CI 결과는 6단계 보고에 포함한다.
