# SSF vs Spring Framework 기능 포괄 분석

> SSF(SOX Service Framework)가 Spring Framework의 기능을 얼마나 포괄하는지 분석한 문서입니다.
> 측위 관련 비즈니스 로직은 제외하고, 프레임워크 인프라 기능만을 비교합니다.

---

## 전체 요약

SSF는 Spring MVC의 핵심 요청 처리 흐름을 자체 구현한 **경량 MVC 프레임워크**입니다. Spring의 방대한 기능 중 **요청 라우팅과 JSP 뷰 처리**에 집중하며, DI/AOP 등 Spring의 핵심 철학은 구현하지 않습니다.

**종합 포괄도: 약 15~20%**

---

## 기능별 상세 비교

### 1. 요청 라우팅 (Request Dispatching) — 포괄도 20%

| 항목 | SSF | Spring |
|------|-----|--------|
| DispatcherServlet | `*.do` 패턴 매핑, HashMap 기반 라우팅 | 경로 패턴, 정규식, 와일드카드 지원 |
| 어노테이션 라우팅 | `@ControllerClassInfo` + `@ControllerMethodInfo` (7개 속성) | `@RequestMapping`, `@GetMapping` 등 (수십 개 속성) |
| HTTP 메서드 구분 | **미지원** — GET/POST 동일 처리 | GET/POST/PUT/DELETE/PATCH 개별 매핑 |
| 컨텐츠 협상 | **미지원** | Accept 헤더 기반 JSON/XML/HTML 자동 선택 |
| 경로 변수 | **미지원** | `@PathVariable("id")` — `/users/{id}` |

**SSF 구현 방식:**
- `DispatcherServlet.init()`에서 `@Reflections` 라이브러리로 `com.ithows.controller` 패키지 스캔
- `@ControllerMethodInfo(id="login.do")` 어노테이션의 id 값으로 HashMap에 등록
- 요청 시 URL에서 커맨드명 추출 → `PageBeanContainer.get(cmd)`로 라우팅

---

### 2. 의존성 주입 (DI/IoC) — 포괄도 5%

| 항목 | SSF | Spring |
|------|-----|--------|
| IoC 컨테이너 | **없음** | ApplicationContext (싱글톤/프로토타입/세션/요청 스코프) |
| 주입 방식 | **없음** — `new`로 직접 생성, static 유틸리티 호출 | 생성자/세터/필드 `@Autowired` 주입 |
| 빈 생명주기 | init 시 리플렉션으로 1회 생성, HashMap 캐싱 | `@PostConstruct`, `@PreDestroy`, 스코프별 관리 |
| 빈 스캐닝 | `@Reflections` 라이브러리로 컨트롤러만 스캔 | `@ComponentScan`으로 전체 컴포넌트 자동 등록 |

**SSF 구현 방식:**
```java
// 컨트롤러 인스턴스를 리플렉션으로 생성 후 HashMap에 캐싱
Object ctrlObject = c.newInstance();
// 이후 static 유틸리티 클래스(AppConfig, JdbcDao)를 서비스 로케이터처럼 사용
```

---

### 3. 파라미터 바인딩 — 포괄도 15%

| 항목 | SSF | Spring |
|------|-----|--------|
| 타입 변환 | 6종 (String, int, float, long, double, char) | 50종+ (Boolean, Date, LocalDateTime, UUID, Enum 등) |
| 바인딩 방식 | 요청 파라미터 → 커맨드 객체 setter 자동 호출 | `@RequestParam`, `@RequestBody`, `@ModelAttribute` |
| 중첩 객체 | **미지원** | `user.address.city` 형태 중첩 바인딩 |
| 검증 | **미지원** | `@Valid`, `@NotNull`, `@Size` 등 Bean Validation |
| JSON 본문 | HttpUtil에서 수동 파싱 | `@RequestBody` + Jackson 자동 역직렬화 |

**SSF 구현 방식 (CommandManager.java):**
```java
// 파라미터명 → setter 메서드 리플렉션 호출
String methodName = "set" + capitalize(paramName);
Method m = getMethod(commandClass, methodName);
// 타입별 분기: String, int, float, long, double, char
if (paramType.equals(int.class)) {
    value = Integer.parseInt(rawValue.replace(",", ""));
}
m.invoke(commandObject, value);
```

