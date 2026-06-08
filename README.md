# WebTerm

> **SSF (Simple Spring-like Framework)** — Java 기반 경량 MVC 웹 애플리케이션 프레임워크의 2026 버전

WebTerm은 Spring Framework와 유사한 어노테이션 기반 컨트롤러 라우팅, JDBC Template 패턴의 데이터 접근, JSP Forward 방식의 뷰 렌더링을 제공하는 경량 Java 웹 프레임워크입니다. 기존 WAR 배포뿐 아니라 **Embedded Tomcat 단독 실행**도 지원합니다.

---

## 핵심 특징

- 어노테이션 기반 URL 매핑 ([@ControllerClassInfo](src/com/ithows/base/ControllerClassInfo.java), [@ControllerMethodInfo](src/com/ithows/base/ControllerMethodInfo.java))
- 리플렉션 기반 요청 디스패칭 ([DispatcherServlet](src/com/ithows/base/DispatcherServlet.java))
- JDBC Template 패턴 데이터베이스 접근 ([JdbcDao](src/com/ithows/JdbcDao.java))
- [ResultMap](src/com/ithows/ResultMap.java) 기반 타입 안전한 결과 처리
- JSP Forward 방식 뷰 렌더링
- 선언적 보안 — `loginRequired`, `requiredSecurityLevel`, [@ApiKeyRequired](src/com/ithows/base/ApiKeyRequired.java)
- 통합 보안 필터 ([SecurityFilter](src/com/ithows/base/SecurityFilter.java)) — HTTP 보안 헤더, XSS 방어, CSRF 토큰, Rate Limiting
- `X-API-Key` 헤더 기반 API Key 인증 — 메서드 단위 선택 적용
- [@ApiInfo](src/com/ithows/base/ApiInfo.java) 어노테이션 기반 자동 API 문서화 (Swagger / OpenAPI 3.0)
- Embedded Tomcat 실행 지원 ([EmbeddedApplication](src/com/ithows/EmbeddedApplication.java))
- 파이썬 스크립트 연동 ([PythonCallUtil](src/com/ithows/util/PythonCallUtil.java)) — JSON 파일 기반 IPC, OS 별 경로 자동 분기
- 단일 클래스에 정리된 패턴 카탈로그 ([TutorialController](src/com/ithows/controller/TutorialController.java)) — 헬스체크 / CRUD / 검색 / 트랜잭션 / 파일 업로드·다운로드 / 파이썬 호출 / API Key·로그인 보안 등 15개 엔드포인트

---

## 기술 스택

| 구분 | 내용 |
|------|------|
| 언어 / JDK | Java 8 (`maven.compiler.source=1.8`) |
| 서블릿 API | Servlet 3.1 / 3.0+ 호환 |
| 빌드 | Maven ([pom.xml](pom.xml), [pom-embedded.xml](pom-embedded.xml)), Ant ([build.xml](build.xml)) |
| DB | MariaDB / MySQL + Tomcat JDBC Connection Pool |
| JSON | `org.json`, Jackson 2.15 |
| 리플렉션 스캔 | Reflections 라이브러리 |
| 뷰 | JSP Forward |

---

## 프로젝트 구조

```
WebTerm/
├── src/com/ithows/              # 프레임워크 + 애플리케이션 코드
│   ├── base/                    # 프레임워크 핵심 (DispatcherServlet, SecurityFilter 등)
│   ├── controller/              # 컨트롤러 (*.do 엔드포인트)
│   ├── dao/                     # DAO 레이어
│   ├── service/                 # 서비스/도메인 로직
│   ├── util/                    # 유틸리티
│   ├── AppConfig.java           # 앱 설정 로더
│   ├── JdbcDao.java             # JDBC Template
│   ├── ResultMap.java           # 결과 래퍼
│   └── EmbeddedApplication.java # Embedded Tomcat 엔트리
├── src/com/sox/ltex/            # 측위/지리공간 도메인 모듈
├── web/                         # 웹 리소스 (JSP, JS, CSS, images)
│   └── WEB-INF/
│       ├── web.xml
│       ├── dispatcher-servlet.xml
│       └── jsp/
├── conf/                        # 런타임 설정
│   ├── configplatform.xml       # 애플리케이션 설정
│   ├── connpool.xml             # DB 커넥션 풀
│   └── log4j.properties
├── lib/                         # 외부 JAR
├── python_process/              # 파이썬 연동 스크립트 (수동 설치)
│   ├── tutorial_echo.py         # 샘플 — 숫자 배열 통계
│   └── tutorial_text_stats.py   # 샘플 — 텍스트 통계 분석
├── docs/                        # 문서
│   ├── Embedded_Tomcat_Guide.md
│   └── Developer_Manual.md      # 웹 애플리케이션 개발 매뉴얼
├── pom.xml                      # WAR 빌드용
├── pom-embedded.xml             # Embedded 실행용
├── embedded-build.sh / .bat     # Embedded 빌드 스크립트
├── embedded-run.sh / .bat       # Embedded 실행 스크립트
├── rename-project.ps1           # 프로젝트 이름 일괄 변경 스크립트
├── new-ssf-project.ps1          # 신규 프로젝트 생성 스크립트 (대화형 / JSON 설정 파일)
├── .scaffold-ignore             # 프로젝트 생성 시 복사 제외 목록
└── make_project/                # 생성된 프로젝트 기본 저장 위치 (복사 제외됨)
```

