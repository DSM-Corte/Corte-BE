# src/test 작업 지침

`src/test` 아래 코드를 수정하기 전에 읽는다. 저장소 루트의 `AGENTS.md`를 먼저 읽었다는 전제다.

## Conventions

- 대상과 같은 패키지의 `src/test/java`에 `<대상>Test` 이름으로 둔다.
- 메서드명은 한글 문장 + 밑줄(`충돌이_3번_계속되면_예외를_던진다`). 본문은 `// given`, `// when`, `// then`
  (또는 `// when & then`) 주석으로 나눈다.
- 검증은 AssertJ(`assertThat`, `assertThatThrownBy`), 목 설정은 BDDMockito(`given`, `willThrow`, `willAnswer`)를 쓴다.
- 단위 테스트: `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`, 또는 생성자로 직접 만든다.
- 통합 테스트: `@SpringBootTest` + `@MockitoBean`. 외부 HTTP는 JDK `HttpServer` 스텁과 `@DynamicPropertySource`로 대체한다.
- 설정은 `src/test/resources/application.yml`이 main 설정을 대체한다(H2 메모리 DB, MySQL 모드, `create-drop`).
- DB 테스트는 H2를 쓴다. H2 MySQL 모드는 MySQL의 락·격리 수준을 그대로 재현하지 않으므로,
  동시성 동작을 바꿨다면 MySQL에서 검증했는지 따로 보고한다.
- 기존 `JwtProviderTests`, `CorteBeApplicationTests`는 이 규칙 이전에 만들어졌다. 요청 없이 이름을 바꾸지 않는다.

## 무엇을 테스트하는가

- 버그 수정: 재현 테스트를 먼저 작성해 실패를 확인한 뒤 고친다.
- 새 기능: 비즈니스 규칙과 예외 경로는 단위 테스트로, 외부 연동·트랜잭션·이벤트처럼 스프링이 개입하는 동작은 통합 테스트로 검증한다.
- 동시성 보호(유니크 제약, 원자적 업데이트 등)를 넣었다면 그 보호가 동작하는 테스트를 함께 작성한다.