---

### 4. 인터셉터/AOP — 포괄도 5%

| 항목 | SSF | Spring |
|------|-----|--------|
| 인터셉터 체인 | **없음** — `ServiceInterceptor.checkLogin()` 수동 호출 | `HandlerInterceptor` 자동 체인 (preHandle/postHandle/afterCompletion) |
| AOP | **없음** | `@Aspect`, `@Before`, `@After`, `@Around` |
| 예외 처리 | try-catch 수동 | `@ExceptionHandler`, `@ControllerAdvice` 선언적 처리 |

**SSF 구현 방식:**
```java
// 컨트롤러 또는 JSP에서 수동 호출
String redirect = ServiceInterceptor.checkLogin(session, request);
if (redirect != null) return redirect;  // 미인증 시 리다이렉트
```

---

### 5. 뷰 리졸버 — 포괄도 25%

| 항목 | SSF | Spring |
|------|-----|--------|
| JSP 뷰 매핑 | 컨벤션 기반 (`login.do` → `login.jsp`) | `InternalResourceViewResolver` 설정 기반 |
| 템플릿 레이아웃 | XML 정의 top/bottom/main 3분할 조합 | Tiles, Thymeleaf, Freemarker 등 다수 지원 |
| 리다이렉트 | `"redirect:URL"` 문자열 반환 | 동일 + RedirectAttributes 플래시 속성 |
| JSON 응답 | 전용 JSP 템플릿으로 수동 출력 | `@ResponseBody` + HttpMessageConverter 자동 직렬화 |

**SSF 뷰 리졸루션 전략 (PageManager.java):**
1. 컨트롤러 메서드 반환값이 null → 기본 JSP (`pageId.replace(".do", ".jsp")`)
2. `"redirect:URL"` → 리다이렉트
3. 특수 상수 (`RESULT_PAGE_JSON` 등) → 전용 JSON 템플릿 JSP로 포워딩
4. 템플릿명 → top/bottom/main 레이아웃 조합
5. JSP 경로 → 직접 포워딩

---

### 6. 트랜잭션 관리 — 포괄도 20%

| 항목 | SSF | Spring |
|------|-----|--------|
| 트랜잭션 범위 | `JdbcTransactor` 콜백 인터페이스 (수동) | `@Transactional` 선언적 관리 |
| 롤백 정책 | 예외 발생 시 catch에서 수동 rollback | `rollbackFor`, `noRollbackFor` 세밀 제어 |
| 격리 수준 | **미지원** | `isolation = Isolation.SERIALIZABLE` 등 |
| 중첩 트랜잭션 | **미지원** | Savepoint 기반 중첩 지원 |
| 커넥션 풀 | Tomcat JDBC Pool (XML 설정) | HikariCP/Tomcat/DBCP 선택, 자동 설정 |

**SSF 구현 방식:**
```java
// 콜백 패턴 기반 프로그래매틱 트랜잭션
JdbcDao.transacUpdate(new JdbcTransactor() {
    public void doTransaction(Connection conn) throws SQLException {
        JdbcDao.update(conn, "INSERT ...", params);
        JdbcDao.update(conn, "UPDATE ...", params);
        // 예외 발생 시 endConnection()에서 rollback
    }
});

// endConnection: commit or rollback
public static void endConnection(Connection conn) {
    try { conn.commit(); }
    catch (SQLException e) { conn.rollback(); }
    finally { conn.close(); }
}
```

---

### 7. 설정 관리 — 포괄도 15%

| 항목 | SSF | Spring |
|------|-----|--------|
| 설정 파일 | XML 프로퍼티 1개 (`configplatform.xml`) | properties, YAML, 환경 변수, 시스템 프로퍼티 |
| 접근 방식 | `AppConfig.getConf("key")` static 호출 | `@Value("${key}")` 주입, `@ConfigurationProperties` |
| 프로파일 | **미지원** | `@Profile("dev")`, `spring.profiles.active` |
| 런타임 수정 | `AppConfig.setConf()` → XML 파일에 즉시 반영 | `@RefreshScope` (Cloud Config) |

---

### 8. 세션 관리 — 포괄도 25%