### 패키지 레이아웃 (핵심)

| 패키지 | 역할 |
|--------|------|
| [com.ithows.base](src/com/ithows/base/) | 프레임워크 코어 — DispatcherServlet, SecurityFilter, ServiceInterceptor, SwaggerServlet, XssRequestWrapper, PageBeanContainer, CommandManager 등 |
| [com.ithows.controller](src/com/ithows/controller/) | 컨트롤러 — APIController, ServiceController, UserController, HealthController, WelcomeController, **TutorialController** (패턴 학습용 15개 엔드포인트) |
| [com.ithows.dao](src/com/ithows/dao/) | DAO — UserDAO, UserKeyDAO, ConfigDAO, FileDAO, PoiDAO |
| [com.ithows.service](src/com/ithows/service/) | 서비스 — FileManager, OndeviceModelFunctions, Websocket 등 |
| [com.ithows.util](src/com/ithows/util/) | 유틸 — DateTimeUtils, DBUtils, UtilString, UtilJSON, NetUtils, **PythonCallUtil** 등 |
| [com.sox.ltex](src/com/sox/ltex/) | 측위 도메인 — LTE/WiFi/BLE/GPS 데이터, GeoOperator, CoordTransformUtil 등 |
| [python_process/](python_process/) | 외부 파이썬 스크립트 (Java 외부 리소스) — `PythonCallUtil` 이 실행 |

---

## 아키텍처

### 요청 처리 흐름

```
HTTP Request (*.do)
    ↓
SecurityFilter
    ├─ 정적 리소스 판별 → 스킵
    ├─ HTTP 보안 헤더 설정
    ├─ XssRequestWrapper 적용 (XSS 필터링)
    ├─ Rate Limiting 체크 (외부 API)
    └─ CSRF 토큰 검증/생성 (옵션)
    ↓
DispatcherServlet.process()
    ├─ URL → PageBean 조회 (PageBeanContainer)
    ├─ @ApiKeyRequired 체크 (loginRequired와 독립, 먼저 처리)
    │     └─ ServiceInterceptor.checkApiKey()
    │           ├─ X-API-Key 헤더 누락 → "Missing API Key"
    │           ├─ UserKeyDAO.checkAPIKey() 불일치 → "Invalid API Key"
    │           └─ 실패 시 resultJson.jsp로 forward (컨트롤러 미호출)
    ├─ ServiceInterceptor.checkPermission()
    │     ├─ 미로그인 → redirect:/login.do
    │     ├─ 보안레벨 부족 → redirect:/accessDenied.do
    │     └─ 허용 → 통과
    ├─ CommandManager로 Command 객체 바인딩
    └─ 리플렉션으로 컨트롤러 메서드 호출
    ↓
Controller Method
    ├─ HttpUtil.getParameterXxx() / getBodyJson()
    ├─ DAO 호출 → 비즈니스 로직
    ├─ request.setAttribute(result/msg/resultMap/resultList)
    └─ return JSP 경로 또는 특수 상수
    ↓
JSP Forward → JSON/HTML Response
```

### MVC 레이어 개요

- **Controller** — `*.do` 엔드포인트. 메서드 시그니처: `(HttpSession, HttpServletRequest, HttpServletResponse, Object command)`, 반환은 JSP 경로 또는 `RESULT_COMMON_JSON` / `RESULT_PAGE_JSON` / `NO_PAGE` 상수
- **DAO** — `JdbcDao`의 static 메서드(`queryForMap`, `queryForMapList`, `update` 등) 호출, `ResultMap`으로 결과 수신
- **View** — `/WEB-INF/jsp/` 하위 JSP로 Forward. 공용 템플릿: `resultJson.jsp`, `resultRawJson.jsp`, `resultPageJson.jsp`

