# 기능: 문제 풀이 점수와 회원 티어

- 기준 SHA: `aefc8113b6f722f46157a1f6b447931d88466045`
- 목적: 회원이 문제를 풀며 쌓은 점수와 다음 승급까지의 진행 상황을 확인한다.
- API: `GET /api/users/me/tier`, 본인 인증 필수. 요청 본문 없음. 200 `{score, tier, nextTier, pointsToNextTier}`. 티어는 `BRONZE_3` 같은 enum 문자열. 최고 단계는 nextTier=null, pointsToNextTier=0.
- 오류: 인증 없음 또는 토큰의 회원이 없으면 401 `A001`. 클라이언트가 점수를 지정하는 API는 없다.
- 점수: 브론즈 10, 실버 20, 골드 30, 플래티넘 40, 다이아 50, 루비 60. 문제 난이도의 5~1은 동일 점수. UNRATED는 0.
- 회원별·문제별 최초 정답 제출에만 양의 점수를 한 번 지급한다. 실행, 비회원, 다른 회원, 오답, 취소·시스템 오류는 제외한다. 완료 콜백 중복 호출도 제외한다.
- 미지정 정답은 0점 기록을 남긴다. 난이도 지정만으로 지급하지 않으며 이후 새 정답 제출에서 지급한다. 양의 점수 지급 후 난이도 변경·재제출은 점수를 바꾸지 않는다.
- 기존 정답은 첫 조회 시 현재 문제 난이도로 문제당 한 번 반영한다. 반복 조회는 점수를 늘리지 않는다. 새 제출 완료 시에는 해당 문제를 즉시 반영한다.

| 회원 티어 | 3 | 2 | 1 |
| --- | ---: | ---: | ---: |
| BRONZE | 0 | 30 | 60 |
| SILVER | 100 | 160 | 220 |
| GOLD | 300 | 400 | 500 |
| PLATINUM | 600 | 730 | 860 |
| DIAMOND | 1000 | 1200 | 1400 |

- 기준 점수 이상이면 해당 단계이며 누적 점수를 소모하지 않는다.
- 데이터: 새 `problem_rewards` 테이블. id, user FK, problem FK, points(Long), createdAt, updatedAt. `(user_id, problem_id)` 유니크. 기존 테이블 컬럼·데이터 삭제 없음. 합계는 저장된 지급 기록의 SUM이며 티어는 합계에서 계산한다.
- 트랜잭션·동시성: 회원 행 PESSIMISTIC_WRITE + 회원·문제 유니크. 정답 상태 전이와 지급은 같은 트랜잭션으로 커밋·롤백한다. 지급·조회는 READ_COMMITTED로 락 대기 후 최신 지급 기록을 읽는다.
- 성능: 기존 정답 반영은 미반영 문제 ID·난이도 projection을 100개씩 조회한다. 엔티티 연관 조회를 반복하지 않는다. 합계는 DB SUM. 조회 API가 기존 기록 반영 때문에 쓰기 트랜잭션인 점에 유의한다.
- 범위 밖: 프론트 UI, 랭킹, 관리자 점수 조정, 운영 서버 배포. `https://corte-dsm.duckdns.org/`는 사용자 배포 후 검증 주소이며 이번에는 수정하지 않는다.
- 가정: 회원 티어는 다이아 1이 최고이며 루비는 문제 난이도로만 존재한다. 기존 기록의 과거 난이도는 저장되어 있지 않아 최초 반영 시 현재 난이도를 사용한다.

## 테스트 대응

| 명세 항목 | 테스트 | 레드 증거 |
| --- | --- | --- |
| 15단계 경계 직전·일치·직후, 최고 티어 | UserTierTests#승급_기준_직전과_정확한_기준과_직후를_구분한다 / 최고_단계_이후에도_점수_범위를_유지한다 | 구현 전 compileTestJava 실패: UserTier 없음 |
| 난이도별 지급, 미지정 0점 | UserTierServiceTests#최초_정답은_문제_난이도별_점수를_지급한다 | 구현 전 compileTestJava 실패: UserTierService 없음 |
| 기존 중복 정답, 반복 조회, 다음 단계·남은 점수 | UserTierServiceTests#기존_중복_정답을_한번만_반영하고_반복_조회해도_점수는_유지한다 | 구현 전 compileTestJava 실패: UserTierService 없음 |
| 중복 콜백·언어 변경·난이도 변경 후 추가 지급 방지 | UserTierServiceTests#콜백_중복과_다른_언어의_재제출과_난이도_변경은_추가_지급하지_않는다 | 구현 전 compileTestJava 실패: UserTierService 없음 |
| 미지정 후 새 정답에서만 지급 | UserTierServiceTests#미지정_문제는_난이도_지정만으로_지급하지_않고_새_정답에서_지급한다 | 구현 전 compileTestJava 실패: UserTierService 없음 |
| 백개 초과 기존 기록 배치 처리 | UserTierServiceTests#기존_기록은_백개를_넘어도_빠짐없이_반영한다 | 구현 전 compileTestJava 실패: UserTierService 없음 |
| 다른 회원·비회원·실행·오답 제외 | UserTierServiceTests#다른_회원과_비회원과_실행과_오답은_지급하지_않는다 | 구현 전 compileTestJava 실패: UserTierService 없음 |
| GET 200 응답 필드와 인증 없음·없는 회원 401 A001 | UserControllerTests#내_티어는_점수와_다음_단계를_반환한다 / 티어는_인증_없이_조회할_수_없다 / 삭제된_회원_토큰으로_티어를_조회하면_인증_오류다 | 구현 전 전체 대상 테스트 실행은 컴파일 단계에서 실패. 되돌림 검사로 추가 확인 |
| 동시 지급·조회 직렬화, 롤백 원자성 | UserTierServiceTests#동시_정답과_기존_기록_조회는_한번만_지급한다 / 채점_완료가_롤백되면_점수도_함께_롤백된다 | 구현 후 보강한 회귀 테스트. 되돌림 검사 결과는 검증 보고서에 기록 |
| 회원·문제 유니크 제약 | ProblemRewardRepositoryTests#회원과_문제가_같은_지급_기록은_유니크_제약으로_막는다 | 구현 후 보강한 회귀 테스트. 되돌림 검사 결과는 검증 보고서에 기록 |

## 검증 메모

- 기준 빌드: Java 21, `./gradlew.bat build`, 종료 코드 0. tests=377, skipped=7, failures=0, errors=0.
- 최초 레드: `./gradlew.bat test --tests '*UserTier*' --tests '*UserControllerTests'`, 종료 코드 1. UserTier·UserTierService 미구현으로 컴파일 실패.
- 로컬 MySQL 8.4.13 HTTP: 비인증 GET 401/A001, 가입 201·로그인 후 GET 200 `{score:0,tier:BRONZE_3,nextTier:BRONZE_2,pointsToNextTier:30}`.
- 같은 루비 문제 정답 2회 + 플래티넘 정답 1회: GET 200 `{score:100,tier:SILVER_3,nextTier:SILVER_2,pointsToNextTier:60}`. 반복 조회도 동일.
- MySQL 검증은 `corte-tier-67-mysql` 일회용 컨테이너와 전용 DB를 사용하며 배포 서버 데이터에는 접근하지 않는다.