| 항목 | SSF | Spring |
|------|-----|--------|
| 세션 추적 | `HashMap<String, HttpSession>` 인메모리 레지스트리 | SessionRegistry, 분산 세션 (Redis/JDBC) |
| 중복 로그인 방지 | `checkLogin(clientNo)` — 기존 세션 무효화 | `maximumSessions(1).maxSessionsPreventsLogin(true)` |
| 세션 이벤트 | `HttpSessionListener` 구현 | 동일 + `@SessionScope` 빈, 이벤트 리스너 |

---

### 9. 필터 체계 — 포괄도 25%

| 항목 | SSF | Spring |
|------|-----|--------|
| 구현 필터 | `CharacterEncodingFilter` + `SecurityFilter` (2개) | 동일 + CORS, CSRF, Security 등 10종+ |
| 보안 헤더 | `SecurityFilter`에서 X-Frame-Options, X-Content-Type-Options, X-XSS-Protection, Content-Security-Policy 등 자동 추가 | Spring Security `headers()` DSL |
| XSS 방어 | `SecurityFilter`에서 파라미터 값 HTML 이스케이프 (`XssRequestWrapper`) | Spring Security `@EnableWebSecurity` |
| CSRF 방어 | `SecurityFilter`에서 토큰 기반 CSRF 검증 (설정으로 활성화 가능) | Spring Security `csrf()` 자동 적용 |
| Rate Limiting | `SecurityFilter`에서 IP별 요청 수 제한 (ConcurrentHashMap 기반) | 미내장 — 별도 라이브러리(Bucket4j 등) 또는 API Gateway 필요 |
| 등록 방식 | web.xml 선언 | `@Bean FilterRegistrationBean` 또는 자동 등록 |

**SSF 구현 방식 (SecurityFilter.java):**
```java
// web.xml 설정으로 제어
<filter>
    <filter-name>SecurityFilter</filter-name>
    <filter-class>com.ithows.base.SecurityFilter</filter-class>
    <init-param><param-name>enableCsrf</param-name><param-value>false</param-value></init-param>
    <init-param><param-name>rateLimitMaxRequests</param-name><param-value>100</param-value></init-param>
    <init-param><param-name>rateLimitWindowMs</param-name><param-value>60000</param-value></init-param>
</filter>
// SecurityFilter가 HTTP 보안 헤더, XSS 방어, CSRF 토큰 검증, Rate Limiting을 일괄 처리
```

> **참고:** SSF의 Rate Limiting은 Spring에 내장되지 않은 기능으로, Spring에서는 별도 라이브러리나 API Gateway에서 처리해야 합니다.

---

### 10. 스케줄링/비동기 — 포괄도 10%

| 항목 | SSF | Spring |
|------|-----|--------|
| 백그라운드 작업 | 데몬 스레드 + 20초 폴링 루프 | `@Scheduled`, `@Async`, TaskScheduler |
| 비동기 처리 | `ModelProcessManager` (수동 스레드풀) | `@Async` + CompletableFuture, WebFlux |
| SSE | 수동 PrintWriter flush | `SseEmitter`, WebFlux SSE |

---

### 11. API 문서화 (Swagger/OpenAPI) — 포괄도 30%

| 항목 | SSF | Spring |
|------|-----|--------|
| OpenAPI 생성 | `SwaggerServlet`에서 어노테이션 스캔 → OpenAPI 3.0 JSON 자동 생성 | `springdoc-openapi` 또는 `springfox`로 자동 생성 |
| API 메타정보 | `@ApiInfo(summary, description, tag, method, parameters)` | `@Operation`, `@Parameter`, `@Schema` (OpenAPI 표준) |
| Swagger UI | `SwaggerServlet`이 내장 HTML로 Swagger UI 제공 (`/swagger/`) | `springdoc-openapi-ui` 의존성 추가 시 자동 |
| 파라미터 정의 | `@ApiInfo.Param(name, type, description, required, example)` | `@Parameter(name, schema, description, required, example)` |
| 태그 그룹핑 | `@ApiInfo(tag = "...")`로 기능별 그룹 | `@Tag(name = "...")`로 기능별 그룹 |
| 스키마 정의 | 기본 `ApiResponse` 스키마만 제공 | `@Schema`로 상세 모델 정의, DTO 자동 스캔 |
| 인증 스키마 | **미지원** | OAuth2, JWT, API Key 등 SecurityScheme 지원 |
| 코드 생성 | **미지원** | OpenAPI Generator로 클라이언트/서버 코드 생성 |