---

## 보안 (Security Layer)

[SecurityFilter](src/com/ithows/base/SecurityFilter.java)가 모든 `*.do` 요청을 가로채 다음을 일괄 처리합니다.

### HTTP 보안 헤더

| 헤더 | 값 |
|------|-----|
| `X-Frame-Options` | `SAMEORIGIN` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` |
| `X-Content-Type-Options` | `nosniff` |
| `X-XSS-Protection` | `1; mode=block` |
| `Content-Security-Policy` | 도메인별 정책 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(self)` |
| `Cache-Control` | `no-store, no-cache, must-revalidate` |

### XSS 방어

[XssRequestWrapper](src/com/ithows/base/XssRequestWrapper.java)가 `getParameter`/`getParameterValues`/`getParameterMap`/`getHeader`를 오버라이드하여 `<`, `>`, `"`, `'`, `&` 이스케이프 및 `<script>`, `on*` 핸들러, `javascript:` 프로토콜을 제거합니다.

### CSRF 토큰 (옵션)

- `web.xml`에서 `csrfEnabled=true`로 활성화
- 세션 생성 시 `SecureRandom` 기반 64자리 hex 토큰 발급
- POST 요청에서 `_csrf` 파라미터 또는 `X-CSRF-TOKEN` 헤더 검증
- 외부 API / 로그인·로그아웃 경로는 검증 제외

### Rate Limiting

- `ConcurrentHashMap` 기반 슬라이딩 윈도우
- 기본값 — `rateLimitMaxRequests=100`, `rateLimitWindowMs=60000` (1분)
- 초과 시 `HTTP 429 Too Many Requests` 반환
- 적용 대상: `/authLocation.do`, `/findFLocation.do`, `/getServerPosition.do`, `/getPosition.do`, `/getCellids.do`, `/getLTECellInfo.do`

### 역할 기반 접근 제어 (RBAC)

[ServiceInterceptor](src/com/ithows/base/ServiceInterceptor.java)가 `@ControllerMethodInfo`의 `loginRequired` / `requiredSecurityLevel`을 기반으로 검증합니다.

| 레벨 | 설명 |
|:----:|------|
| 0 | 모든 사용자 (기본값) |
| 1 | General 이상 |
| 2 | Super 이상 |
| 3 | Admin 전용 |

### API Key 인증 (`@ApiKeyRequired`)

특정 엔드포인트를 `X-API-Key` 헤더로만 접근 가능하도록 제한합니다. [@ApiKeyRequired](src/com/ithows/base/ApiKeyRequired.java)가 붙은 컨트롤러 메서드는 `DispatcherServlet`이 실행 전에 [ServiceInterceptor.checkApiKey()](src/com/ithows/base/ServiceInterceptor.java)로 헤더를 검증합니다.

**특징:**
- **메서드 단위 선택 적용** — 어노테이션이 붙은 메서드에만 적용
- **로그인과 독립** — `loginRequired` / `requiredSecurityLevel`과 무관하게 동작, 권한 체크보다 먼저 실행
- **검증 소스** — [UserKeyDAO.checkAPIKey()](src/com/ithows/dao/UserKeyDAO.java) — `AppConfig.getConf("common_api_key")` 값과 비교
- **실패 응답** — 기존 에러 패턴(`result: "ERROR"` + `msg`)으로 [simpleResultJson.jsp](web/WEB-INF/jsp/simpleResultJson.jsp) 포맷의 JSON 반환, 감사 로그 자동 기록

**사용 예시** ([APIController.helloWorld](src/com/ithows/controller/APIController.java)):
```java
@ControllerMethodInfo(id = "/api/helloWorld.do")
@ApiKeyRequired
public String helloWorld(HttpSession session, HttpServletRequest request,
                         HttpServletResponse response, Object command) throws Exception {
    request.setAttribute("result", "OK");
    request.setAttribute("msg", "Success");
    request.setAttribute("data", "Hello World");
    return "RESULT_PAGE_JSON";
}
```

**설정** — [conf/configplatform.xml](conf/configplatform.xml)에 API Key 등록:
```xml
<entry key="common_api_key">발급할_API_KEY_값</entry>
```

