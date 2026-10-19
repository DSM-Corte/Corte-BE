# 문제 API

문제 데이터의 생성, 상세·목록 조회, 부분 수정, 삭제를 제공한다.
화면, 회원 인증, 채점, 비공개 테스트 데이터는 이 구현의 범위에 포함하지 않는다.

## 데이터와 검증

| 필드 | 생성 시 필수 | 최대 길이 | 규칙 |
| --- | --- | --- | --- |
| title | 예 | 200 | 공백만으로 구성할 수 없음 |
| description | 예 | 20,000 | 공백만으로 구성할 수 없음 |
| inputFormat | 예 | 10,000 | 공백만으로 구성할 수 없음 |
| outputFormat | 예 | 10,000 | 공백만으로 구성할 수 없음 |
| exampleInput | 예 | 10,000 | 빈 문자열 허용 |
| exampleOutput | 예 | 10,000 | 빈 문자열 허용 |

예제 입출력의 공백과 줄바꿈은 그대로 저장한다. 예제는 공개용이다.
필드와 길이 제한은 초기 구현을 위한 기본값이며, 제품 요구사항에 맞춰 조정할 수 있다.

## 생성

`POST /api/problems`

요청 (`Content-Type: application/json`):

```json
{
  "title": "두 수의 합",
  "description": "두 정수의 합을 구하세요.",
  "inputFormat": "두 정수 A와 B가 주어진다.",
  "outputFormat": "A + B를 출력한다.",
  "exampleInput": "1 2\n",
  "exampleOutput": "3\n"
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
  "inputFormat": "두 정수 A와 B가 주어진다.",
  "outputFormat": "A + B를 출력한다.",
  "exampleInput": "1 2\n",
  "exampleOutput": "3\n"
}
```

## 목록 조회

`GET /api/problems?page=0&size=20` → `200 OK`

페이지는 0부터 시작한다. 기본 크기는 20이고 1~100을 허용한다.
ID 내림차순으로 정렬하며, 본문과 예제는 조회하거나 반환하지 않는다.

```json
{
  "content": [{ "id": 1, "title": "두 수의 합" }],
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
  "exampleInput": ""
}
```

전달하지 않은 필드와 `null`은 기존 값을 유지한다.
예제 필드의 빈 문자열은 내용을 비운다. 다른 필드의 빈 문자열·공백은 거절한다.
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

## 학습 확인

`ProblemController → ProblemService → ProblemRepository → Problem` 흐름을 따라가 보자.
그다음 수정 요청에서 `title`을 생략했을 때와 빈 문자열로 보냈을 때를 비교하고,
제목 길이 제한 하나를 직접 바꿔 테스트 결과를 확인해 보자.