**SSF 구현 방식 (SwaggerServlet + @ApiInfo):**
```java
// 컨트롤러 메서드에 @ApiInfo 어노테이션 선언
@ControllerMethodInfo(id = "/service/remakeMatchGrid.do", loginRequired = true)
@ApiInfo(
    summary = "Match 그리드 재생성",
    description = "지정 영역의 LTE Match 그리드를 재생성합니다.",
    tag = "LTE Match 그리드",
    method = "GET",
    parameters = {
        @ApiInfo.Param(name = "minX", type = "number", description = "최소 경도", required = true),
        @ApiInfo.Param(name = "minY", type = "number", description = "최소 위도", required = true),
        @ApiInfo.Param(name = "maxX", type = "number", description = "최대 경도", required = true),
        @ApiInfo.Param(name = "maxY", type = "number", description = "최대 위도", required = true)
    }
)
public String remakeMatchGrid(...) { ... }

// SwaggerServlet이 Reflections로 전체 컨트롤러 스캔 →
// @ControllerMethodInfo + @ApiInfo 조합으로 OpenAPI 3.0 JSON 생성 →
// /swagger/ 경로에서 Swagger UI 제공
```

**현재 적용 현황:**
- 12개 컨트롤러, 약 52개 API 메서드에 `@ApiInfo` 적용 완료
- 10개 태그 그룹: 인증, 페이지, 관리자, 서비스 관리, 유틸리티, LTE Match 그리드, 실내측위 모델, 온디바이스 그리드, 앱 로그 분석, 위치인증 API 등

---

### 12. 데이터 액세스 추상화 — 포괄도 10%

| 항목 | SSF | Spring |
|------|-----|--------|
| JDBC 래퍼 | `JdbcDao.queryForMapList()`, `update()` 등 | `JdbcTemplate.query()`, `update()` 등 (유사한 API) |
| ORM 지원 | **없음** | JPA, Hibernate, MyBatis 통합 |
| RowMapper | `RowMapper` 추상 클래스 (Spring과 유사) | `RowMapper<T>` 인터페이스 |
| 듀얼 DB | `JdbcDao` + `JdbcDao2` (코드 복제) | `@Primary`, `@Qualifier` 다중 DataSource |

---

### 13. 완전 미구현 영역 — 포괄도 0%

| Spring 기능 | SSF 상태 |
|-------------|---------|
| Spring Security (인증/인가) | 부분 구현 — SecurityFilter(헤더/XSS/CSRF/RateLimit), 세션 기반 로그인 체크 |
| Bean Validation (`@Valid`) | 미구현 |
| 선언적 예외 처리 (`@ControllerAdvice`) | 미구현 |
| REST API 자동 직렬화 (`@ResponseBody`) | 미구현 — JSP 수동 출력 |
| 프로파일/환경 분리 (`@Profile`) | 미구현 |
| 테스트 인프라 (MockMvc, `@SpringBootTest`) | 미구현 |
| 국제화 (i18n, `MessageSource`) | 미구현 |
| 이벤트 시스템 (`ApplicationEvent`) | 미구현 |
| WebSocket 추상화 (STOMP) | 미구현 — 직접 JSR-356 사용 |
| 캐싱 (`@Cacheable`) | 미구현 |

---

## 종합 포괄도 차트