**호출 예시:**
```bash
# 성공
curl -H "X-API-Key: <common_api_key 값>" http://localhost:8088/api/helloWorld.do
# → {"result":"OK","msg":"Success","data":"Hello World"}

# 실패 — 키 누락
curl http://localhost:8088/api/helloWorld.do
# → {"result":"ERROR","msg":"Missing API Key", ...}

# 실패 — 키 불일치
curl -H "X-API-Key: wrong" http://localhost:8088/api/helloWorld.do
# → {"result":"ERROR","msg":"Invalid API Key", ...}
```

### 감사 로그

`ServiceInterceptor.auditLog()`가 `LOGIN`, `LOGOUT`, `ACCESS_DENIED` 이벤트를 구조화된 형식으로 기록합니다. `@ApiKeyRequired` 검증 실패도 `ACCESS_DENIED`로 기록됩니다.

```
[AUDIT] ACCESS_DENIED | IP=192.168.1.1 | URI=/admin/manage.do | Method=GET | SessionID=... | level=1 → required=3
```

---

## 파이썬 스크립트 연동

자바로 구현하기 번거로운 작업(수치 계산·텍스트 분석·ML 추론 등)을 파이썬에 위임하기 위한 경량 IPC 유틸리티를 제공합니다.

### 구조

```
[Java]  PythonCallUtil.callPython(script, requestJson, timeout)
   │
   ▼  임시 요청 파일 작성
[Python]  python script.py --request req.json --response res.json
   │
   ▼  응답 파일 작성
[Java]  응답 JSON 파싱 → JSONObject 반환 (실패 시 {"result":"ERROR","msg":...})
```

- **파일 기반 IPC** — stdin/stdout 대신 JSON 파일로 데이터 전달 (대용량·멀티라인 안전)
- **타임아웃** — 지정 시간 초과 시 프로세스 강제 종료
- **에러 흡수** — stderr→stdout 병합해 비동기 수집, 임시파일 자동 정리
- **스크립트 업로드 미지원** — 보안상 서버에 **수동 설치**만 허용 (path traversal 방어 포함)

### 설정 ([configplatform.xml](web/WEB-INF/classes/configplatform.xml))

```xml
<entry key="python_command">C:\Python310\python.exe</entry>

<!-- 디렉토리는 OS 별 컨텍스트 루트(context_win_dir / context_dir) 하위의 "상대 경로"
     실제 절대 경로 = AppConfig.getContextPath() + 아래 값 -->
<entry key="python_script_dir">python_process/</entry>
<entry key="python_temp_dir">python_process/temp/</entry>

<!-- python --version 프로브 타임아웃(초). 미설정 시 5초. -->
<entry key="python_version_timeout_sec">5</entry>
```

| 키 | 기본값 | 설명 |
|----|--------|------|
| `python_command` | `python` | 파이썬 실행 명령 |
| `python_script_dir` | `$user.dir/python_process` | 스크립트 폴더. 상대경로면 `AppConfig.getContextPath()` 와 결합 |
| `python_temp_dir` | `java.io.tmpdir` | 요청/응답 임시 파일 폴더. 상대경로면 컨텍스트 루트와 결합 |
| `python_version_timeout_sec` | `5` | `pythonInfo.do` 의 `python --version` 프로브 타임아웃(초) |

### OS 별 경로 자동 분기

`python_script_dir` / `python_temp_dir` 가 **상대 경로**이면 [PythonCallUtil](src/com/ithows/util/PythonCallUtil.java) 가 [AppConfig.getContextPath()](src/com/ithows/AppConfig.java) 와 결합해 절대 경로로 만듭니다. `getContextPath()` 가 OS 에 따라 자동 분기하므로 (Windows → `context_win_dir`, Linux → `context_dir`) 같은 설정 파일로 양 OS 에 배포할 수 있습니다.

