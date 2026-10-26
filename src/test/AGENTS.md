# src/test 작업 지침

`src/test` 아래 코드를 수정하기 전에 읽는다. 저장소 루트의 `AGENTS.md`를 먼저 읽었다는 전제다.

## Conventions

- 대상과 같은 패키지의 `src/test/java`에 `<대상>Tests` 이름으로 둔다(`PostServiceTests`).
- 메서드명은 한글 문장 + 밑줄(`충돌이_3번_계속되면_예외를_던진다`). 본문은 `// given`, `// when`, `// then`
  (또는 `// when & then`) 주석으로 나눈다.
- 검증은 AssertJ(`assertThat`, `assertThatThrownBy`), 목 설정은 BDDMockito(`given`, `willThrow`, `willAnswer`)를 쓴다.
- 단위 테스트: `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`, 또는 생성자로 직접 만든다.
- 통합 테스트: `@SpringBootTest`. 대체할 빈이 있을 때만 `@MockitoBean`을 쓴다.
  외부 HTTP는 JDK `HttpServer` 스텁과 `@DynamicPropertySource`로 대체한다.
- HTTP 테스트: `@SpringBootTest` + `@AutoConfigureMockMvc`(`org.springframework.boot.webmvc.test.autoconfigure`).
  보안 필터와 `GlobalExceptionFilter`가 함께 적용된다. 클래스에 `@Transactional`을 붙여 테스트마다 롤백한다.
  인증은 주입받은 `JwtProvider`로 토큰을 만들어 `Authorization` 헤더에 넣는다(관리자는 `createAccessToken(id, Role.ADMIN)`).
  엔드포인트마다 성공 응답을 검증한다. 인증 필수 경로는 토큰 없는 요청의 401 `A001`, 공개 경로(`/auth/**`)는 토큰 없이 통과를 검증한다.
  입력 검증(400 `C001`)과 도메인 오류는 그 경로에 해당 규칙이 있을 때 검증한다.
- 설정은 `src/test/resources/application.yml`이 main 설정을 대체한다(H2 메모리 DB, MySQL 모드, `create-drop`).
- DB 테스트는 H2를 쓴다. H2 MySQL 모드는 MySQL의 락·격리 수준을 그대로 재현하지 않으므로,
  동시성 동작을 바꿨다면 MySQL에서 검증했는지 따로 보고한다.
- 이 규칙 이전에 만든 `*Test` 클래스와 영문 메서드명은 요청 없이 바꾸지 않는다.
  같은 대상의 테스트 클래스가 이미 있으면 새로 만들지 않고 그 클래스에 추가한다.

## 실행

- 단일 클래스: `./gradlew test --tests '<패키지>.<클래스명>'`. 최종 확인: `./gradlew build`.
- JDK 21이 필요하다(Gradle toolchain). 실패 리포트: `build/reports/tests/test/index.html`.

## 무엇을 테스트하는가

- 개수와 모양은 `docs/code-templates.md` 5. 테스트를 따른다.

- 버그 수정: 재현 테스트를 먼저 작성해 실패를 확인한 뒤 고친다.
- 새 기능: 비즈니스 규칙과 예외 경로는 단위 테스트, Repository 쿼리·제약은 `@DataJpaTest`,
  HTTP 계약은 MockMvc, 외부 연동·트랜잭션·이벤트처럼 스프링이 개입하는 동작은 통합 테스트로 검증한다.
- 동시성 보호(유니크 제약, 원자적 업데이트 등)를 넣었다면 그 보호가 동작하는 테스트를 함께 작성한다.