```
┌─────────────────────────┬──────────┬──────────────────────────────────────────┐
│ Spring 기능 영역          │ SSF 포괄 │ 상태                                      │
├─────────────────────────┼──────────┼──────────────────────────────────────────┤
│ 요청 라우팅               │ ██░░░░░░ │ 20% — 기본 URL 매핑만                     │
│ 의존성 주입 (DI/IoC)      │ █░░░░░░░ │  5% — 컨트롤러 캐싱만                     │
│ 파라미터 바인딩            │ █░░░░░░░ │ 15% — 6종 원시 타입만                     │
│ 인터셉터/AOP             │ █░░░░░░░ │  5% — 로그인 체크 수동 호출                │
│ 뷰 리졸버                │ ██░░░░░░ │ 25% — JSP 한정 컨벤션 기반                │
│ 트랜잭션 관리             │ ██░░░░░░ │ 20% — 콜백 패턴 프로그래매틱               │
│ 설정 관리                │ █░░░░░░░ │ 15% — XML 키-값 static 접근               │
│ 세션 관리                │ ██░░░░░░ │ 25% — 인메모리 레지스트리                  │
│ 필터/보안 체계            │ ██░░░░░░ │ 25% — SecurityFilter (헤더/XSS/CSRF/Rate) │
│ 스케줄링/비동기           │ █░░░░░░░ │ 10% — 수동 스레드 관리                    │
│ API 문서화 (Swagger)      │ ██░░░░░░ │ 30% — @ApiInfo + SwaggerServlet           │
│ 데이터 액세스 추상화       │ █░░░░░░░ │ 10% — JdbcDao (Spring JDBC 유사)          │
│ REST API 지원            │ █░░░░░░░ │  5% — 수동 JSON 출력                      │
│ 테스트 인프라             │ ░░░░░░░░ │  0% — 미구현                             │
│ 국제화 (i18n)            │ ░░░░░░░░ │  0% — 미구현                             │
│ 이벤트 시스템             │ ░░░░░░░░ │  0% — 미구현                             │
├─────────────────────────┼──────────┼──────────────────────────────────────────┤
│ 종합                     │ █░░░░░░░ │ 약 15~20%                                │
└─────────────────────────┴──────────┴──────────────────────────────────────────┘
```

---

## 핵심 결론

### SSF가 잘 하는 것
- `*.do` 패턴 기반 심플한 요청 라우팅
- 어노테이션 기반 컨트롤러 자동 등록
- JSP 컨벤션 기반 뷰 리졸루션 (top/bottom 템플릿 조합)
- `JdbcTransactor` 콜백 패턴의 트랜잭션 관리
- 중복 로그인 방지 세션 관리
- **`SecurityFilter`에서 HTTP 보안 헤더, XSS 방어, CSRF 토큰, Rate Limiting 일괄 처리**
- **`@ApiInfo` + `SwaggerServlet`으로 OpenAPI 3.0 문서 자동 생성 및 Swagger UI 제공**

### SSF에 완전히 없는 것
- **IoC/DI 컨테이너** (Spring과의 가장 큰 차이)
- **AOP / 인터셉터 체인**
- **선언적 트랜잭션** (`@Transactional`)
- **Bean Validation** (`@Valid`)
- **선언적 예외 처리** (`@ControllerAdvice`)
- **REST API 자동 직렬화** (`@ResponseBody`)
- **프로파일/환경 분리**
- **테스트 인프라**

### 아키텍처 특성
SSF는 2000년대 초반 Struts 스타일의 경량 MVC를 자체 구현한 것으로, **요청을 받아 컨트롤러를 호출하고 JSP를 반환하는 최소 흐름**에 특화되어 있습니다. Spring의 핵심 가치인 IoC/DI, AOP, 선언적 프로그래밍 모델은 포함하지 않으며, 대신 static 유틸리티 클래스와 서비스 로케이터 패턴에 의존합니다.


---

## SSF가 Spring 대비 갖는 장점

Spring이 만능은 아닙니다. SSF가 갖는 고유한 강점을 영역별로 정리합니다.

### 1. 단순성과 투명성

Spring의 가장 큰 비판 중 하나는 **"마법(Magic)"** 입니다. `@Autowired` 하나로 객체가 주입되지만, 실패했을 때 원인을 추적하기 어렵습니다.

```java
// Spring — 무슨 일이 일어나는지 보이지 않음
@Autowired
private UserService userService;  // 어디서? 언제? 어떤 구현체가?
```

```java
// SSF — 모든 흐름이 코드에 보임
ResultMap user = JdbcDao.queryForMapObject(sql, params);  // 직접 호출, 추적 가능
```

SSF는 **"코드를 읽으면 동작이 보인다"** 는 장점이 있습니다. DispatcherServlet → PageBeanContainer → Controller → JdbcDao → JSP, 이 흐름이 전부입니다. Spring은 프록시, AOP, 빈 후처리기 등 수십 개의 숨겨진 레이어가 동작합니다.

---

### 2. 학습 곡선