| OS | 컨텍스트 루트 | 결합 결과 |
|----|---------------|-----------|
| Windows | `C:\03_work\WebTerm\build\web\` | `C:\03_work\WebTerm\build\web\python_process\` |
| Linux | `/locationService/tomcat/tomcatweb/webapps/WebTerm/` | `/locationService/tomcat/tomcatweb/webapps/WebTerm/python_process/` |

특정 환경에서만 다른 위치를 쓰려면 `python_script_dir` / `python_temp_dir` 에 절대 경로를 넣으면 그 값이 그대로 쓰입니다 (override).

### 샘플

- [python_process/tutorial_echo.py](python_process/tutorial_echo.py) — 숫자 배열 통계 (기본 호출 규약 샘플)
- [python_process/tutorial_text_stats.py](python_process/tutorial_text_stats.py) — 정규식/Counter 를 이용한 텍스트 통계

스크립트 규약·호출 예시·트러블슈팅은 [docs/Developer_Manual.md §Step 8](docs/Developer_Manual.md) 에 상세히 설명되어 있습니다.

### 진단

동작이 이상할 때 — `GET /tutorial/pythonInfo.do` 호출로 현재 해석된 경로(`context_path` 포함), 스크립트 목록, 파이썬 버전을 확인할 수 있습니다. (운영 배포 시엔 제거 또는 Admin 전용으로 보호)

---

## API 문서화 (Swagger / OpenAPI 3.0)

[SwaggerServlet](src/com/ithows/base/SwaggerServlet.java)이 `@ControllerMethodInfo` / [@ApiInfo](src/com/ithows/base/ApiInfo.java) 어노테이션을 리플렉션으로 스캔하여 OpenAPI 3.0 스펙과 Swagger UI를 자동 생성합니다.

| URL | 설명 |
|-----|------|
| `/docs/` | Swagger UI |
| `/docs/api-docs` | OpenAPI 3.0 JSON |
| `/docs/api-docs?refresh=true` | 스펙 강제 재생성 |

```java
@ControllerMethodInfo(id = "/authLocation.do")
@ApiInfo(
    summary = "위치 인증",
    description = "복합신호 기반 위치 인증 API",
    tag = "인증 API",
    method = "POST",
    parameters = {
        @ApiInfo.Param(name = "req_posmethod", type = "string", required = true,
                       description = "측위 방식 (AGNSS/WiFi/CellID/Fused)"),
        @ApiInfo.Param(name = "latitude", type = "number",
                       description = "위도", example = "37.5665")
    },
    responseDescription = "인증 결과 및 매칭 정보"
)
public String authLocation(...) { ... }
```

`@ApiInfo`가 없는 엔드포인트도 `@ControllerMethodInfo`만으로 기본 문서가 자동 생성됩니다.

---

## 컨트롤러 작성 가이드

### 어노테이션

```java
@ControllerClassInfo(controllerPage = "/api/_api.jsp")
public class APIController {

    @ControllerMethodInfo(
        id = "/api/checkHealth.do",
        loginRequired = false,
        requiredSecurityLevel = 0
    )
    public String checkHealth(HttpSession session, HttpServletRequest request,
                              HttpServletResponse response, Object command) {
        return "RESULT_COMMON_JSON";
    }
}
```

**`@ControllerMethodInfo` 속성**

| 속성 | 기본값 | 설명 |
|------|--------|------|
| `id` | (필수) | URL 매핑 경로 |
| `controllerPage` | - | 메서드 전용 뷰 (클래스 설정 오버라이드) |
| `commandClass` | - | 파라미터 바인딩용 Command 클래스 |
| `commandName` | - | Command 객체의 request attribute 이름 |
| `version` | - | API 버전 관리 (높은 버전 우선) |
| `loginRequired` | `false` | 로그인 필요 여부 |
| `requiredSecurityLevel` | `0` | 접근 허용 최소 보안 레벨 |

**추가 인증 어노테이션**

| 어노테이션 | 대상 | 설명 |
|-----------|------|------|
| [@ApiKeyRequired](src/com/ithows/base/ApiKeyRequired.java) | 메서드 | `X-API-Key` 헤더 검증 — [보안 > API Key 인증](#api-key-인증-apikeyrequired) 참조 |

### 반환값

- JSP 경로 — `"/api/result.jsp"` → `/WEB-INF/jsp/api/result.jsp`로 Forward
- `"NO_PAGE"` — 추가 뷰 렌더링 없음
- `"RESULT_COMMON_JSON"` — `resultJson.jsp` 사용
- `"RESULT_PAGE_JSON"` — `resultPageJson.jsp` 사용

### 파라미터 추출

```java
String searchType = HttpUtil.getParameterString(request, "searchType", "server");
int pageNo = HttpUtil.getParameterInt(request, "pageNo", 1);
double minX = HttpUtil.getParameterDouble(request, "minX", -1);
long id = HttpUtil.getParameterLong(request, "id", -1);
boolean active = HttpUtil.getParameterBoolean(request, "active");

