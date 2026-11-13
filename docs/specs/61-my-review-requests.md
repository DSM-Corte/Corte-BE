# 마이페이지 내 리뷰 요청 목록 조회 (#61)

- 기준 SHA: 244162db9152071db6044bf0d1385046639e5b20
- 목적: 마이페이지에서 내가 요청한 리뷰를 상태별로 찾아 상세 화면으로 이동한다.
- API: GET /api/users/me/review-requests. Bearer 토큰 필수. 토큰의 사용자 ID와 요청자가 같은 행만 반환한다.
- 쿼리: status는 ALL(기본), OPEN, CLOSED. page는 0부터, size는 1~100(기본 20). 페이지 오프셋은 정수 최댓값까지 허용한다.
- 응답: 200, {content, page, size, totalElements, totalPages}. 항목은 {id, problemId, problemTitle, status, createdAt}. status는 OPEN 또는 CLOSED다. 요청 ID 내림차순이며 결과가 없으면 content는 빈 배열이다.
- 오류: 인증 없음 401 A001, 잘못된 필터나 페이지 400 C001.
- 기존 리뷰 요청의 closed와 요청자, 연결된 문제를 읽는다. 스키마 변경과 쓰기 없음. 단일 페이지 집계에 기존 연관관계를 조인하고 개수는 별도 쿼리로 계산한다. 락이 필요하지 않다.
- 기존 리뷰 목록·상세·닫기 및 프로필 API는 범위 밖이다.

## 테스트 대응

구현 전 테스트 결과를 기록한다.
