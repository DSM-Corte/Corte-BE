# 게시글·댓글 작성자 ID

- 이슈: #92
- 기준 SHA: ab718be5e5934731b1d466e3cb22a155742aee4d

## 기능: 게시글·댓글 응답에 작성자 ID(authorId) 추가
- 목적: FE가 응답의 `authorId`를 `getCurrentUserId()`(토큰 `sub`)와 비교해 작성자에게만 수정·삭제 버튼을 보여준다
- API (필드 추가만, 경로·상태 코드·인증은 그대로)
  - 게시글 응답(`PostResponse`)에 `authorId`(Long) 추가: `POST /api/posts`(201), `GET /api/posts/{id}`, `PATCH /api/posts/{id}`
  - 댓글 응답에 `authorId`(Long) 추가: `POST`(201)·`GET`(목록 항목)·`PATCH /api/posts/{postId}/comments/...`
  - 기존 `author`(닉네임) 필드는 그대로 둔다
  - 게시글 목록 요약(`PostPageResponse.Summary`)은 바꾸지 않는다
- 데이터: 스키마 변경 없음. 작성자 `User.id`를 그대로 내려준다
- 규칙: `authorId` == 작성자 `User.id` == access token `sub`
- 오류: 새 오류 없음
- 트랜잭션: 기존과 같음 / 동시성: 없음(응답 필드 추가) / 성능: 추가 조회 없음(댓글 목록은 기존 작성자 join에서 `id`를 함께 조회)
- 기술 선택: 현재 스택
- 범위 밖: 게시글 목록 요약의 `authorId`, `author` 필드 이름 변경
- 새 컨벤션: 없음

## 테스트 대응
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