JSONObject jParam = HttpUtil.getBodyJson(request);   // POST body
```

### 응답 설정

```java
request.setAttribute("result", "OK");              // OK / NO / ERROR / ERR
request.setAttribute("msg", "Success");
request.setAttribute("restime", DateTimeUtils.getTimeDateNow());
request.setAttribute("resultMap", resultMap);       // 단건
request.setAttribute("resultList", resultList);     // 목록
```

### 표준 에러 코드

| 코드 | 의미 |
|------|------|
| `OK` | 성공 |
| `NO` | 데이터 없음 |
| `ERROR` | 검증/비즈니스 오류 |
| `ERR` | 시스템 오류 |

---

## DAO 사용법

### JdbcDao 주요 메서드

```java
List<ResultMap> list = JdbcDao.queryForMapList(sql, params);
ResultMap        map  = JdbcDao.queryForMap(sql, params);
String           str  = JdbcDao.queryForString(sql, params);
int              cnt  = JdbcDao.queryForInt(sql, params);
long             id   = JdbcDao.queryForLong(sql, params);

int rowCount = JdbcDao.update(sql, params);         // INSERT/UPDATE/DELETE
JdbcDao.executeCallable(sql, params);               // 저장 프로시저

JdbcDao.beginTransaction();
// ...
JdbcDao.commit();   // or JdbcDao.rollback();
```

### ResultMap

```java
String name   = map.getString("name");
int    age    = map.getInt("age", 0);                 // 기본값 지원
long   id     = map.getLong("id");
double score  = map.getDouble("score");
byte[] data   = map.getBytes("data");
```

### SQL 인젝션 방지

**반드시 파라미터화된 쿼리를 사용하세요. 문자열 연결은 금지입니다.**

```java
// OK
String sql = "SELECT * FROM user WHERE userId = ?";
JdbcDao.queryForMap(sql, new Object[]{userId});

// 금지
String sql = "SELECT * FROM user WHERE userId = '" + userId + "'";
```

---

## URL 매핑 규칙

**형식:** `/{모듈}/{동작}.do`

| 예시 | 설명 |
|------|------|
| `/authLocation.do` | 외부 API (루트 레벨) |
| `/api/requestNewKey.do` | 외부 API (`/api/`) |
| `/api/checkHealth.do` | 헬스체크 |
| `/app/registDevice.do` | 디바이스 API |
| `/admin/getRequestlogList.do` | 관리자 (로그인 필요) |
| `/service/searchLogData.do` | 서비스 (로그인 필요) |

---

## 설정 파일

| 파일 | 역할 |
|------|------|
| [web/WEB-INF/web.xml](web/WEB-INF/web.xml) | 서블릿/필터 매핑 (DispatcherServlet, SecurityFilter, SwaggerServlet, CharacterEncodingFilter) |
| [web/WEB-INF/dispatcher-servlet.xml](web/WEB-INF/dispatcher-servlet.xml) | 템플릿 페이지 정의 |
| [conf/configplatform.xml](conf/configplatform.xml) | 앱 설정 — 도메인, OS 별 컨텍스트 루트(`context_win_dir` / `context_dir`), `common_api_key`, `python_*` (파이썬 연동) 등 |
| [conf/connpool.xml](conf/connpool.xml) | DB 커넥션 풀 |
| [conf/log4j.properties](conf/log4j.properties) | 로깅 |

### web.xml 주요 설정

```xml
<!-- 세션 타임아웃 -->
<session-config><session-timeout>180</session-timeout></session-config>

<!-- SecurityFilter 파라미터 -->
<init-param>
    <param-name>rateLimitMaxRequests</param-name>
    <param-value>100</param-value>
</init-param>
<init-param>
    <param-name>rateLimitWindowMs</param-name>
    <param-value>60000</param-value>
</init-param>
```

---

## 신규 프로젝트 생성

[new-ssf-project.ps1](new-ssf-project.ps1)을 실행하면 WebTerm을 기반으로 한 새 프로젝트를 자동으로 구성합니다.  
대화형 모드와 JSON 설정 파일을 이용한 비대화형 모드를 모두 지원합니다.

### 실행

```powershell
# 대화형 모드 (SSF_2026 프로젝트 루트에서 실행)
.\new-ssf-project.ps1

# 다른 위치의 SSF 소스를 사용할 경우
.\new-ssf-project.ps1 -SourceRoot C:\other\SSF_2026

# JSON 설정 파일 템플릿 생성 (ssf-project-config.json 생성 후 종료)
.\new-ssf-project.ps1 -GenerateConfig

# JSON 설정 파일로 비대화형 실행
.\new-ssf-project.ps1 -ConfigFile ssf-project-config.json

