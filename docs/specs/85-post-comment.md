# 게시글 댓글

- 이슈: #85
- 기준 SHA: e12df7eb83bead2c1c89ebda5bbe1e87fb20e272

## 기능: 게시글 댓글
- 목적: 로그인한 회원이 게시글에 댓글로 의견을 주고받는다. 코드 리뷰 댓글(`domain/review`)과는 별개다
- API (모두 로그인 필요, `/api/posts/{postId}/comments`)
  - `POST`: 요청 `{content}`, 201 + `Location` + 댓글 응답
  - `GET`: `?page&size`(기본 0/20, 최대 100), 200 + `{content[{id, author, content, createdAt, updatedAt}], page, size, totalElements, totalPages}`
  - `PATCH /{commentId}`: 요청 `{content}`, 200 + 댓글 응답, 작성자만
  - `DELETE /{commentId}`: 204, 작성자만
  - 댓글 응답: `{id, content, author(닉네임), createdAt, updatedAt}`
- 데이터: `PostComment`(`post_comments`): `id`, `post`(ManyToOne LAZY, `post_id`), `author`(`author_id`), `content`(length 500), `createdAt`, `updatedAt`.
  인덱스 `idx_post_comments_post_id`. 새 테이블이라 기존 데이터 영향 없음
- 규칙
  - 본문 필수, 공백만 불가, 최대 500자. 수정은 `null`이면 변경 없음(공백만이면 400)
  - 대댓글 없음. 정렬은 오래된 순(`id` 오름차순)이다. 대화 흐름 순으로 읽도록 목록 기본값(내림차순)과 다르게 정했다
  - 게시글을 삭제하면 그 댓글도 함께 삭제한다(`deleteAllByPostId` 후 게시글 삭제)
  - 경로의 `postId`와 댓글의 게시글이 다르면 404 `B003`
- 오류: 게시글 없음 404 `B001`(기존), 댓글 없음 404 `B003`, 작성자가 아님 403 `B004`, 검증 실패 400 `C001`, 토큰 없음 401 `A001`
- 트랜잭션: Service 트랜잭션 안에서 DTO 변환까지 끝낸다 / 동시성: 없음(단순 덮어쓰기, 별도 락 없음). 삭제는 대상이 이미 없으면 404
- 성능: 목록은 페이징 + 작성자 닉네임 join(`Summary` projection), 게시글 삭제는 벌크 삭제 1회
- 기술 선택: 현재 스택
- 범위 밖: 대댓글, 댓글 좋아요, 알림, 관리자 댓글 삭제, 내가 쓴 댓글 목록, 게시글 목록의 댓글 수
- 새 컨벤션: 없음

## 테스트 대응
(구현하며 채운다)