| 항목 | SSF | Spring |
|------|-----|--------|
| 프레임워크 전체 코드량 | Java 11개 파일, ~2,000줄 | 수십만 줄 (Spring Core만 수만 줄) |
| 핵심 개념 | Servlet, JSP, JDBC — 3가지 | IoC, DI, AOP, Bean Lifecycle, Proxy 등 수십 가지 |
| 숙련 소요 시간 | 1~2일이면 전체 파악 가능 | 수개월~수년 (깊이에 따라) |
| 디버깅 | 스택트레이스가 짧고 명확 | 프록시/AOP 레이어로 스택트레이스 수십 줄 |

신규 인력이 투입되었을 때 SSF는 `DispatcherServlet.java` 하나만 읽으면 전체 요청 흐름을 이해합니다. Spring은 DispatcherServlet → HandlerMapping → HandlerAdapter → ArgumentResolver → MessageConverter → ViewResolver 체인을 모두 이해해야 합니다.

---

### 3. 기동 속도와 경량성

```
SSF 기동 시간:
  Servlet init → 어노테이션 스캔 → HashMap 등록 → 완료 (수 초)

Spring Boot 기동 시간:
  ComponentScan → BeanDefinition → DI 해석 → 프록시 생성 →
  AutoConfiguration → HealthIndicator → Actuator → ... (수십 초)
```

| 항목 | SSF | Spring Boot |
|------|-----|-------------|
| WAR 크기 | 수 MB (lib 포함 ~50MB) | 50~200MB+ |
| 기동 시간 | 2~5초 | 10~30초+ (규모에 따라) |
| 메모리 사용 | 최소한 (Servlet + JDBC Pool만) | BeanFactory, 프록시, 캐시 등 오버헤드 |
| 의존성 수 | pom.xml에 직접 관리하는 것만 | Starter 하나에 수십 개 전이 의존성 |

리소스가 제한된 환경(IoT 에지 서버, 임베디드 등)에서는 SSF의 경량성이 실질적 장점입니다.

---

### 4. 프레임워크 완전 장악

SSF의 프레임워크 코드는 **프로젝트 내부에 있습니다.** 수정이 필요하면 직접 고칩니다.

```java
// SSF — 라우팅 방식을 바꾸고 싶으면 DispatcherServlet.java를 직접 수정
// 파라미터 바인딩에 새 타입을 추가하고 싶으면 CommandManager.java에 추가
// 인터셉터를 추가하고 싶으면 process() 메서드에 직접 삽입
```

Spring에서 프레임워크 동작을 바꾸려면:
- `BeanPostProcessor`, `HandlerMethodArgumentResolver` 등 확장 포인트를 찾아야 하고
- 내부 동작 원리를 깊이 이해해야 하며
- 버전 업그레이드 시 호환성 문제가 발생할 수 있습니다

SSF는 **프레임워크가 곧 프로젝트 코드**이므로 제약 없이 자유롭게 변경 가능합니다.

---

### 5. SQL에 대한 완전한 제어

```java
// SSF — SQL을 직접 작성, 최적화된 쿼리 보장
String sql = "SELECT g.*, ST_Intersects(g.geom, ST_PolyFromText(?)) AS hit " +
             "FROM globalgrid g WHERE g.level = ? " +
             "AND g.ltekey LIKE CONCAT('%', ?, '%') " +
             "ORDER BY g.updatetime DESC LIMIT 100";
ArrayList<ResultMap> results = JdbcDao.queryForMapList(sql, params);
```

Spring Data JPA는 편리하지만:
- **N+1 문제** — `@OneToMany` 관계에서 자동으로 발생
- **예측 불가능한 쿼리** — 메서드명이 복잡해지면 생성되는 SQL을 예측하기 어려움
- **공간 쿼리 제약** — `ST_Intersects` 같은 MySQL Spatial 함수는 JPA 네이티브 쿼리 필요
- **성능 튜닝** — 실행 계획 최적화를 위해 결국 네이티브 SQL로 돌아가는 경우 다수

이 프로젝트처럼 **공간 쿼리(Spatial Query)가 핵심**인 경우, JPA 추상화가 오히려 방해가 될 수 있습니다.

---

### 6. 프레임워크 버전 종속성 없음