# JSON 설정 파일 + 확인 프롬프트 없이 완전 자동 실행
.\new-ssf-project.ps1 -ConfigFile ssf-project-config.json -Force
```

### 파라미터

| 파라미터 | 설명 |
|----------|------|
| `-GenerateConfig` | `ssf-project-config.json` 템플릿 생성 후 종료 |
| `-ConfigFile <경로>` | JSON 파일을 읽어 대화형 입력 없이 자동 실행 |
| `-Force` | 덮어쓰기·진행 확인 프롬프트 생략 (JSON 모드와 주로 함께 사용) |
| `-SourceRoot <경로>` | 원본 SSF 프로젝트 루트 (기본값: 스크립트 위치) |

### 권장 사용 순서 (JSON 모드)

```powershell
# 1) 템플릿 생성
.\new-ssf-project.ps1 -GenerateConfig

# 2) ssf-project-config.json 편집 (projectName, groupId 등 설정)

# 3) 실행
.\new-ssf-project.ps1 -ConfigFile ssf-project-config.json -Force
```

### JSON 설정 파일 (`ssf-project-config.json`)

`-GenerateConfig`로 생성되는 템플릿 파일입니다. 빈 값(`""`)은 자동 계산됩니다.

```json
{
  "_comment":        "new-ssf-project.ps1 설정 파일. 빈 값(\"\")은 자동 계산됩니다.",
  "projectName":     "MyNewProject",
  "groupId":         "com.example",
  "siteDesc":        "My New Project Service",
  "targetParent":    "C:\\01_project\\03_Roadmap\\SSF_2026\\make_project",
  "dbHost":          "127.0.0.1",
  "dbPort":          "3307",
  "dbName":          "collectdata",
  "winContextDir":   "",
  "linuxContextDir": "",
  "pythonExe":       "C:\\Python310\\python.exe",
  "apiKey":          "sox_api_key_2018",
  "gitInit":         false
}
```

#### JSON 필드 설명

| 필드 | 필수 | 기본값 | 설명 |
|------|:----:|--------|------|
| `projectName` | ✓ | — | 프로젝트명 (영문 시작, 영숫자 / `_` / `-`) |
| `groupId` | ✓ | — | Java 패키지 Group ID (소문자, 점 구분, 2단계 이상) |
| `siteDesc` | | `{projectName} Service` | `configplatform.xml`의 `site_domain` 값 |
| `targetParent` | | `{SourceRoot}\make_project` | 프로젝트 폴더가 생성될 상위 디렉토리 |
| `dbHost` | | `127.0.0.1` | DB 호스트 |
| `dbPort` | | `3307` | DB 포트 |
| `dbName` | | `collectdata` | DB 이름 |
| `winContextDir` | | `{생성위치}\build\web\` | Windows `context_win_dir` (빈 값 → 자동 계산) |
| `linuxContextDir` | | `/opt/tomcat/webapps/{프로젝트명}/` | Linux `context_dir` (빈 값 → 자동 계산) |
| `pythonExe` | | `C:\Python310\python.exe` | Python 실행 파일 경로 |
| `apiKey` | | `sox_api_key_2018` | `common_api_key` 값 |
| `gitInit` | | `false` | `true`로 설정 시 생성 완료 후 `git init` 자동 실행 |

### 입력 항목 — 대화형 모드 (3단계)

| 단계 | 항목 | 기본값 | 설명 |
|------|------|--------|------|
| 1 | 프로젝트명 | — | 영문 시작, 영숫자 / `_` / `-` 허용 (예: `MyNewService`) |
| 1 | Java 패키지 | `com.example` | Group ID (예: `com.mycompany`) |
| 1 | 서비스 설명 | `{프로젝트명} Service` | `site_domain` 값 |
| 2 | 생성 위치 | `{SourceRoot}\make_project` | 프로젝트 폴더가 생성될 상위 디렉토리 |
| 3 | DB 호스트 / 포트 / 이름 | `127.0.0.1` / `3307` / `collectdata` | `connpool.xml` |
| 3 | Windows 빌드 경로 | `{생성위치}\{프로젝트명}\build\web\` | `context_win_dir` |
| 3 | Linux 배포 경로 | `/opt/tomcat/webapps/{프로젝트명}/` | `context_dir` |
| 3 | Python 실행 파일 | `C:\Python310\python.exe` | `python_command` |
| 3 | API Key | `sox_api_key_2018` | `common_api_key` |

### 처리 단계

스크립트는 설정을 확인한 후 5단계를 순서대로 수행합니다.

```
[단계 1/5] 소스 파일 복사
           robocopy 기반 — .scaffold-ignore 에 정의된 항목 제외
           기본 생성 위치: {SourceRoot}\make_project\{프로젝트명}\

