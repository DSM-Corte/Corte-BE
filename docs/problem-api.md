# 문제 API

문제 데이터의 생성, 상세·목록 조회, 부분 수정, 삭제를 제공한다.
화면, 회원 인증, 채점, 입출력 형식, 예제 입출력, 테스트케이스는 이 구현의 범위에 포함하지 않는다.

## 데이터와 검증

| 필드 | 생성 시 필수 | 제한 | 규칙 |
| --- | --- | --- | --- |
| title | 예 | 200 | 공백만으로 구성할 수 없음 |
| description | 예 | 20,000 | 공백만으로 구성할 수 없음 |
| category | 예 | 50자 | 공백만으로 구성할 수 없음 |
| memoryLimitMb | 예 | 양의 정수 | 메모리 제한, MB 단위 |
| timeLimitMs | 예 | 양의 정수 | 시간 제한, ms 단위 |

카테고리는 문제당 문자열 하나로 저장한다. 메모리·시간 제한은 문제의 설정값이며,
실제 코드 실행이나 제한 초과 판정은 채점 기능에서 구현해야 한다.
입출력 필드는 제거했으며 테스트케이스 요구사항이 정해지면 별도로 설계한다.

## 생성

`POST /api/problems`

요청 (`Content-Type: application/json`):

```json
{
  "title": "두 수의 합",
  "description": "두 정수의 합을 구하세요.",
  "category": "구현",
  "memoryLimitMb": 256,
  "timeLimitMs": 1000
}
```

응답: `201 Created`, `Location: /api/problems/{id}`.
아래의 상세 응답과 같은 JSON을 반환한다.

## 상세 조회

`GET /api/problems/{id}` → `200 OK`

```json
{
  "id": 1,
  "title": "두 수의 합",
  "description": "두 정수의 합을 구하세요.",
  "category": "구현",
  "memoryLimitMb": 256,
  "timeLimitMs": 1000
}
```

## 목록 조회

`GET /api/problems?page=0&size=20` → `200 OK`

페이지는 0부터 시작한다. 기본 크기는 20이고 1~100을 허용한다.
ID 내림차순으로 정렬하며, ID·제목·카테고리만 조회하고 반환한다.
`GET /api/problems?category=구현&page=0&size=20`처럼 카테고리를 지정하면
해당 문자열과 일치하는 문제만 조회한다. 카테고리를 생략하면 전체를 조회한다.
카테고리는 최대 50자이며 공백만으로 구성할 수 없다.

```json
{
  "content": [{ "id": 1, "title": "두 수의 합", "category": "구현" }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## 부분 수정

`PATCH /api/problems/{id}` → `200 OK`

```json
{
  "title": "수정한 제목",
  "category": "그래프",
  "memoryLimitMb": 512,
  "timeLimitMs": 2000
}
```

전달하지 않은 필드와 `null`은 기존 값을 유지한다.
문자열 필드의 빈 문자열·공백과 메모리·시간 제한의 0 이하 값은 거절한다.
빈 객체 `{}`는 아무 필드도 변경하지 않는다. 응답은 수정된 문제의 상세 JSON이다.

## 삭제

`DELETE /api/problems/{id}` → `204 No Content`

DB에서 실제로 삭제한다. 없는 ID를 조회·수정·삭제하면 `404`, 오류 코드 `C004`를 반환한다.
입력 검증 실패·잘못된 JSON·잘못된 페이지 인자는 `400`, 오류 코드 `C001`을 반환한다.

## 현재 정책과 검증 범위

- 인증 기능이 없어 모든 API를 인증 없이 호출할 수 있다. 작성자·관리자 권한 제한은 구현하지 않았다.
- 제출 기록 등 연결된 도메인은 아직 없다. 연결된 데이터의 보존 정책은 별도로 정해야 한다.
- 생성·수정·삭제는 각각 트랜잭션으로 처리한다. 버전 검사나 명시적 락은 추가하지 않아,
  동시 수정 시 나중에 저장된 변경이 앞선 변경을 덮어쓸 수 있다.
- Service 단위 테스트, MockMvc HTTP 테스트, H2 JPA 테스트를 제공한다.
  H2 테스트는 MySQL의 SQL·락·격리 수준과 완전히 같음을 보장하지 않는다.
- 상세·목록 조회 Service 메서드에 `@Transactional(readOnly = true)`를 명시한다.
- 문제 Entity와 응답 DTO는 Lombok Builder로 생성하고 DTO 변환은 Service에서 수행한다.
- 카테고리·ID 복합 인덱스를 선언했다. 실제 MySQL 실행 계획과 성능은 별도 검증이 필요하다.

## 기존 DB 적용 시 주의

현재 테스트는 H2의 `create-drop`으로 새 스키마를 생성한다.
기존 MySQL `problems` 테이블에 데이터가 있다면 카테고리·메모리·시간 제한을 먼저 채우고
필수 제약을 적용하는 마이그레이션이 필요하다. `ddl-auto: update`만으로 기존 입출력 컬럼이
삭제되는 것은 보장되지 않으며, 남아 있는 NOT NULL 입출력 컬럼은 새 문제 저장을 막을 수 있다.
운영 DB 변경·데이터 삭제는 이 코드 작업에서 실행하지 않았다.

## 학습 확인

`ProblemController → ProblemService → ProblemRepository → Problem` 흐름을 따라가 보자.
그다음 수정 요청에서 `title`을 생략했을 때와 빈 문자열로 보냈을 때를 비교하고,
제목 길이 제한 하나를 직접 바꿔 테스트 결과를 확인해 보자.