| 항목 | SSF | Spring |
|------|-----|--------|
| 버전 업그레이드 | 불필요 (자체 코드) | Spring 5→6, Boot 2→3 마이그레이션 공수 큼 |
| Java 버전 종속 | Java 8에서 문제없이 동작 | Spring 6/Boot 3은 Java 17 필수 |
| Jakarta 전환 | 해당 없음 | `javax.*` → `jakarta.*` 네임스페이스 전환 필요 |
| 보안 취약점 패치 | 프레임워크 코드가 작아 영향 범위 한정 | Log4Shell 같은 전이 의존성 취약점 노출 위험 |
| 하위 호환성 | 직접 통제 | Spring 메이저 버전 간 Breaking Changes |

Spring Boot 2 → 3 마이그레이션은 Java 17 필수, Jakarta 네임스페이스 전환, 수십 개 deprecated API 교체 등 **수주~수개월의 작업**이 필요한 경우가 많습니다. SSF는 이런 외부 프레임워크 의존 리스크가 없습니다.

---

### 7. 디버깅 용이성

```
SSF 예외 스택트레이스 (5줄):
  at UserDAO.getLoginUser(UserDAO.java:42)
  at WelcomeController.login(WelcomeController.java:38)
  at PageManager.callController(PageManager.java:25)
  at DispatcherServlet.process(DispatcherServlet.java:112)
  at DispatcherServlet.doPost(DispatcherServlet.java:68)

Spring 예외 스택트레이스 (30줄+):
  at UserService$$EnhancerBySpringCGLIB$$abc123.getUser(<generated>)
  at UserService$$FastClassBySpringCGLIB$$def456.invoke(<generated>)
  at MethodProxy.invoke(MethodProxy.java:218)
  at CglibAopProxy$DynamicAdvisedInterceptor.intercept(...)
  at TransactionInterceptor.invoke(...)
  at TransactionAspectSupport.invokeWithinTransaction(...)
  at ReflectiveMethodInvocation.proceed(...)
  ... (프록시, AOP, 트랜잭션 레이어 20줄+)
  at DispatcherServlet.doDispatch(...)
  at FrameworkServlet.processRequest(...)
```

SSF는 **호출 스택이 짧고 직관적**이어서 문제 원인을 즉시 파악할 수 있습니다.

---

### 8. 배포 단순성

```
SSF 배포:
  mvn package → WAR 복사 → Tomcat webapps/ → 완료

Spring Boot 배포 (일반적):
  mvn package → JAR 생성 → JVM 옵션 설정 → 프로파일 선택 →
  환경 변수 설정 → 실행 (+ 모니터링 설정, 로그 설정...)
```

SSF는 **기존 Tomcat에 WAR만 배포하면 끝**입니다. 별도 인프라 변경 없이 운영 중인 Tomcat 위에 그대로 얹을 수 있습니다.

---

### SSF 장점 요약

```
┌─────────────────────┬──────────────────────────────────────────────────────┐
│ SSF 장점             │ 설명                                                  │
├─────────────────────┼──────────────────────────────────────────────────────┤
│ 투명성               │ 모든 흐름이 코드에 보임, "마법" 없음                      │
│ 학습 곡선             │ 1~2일이면 전체 프레임워크 파악 가능                       │
│ 경량성               │ 기동 2~5초, 메모리 오버헤드 최소                         │
│ 완전한 장악력          │ 프레임워크 코드가 프로젝트 안에 있어 자유 수정              │
│ SQL 완전 제어         │ 공간 쿼리 등 복잡 SQL 직접 최적화 가능                   │
│ 버전 종속성 없음       │ 프레임워크 업그레이드/마이그레이션 리스크 제로              │
│ 디버깅 용이           │ 스택트레이스 5줄 vs Spring 30줄+                       │
│ 배포 단순             │ WAR 복사 한 번으로 완료                                │
│ 통합 보안 필터         │ SecurityFilter 하나로 헤더/XSS/CSRF/Rate Limit 일괄 처리 │
│ API 문서 자동화        │ @ApiInfo 어노테이션 → Swagger UI 자동 생성              │
└─────────────────────┴──────────────────────────────────────────────────────┘
```

**결론:** SSF는 **"작은 팀이 잘 아는 도메인에서 빠르게 개발/운영하는"** 시나리오에 적합합니다. 이 프로젝트처럼 측위라는 특화된 도메인에서 소수 개발자가 전체 시스템을 장악하고, 공간 쿼리 성능이 중요한 경우, Spring의 추상화 레이어가 오히려 불필요한 복잡성이 될 수 있습니다.