[단계 2/5] 텍스트 치환
           WebTerm → 신규 프로젝트명
           com.ithows → 신규 Java 패키지
           DB URL, 경로, API Key 등 설정값 일괄 치환

[단계 3/5] 소스 패키지 디렉토리 재편성
           src/com/ithows/ → src/{패키지경로}/

[단계 4/5] 파일명 변경
           WebTerm.iml 등 프로젝트명이 포함된 파일명 일괄 변경

[단계 5/5] Git 초기화 (선택)
           git init + 초기 커밋
```

### 복사 제외 설정 (`.scaffold-ignore`)

[.scaffold-ignore](.scaffold-ignore) 파일이 복사에서 제외할 항목을 정의합니다. `.gitignore`와 동일한 문법을 사용합니다.

```
# 디렉토리 (이름 완전 일치)
.git/
target/
make_project/       ← 생성된 프로젝트 폴더 자체를 제외 (무한 복사 방지)

# 디렉토리 (접두어 매칭)
tomcat.*/           ← tomcat.8088, tomcat.9090 등

# 파일 패턴
*.class
*.log
```

이 파일은 생성된 프로젝트에도 함께 복사되므로, 파생 프로젝트에서도 동일하게 동작합니다.

### 생성 후 확인 사항

| 항목 | 위치 | 설명 |
|------|------|------|
| DB 계정 | `web/WEB-INF/classes/connpool.xml` | `username` / `password` 추가 필요 |
| 경로·도메인 | `web/WEB-INF/classes/configplatform.xml` | 최종 경로 확인 |
| 로컬 JAR 경로 | `pom.xml` (`<systemPath>`) | `ojdbc6.jar` 등 절대경로 확인 |
| IDE 재로드 | `.idea/` 또는 `nbproject/` | IDE에서 프로젝트 열기 → Maven 재임포트 |

---

## 빌드 및 실행

### WAR 빌드 (외부 Tomcat 배포)

```bash
mvn clean package
# → target/WebTerm-1.0-SNAPSHOT.war
```

외부 Tomcat의 `webapps/`에 배포합니다.

### Embedded Tomcat 단독 실행

별도 WAS 설치 없이 `java` 하나로 실행합니다. 자세한 내용은 [docs/Embedded_Tomcat_Guide.md](docs/Embedded_Tomcat_Guide.md) 참조.

**빌드:**
```bash
# Windows
embedded-build.bat

# Linux/macOS
./embedded-build.sh
```

**실행:**
```bash
# Windows
embedded-run.bat

# Linux/macOS
./embedded-run.sh
```

Embedded 모드의 엔트리 포인트는 [com.ithows.EmbeddedApplication](src/com/ithows/EmbeddedApplication.java)입니다.

---

## 모범 사례 요약

- **파라미터 검증** — 항상 기본값을 넘기고, 필수 파라미터는 초기에 검사 후 조기 리턴
- **Null 안전** — DAO 결과는 `null` / `isEmpty()` 모두 확인
- **일관된 에러 응답** — `OK` / `NO` / `ERROR` / `ERR` + 의미 있는 `msg`
- **파라미터화된 SQL** — 문자열 연결 절대 금지
- **선언적 보안** — 프로그래밍 체크보다 `@ControllerMethodInfo(loginRequired=true, requiredSecurityLevel=N)` 선호
- **트랜잭션** — `JdbcDao.beginTransaction()` / `commit()` / `rollback()`로 명시적 관리
- **XSS** — SecurityFilter가 입력을 자동 필터링하지만 JSP 출력에도 `<c:out>` / `fn:escapeXml` 병행

---

## 관련 문서

- [docs/Developer_Manual.md](docs/Developer_Manual.md) — 웹 애플리케이션 개발 매뉴얼 (단계별 튜토리얼 + 파이썬 연동)
- [SSF_Framework_RAG_Document.md](SSF_Framework_RAG_Document.md) — 프레임워크 상세 레퍼런스 (RAG 검색용)
- [SSF_vs_Spring_Analysis.md](SSF_vs_Spring_Analysis.md) — Spring Framework와의 기능 비교
- [docs/Embedded_Tomcat_Guide.md](docs/Embedded_Tomcat_Guide.md) — Embedded Tomcat 전환 가이드
