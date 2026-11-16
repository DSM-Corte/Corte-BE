# 유저 리뷰 답글에 이모티콘 반응 추가

- 이슈: #65
- 기준 SHA: 766aa161489bc4042c935ca3cedff961b6daa99a

## 기능

원래 줄 코멘트나 전체 댓글을 쓴 유저와 관리자는 해당 코멘트의 답글에 반응을 추가할 수 있다.
반응을 단 유저와 관리자는 반응 하나를 삭제할 수 있다. 모든 API는 로그인해야 한다.

| 메서드 | 경로 | 계약 |
| --- | --- | --- |
| POST | /api/review-requests/{requestId}/replies/{replyId}/reactions | type을 받아 201, Location과 id, type, userId 반환 |
| DELETE | /api/review-requests/{requestId}/replies/{replyId}/reactions/{reactionId} | 반응 삭제 후 204 |

- 종류는 HEART, THUMBS_UP, SMILE, CELEBRATION이다. 문자열 enum 값은 저장 및 API에서 유지한다.
- 답글 응답에 reactionCounts(종류별 개수)와 reactions(삭제 대상 식별용 id, type, userId)를 추가한다. 반응이 없으면 빈 객체와 빈 배열이다.
- 닫힌 요청은 기존 답글 조회 권한을 유지한다. 조회 권한이 있더라도 반응 쓰기는 제한한다.
- 오류: 기존 요청 접근 R001(404), 답글 없음 R002(404), 닫힘 R010(409), 잘못된 종류 C001(400).
  반응 없음 R015(404), 반응 변경 권한 없음 R016(403), 같은 반응 중복 R017(409).
- 신규 테이블 review_reply_reactions에 답글, 추가한 회원, 종류를 저장한다. 기존 답글 변경은 없다.
- 동일 답글·회원·종류 조합을 DB 유니크 제약으로 보호한다. 쓰기는 트랜잭션 안에서 수행한다.
- 답글 직접 삭제와 부모 코멘트 삭제 시 반응도 DB 외래 키로 정리한다.
- 답글 목록의 반응은 답글 ID 단위로 일괄 조회한다. 기존 목록 페이징을 따른다.
- 범위 밖: 프론트엔드 화면.

## 테스트 대응

| 규칙 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 두 답글 유형, 추가 및 취소, 집계 | ReplyControllerTests | 구현 전 실행 후 기록 |
| 권한, 닫힘, 입력 및 대상 검증 | ReplyControllerTests | 구현 전 실행 후 기록 |
| 유형별 중복 제한과 부모 삭제 | ReplyControllerTests | 구현 전 실행 후 기록 |

