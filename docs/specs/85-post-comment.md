# 게시글 댓글

- 이슈: #85
- 기준 SHA: e12df7eb83bead2c1c89ebda5bbe1e87fb20e272

## 기능: 게시글 댓글
- 목적: 로그인한 회원이 게시글에 댓글로 의견을 주고받는다. 코드 리뷰 댓글(`domain/review`)과는 별개다
- API (모두 로그인 필요, `/api/posts/{postId}/comments`)
  - `POST`: 요청 `{content}`, 201 + `Location` + 댓글 응답
  - `GET`: `?page&size`(기본 0/20, 최대 100), 200 + `{content[{id, author, authorId, content, createdAt, updatedAt}], page, size, totalElements, totalPages}`
  - `PATCH /{commentId}`: 요청 `{content}`, 200 + 댓글 응답, 작성자만
  - `DELETE /{commentId}`: 204, 작성자만
  - 댓글 응답: `{id, content, author(닉네임), authorId(작성자 ID, #92), createdAt, updatedAt}`
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
| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 목록: 해당 게시글 댓글만, 작성자 닉네임 join, 오래된 순 | `PostCommentRepositoryTests#목록_조회는_해당_게시글의_댓글만_작성자_닉네임과_함께_오래된_순으로_가져온다` | 컴파일 실패: `PostComment`, `PostCommentRepository` 없음 |
| 게시글 삭제 시 댓글 일괄 삭제(다른 게시글 댓글은 유지) | `PostCommentRepositoryTests#게시글의_댓글만_모두_삭제한다`, `PostServiceTests#게시글을_삭제하면_댓글을_먼저_지운_뒤_게시글을_지운다`, `PostCommentControllerTests#게시글을_삭제하면_댓글도_함께_삭제된다` | 컴파일 실패: `deleteAllByPostId` 없음 |
| POST 201 + Location + 댓글 응답(작성자 닉네임) | `PostCommentControllerTests#댓글을_작성하면_201과_Location과_댓글을_준다`, `PostCommentServiceTests#댓글을_작성하면_작성자_닉네임과_함께_응답한다` | 컴파일 실패: `PostCommentService` 없음 |
| 게시글 없음 → 404 B001 (작성·목록) | `PostCommentServiceTests#게시글이_없으면_댓글을_작성할_수_없다`, `#게시글이_없으면_댓글_목록을_조회할_수_없다`, `PostCommentControllerTests#없는_게시글이면_404_B001을_준다` | 컴파일 실패 |
| 토큰은 유효한데 회원 없음 → 401 | `PostCommentServiceTests#회원이_없으면_댓글을_작성할_수_없다` | 컴파일 실패 |
| GET 목록 오래된 순·페이징 | `PostCommentControllerTests#댓글_목록은_오래된_순으로_페이징해서_준다`, `PostCommentServiceTests#댓글_목록은_오래된_순으로_조회해_응답으로_바꾼다` | 컴파일 실패 |
| 페이지 범위 오류 → 400 C001 | `PostCommentServiceTests#페이지_범위가_잘못되면_댓글_목록_조회에_실패한다`, `PostCommentControllerTests#페이지_범위가_잘못되면_400_C001을_준다` | 컴파일 실패 |
| PATCH 200, null이면 본문 유지 | `PostCommentControllerTests#작성자는_댓글을_수정할_수_있다`, `PostCommentServiceTests#작성자가_수정하면_본문이_바뀐다`, `#수정_본문이_null이면_기존_본문을_유지한다` | 컴파일 실패 |
| DELETE 204 | `PostCommentControllerTests#작성자는_댓글을_삭제할_수_있다`, `PostCommentServiceTests#작성자가_삭제하면_댓글이_지워진다` | 컴파일 실패 |
| 작성자가 아니면 403 B004 (수정·삭제) | `PostCommentControllerTests#작성자가_아니면_수정과_삭제에_403_B004를_준다`, `PostCommentServiceTests#작성자가_아니면_댓글을_수정할_수_없다`, `#작성자가_아니면_댓글을_삭제할_수_없다` | 컴파일 실패: `PostCommentForbiddenException` 없음 |
| 댓글 없음 / 다른 게시글의 댓글 → 404 B003 | `PostCommentControllerTests#없는_댓글이거나_다른_게시글의_댓글이면_404_B003을_준다`, `PostCommentServiceTests#댓글이_없으면_수정할_수_없다`, `#다른_게시글의_댓글이면_수정할_수_없다`, `#다른_게시글의_댓글이면_삭제할_수_없다` | 컴파일 실패: `PostCommentNotFoundException` 없음 |
| 본문 공백·501자 → 400 C001, 500자는 허용(경계값) | `PostCommentControllerTests#본문이_공백이거나_500자를_넘으면_400_C001을_준다`, `#본문이_정확히_500자면_작성할_수_있다` | 컴파일 실패 |
| 토큰 없음 → 401 A001 | `PostCommentControllerTests#토큰이_없으면_401_A001을_준다` | 컴파일 실패(보안 설정이 이미 막아 구현 후에도 같은 결과) |
| 트랜잭션·동시성·성능(join 1회, 벌크 삭제) | 해당 없음: 락 없는 단순 덮어쓰기. 쿼리 동작은 위 Repository 테스트가 검증 | 해당 없음 |
