# SSF 프레임워크 문서

## 메타데이터
- 프레임워크 이름: SSF (Simple Spring-like Framework)
- 유형: Java 웹 애플리케이션 프레임워크
- 아키텍처: MVC 패턴
- 어노테이션 기반: Yes
- 데이터베이스 접근: JDBC Template 패턴
- 뷰 기술: JSP Forward

---

## 1. 프레임워크 개요

### 1.1 SSF란?
SSF는 Spring Framework와 유사한 경량 Java 웹 애플리케이션 프레임워크입니다. 어노테이션 기반 컨트롤러 라우팅, 의존성 관리, JDBC Template을 통한 데이터베이스 접근을 제공합니다.

**주요 특징:**
- 어노테이션 기반 URL 매핑 (@ControllerClassInfo, @ControllerMethodInfo)
- 리플렉션 기반 요청 디스패칭
- JDBC Template 패턴의 데이터베이스 연산
- ResultMap을 통한 타입 안전한 데이터 처리
- JSP Forward 방식의 뷰 렌더링
- 선언적 보안 (loginRequired, requiredSecurityLevel)
- SecurityFilter를 통한 HTTP 보안 헤더, XSS 방어, CSRF 토큰, Rate Limiting
- @ApiInfo 어노테이션 기반 자동 API 문서화 (Swagger/OpenAPI 3.0)

**기술 스택:**
- Servlet API 3.0+
- Tomcat JDBC Connection Pool
- MariaDB/MySQL Driver
- JSON 처리 (org.json)
- Reflections 라이브러리 (어노테이션 스캔)

---

## 2. 컨트롤러 레이어

### 2.1 컨트롤러 어노테이션 구조

**클래스 레벨 어노테이션:**
```java
@ControllerClassInfo(controllerPage = "/api/_api.jsp")
public class APIController {
    // 컨트롤러 메서드
}
```

**속성:**
- `controllerPage`: 해당 컨트롤러의 기본 JSP 페이지

**메서드 레벨 어노테이션:**
```java
@ControllerMethodInfo(
    id = "/api/checkHealth.do",
    loginRequired = false,
    requiredSecurityLevel = 0
)
public String checkHealth(HttpSession session, HttpServletRequest request,
                          HttpServletResponse response, Object command) {
    // 구현부
    return "RESULT_COMMON_JSON";
}
```

**속성:**
- `id`: URL 매핑 경로 (필수)
- `controllerPage`: 메서드 전용 뷰 경로 (클래스 레벨 설정 오버라이드)
- `commandClass`: 파라미터 바인딩용 Command 클래스
- `commandName`: Command 객체의 request attribute 이름
- `version`: API 버전 관리 (동일 URL에 대해 높은 버전 우선)
- `loginRequired`: 로그인 필요 여부 (기본값 `false`)
- `requiredSecurityLevel`: 접근 허용 최소 보안 레벨 (기본값 `0`)

**보안 레벨 체계:**
| 레벨 | 설명 |
|:----:|------|
| 0 | 모든 사용자 (기본값) |
| 1 | General 이상 |
| 2 | Super 이상 |
| 3 | Admin 전용 |

### 2.2 표준 메서드 시그니처

```java
public String methodName(
    HttpSession session,           // 세션 객체
    HttpServletRequest request,    // 요청 객체
    HttpServletResponse response,  // 응답 객체
    Object command                 // Command 객체 (선택)
)
```

**반환값:**
- JSP 경로: `"/api/result.jsp"` → WEB-INF/jsp/api/result.jsp로 포워딩
- 특수 상수:
  - `"NO_PAGE"`: 추가 뷰 렌더링 없음
  - `"RESULT_COMMON_JSON"`: resultJson.jsp 사용
  - `"RESULT_PAGE_JSON"`: resultPageJson.jsp 사용

### 2.3 요청 파라미터 처리

**GET 파라미터:**
```java
String searchType = HttpUtil.getParameterString(request, "searchType", "server");
int pageNo = HttpUtil.getParameterInt(request, "pageNo", 1);
double minX = HttpUtil.getParameterDouble(request, "minX", -1);
long id = HttpUtil.getParameterLong(request, "id", -1);
boolean active = HttpUtil.getParameterBoolean(request, "active");
```

**POST JSON Body:**
```java
JSONObject jParam = null;

if (request.getMethod().toLowerCase().equals("get")) {
    String paramStr = HttpUtil.getParameterString(request, "param", "");
    if (!paramStr.equals("")) {
        try {
            jParam = new JSONObject(paramStr);
        } catch (Exception e) {
            jParam = null;
        }
    }
} else {
    try {
        jParam = HttpUtil.getBodyJson(request);
    } catch (Exception e) {
        jParam = null;
    }
}
```

**JSONObject에서 값 추출:**
```java
String cellID = jParam.getString("cellID");
int mnc = jParam.getInt("mnc");
double longitude = jParam.getDouble("longitude");
long requestTime = jParam.getLong("request_time");
JSONArray measurements = jParam.getJSONArray("measurements");
```

### 2.4 응답 처리

**응답 속성 설정:**
```java
request.setAttribute("result", "OK");           // 결과 코드
request.setAttribute("msg", "Success");         // 메시지
request.setAttribute("restime", DateTimeUtils.getTimeDateNow());
request.setAttribute("resultMap", resultMap);   // 단건 결과
request.setAttribute("resultList", resultList); // 목록 결과
```

**응답 JSP 템플릿:**

**resultJson.jsp** (표준 JSON 응답):
```json
{
    "result": "OK",
    "msg": "Success",
    "restime": "2024-01-01 12:00:00",
    "resultMap": {...},
    "count": 10,
    "resultList": [...]
}
```

**resultRawJson.jsp** (비이스케이프 JSON 응답):
```json
{
    "result": "OK",
    "resultMap": ${resultMap},
    "resultList": ${resultList}
}
```

### 2.5 에러 처리 패턴

```java
try {
    // 파라미터 검증
    if (jParam == null) {
        request.setAttribute("result", "ERROR");
        request.setAttribute("msg", "No Parameter");
        return "/api/resultJson.jsp";
    }

    // 데이터 검증
    if (minX == -1 || minY == -1) {
        request.setAttribute("result", "ERR");
        request.setAttribute("msg", "Invalid parameters");
        return "RESULT_COMMON_JSON";
    }

    // 비즈니스 로직
    ArrayList<ResultMap> list = dao.selectData();

    // 결과 검증
    if (list == null || list.isEmpty()) {
        request.setAttribute("result", "NO");
        request.setAttribute("msg", "No data");
        return "RESULT_COMMON_JSON";
    }

    // 성공 응답
    request.setAttribute("result", "OK");
    request.setAttribute("resultList", list);

} catch (Exception e) {
    e.printStackTrace();
    request.setAttribute("result", "ERROR");
    request.setAttribute("msg", "Server Error");
}

return "RESULT_COMMON_JSON";
```

---

## 3. 보안 레이어

### 3.1 SecurityFilter

`SecurityFilter`는 서블릿 필터로서 모든 `.do` 요청에 대해 보안 기능을 일괄 처리합니다.

**web.xml 설정:**
```xml
<filter>
    <filter-name>SecurityFilter</filter-name>
    <filter-class>com.ithows.base.SecurityFilter</filter-class>
    <init-param>
        <param-name>rateLimitMaxRequests</param-name>
        <param-value>100</param-value>
    </init-param>
    <init-param>
        <param-name>rateLimitWindowMs</param-name>
        <param-value>60000</param-value>
    </init-param>
</filter>
<filter-mapping>
    <filter-name>SecurityFilter</filter-name>
    <url-pattern>*.do</url-pattern>
</filter-mapping>
```

**처리 순서:**
1. 정적 리소스 판별 → 보안 처리 생략
2. HTTP 보안 헤더 설정
3. XSS 입력값 필터링 (XssRequestWrapper)
4. Rate Limiting (외부 API 엔드포인트)
5. CSRF 토큰 검증 (옵션, 기본 비활성화)
6. CSRF 토큰 생성

### 3.2 HTTP 보안 헤더

SecurityFilter가 모든 동적 요청에 자동 설정하는 보안 헤더:

| 헤더 | 값 | 용도 |
|------|-----|------|
| `X-Frame-Options` | `SAMEORIGIN` | 클릭재킹 방어 |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` | HTTPS 강제 (HSTS) |
| `X-Content-Type-Options` | `nosniff` | MIME 타입 스니핑 방지 |
| `X-XSS-Protection` | `1; mode=block` | 브라우저 XSS 필터 활성화 |
| `Content-Security-Policy` | (도메인별 정책) | CSP — 스크립트/스타일/이미지 출처 제한 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Referrer 정보 제한 |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(self)` | 브라우저 기능 제한 |
| `Cache-Control` | `no-store, no-cache, must-revalidate` | 동적 페이지 캐시 방지 |

### 3.3 XSS 방어 (XssRequestWrapper)

`XssRequestWrapper`는 `HttpServletRequestWrapper`를 상속하여 요청 파라미터와 헤더에서 위험한 문자를 자동 이스케이프합니다.

**오버라이드 메서드:**
- `getParameter()`, `getParameterValues()`, `getParameterMap()`, `getHeader()`

**필터링 규칙:**
```
< → &lt;       > → &gt;       " → &quot;      ' → &#39;      & → &amp;
<script> 태그 제거
on* 이벤트 핸들러 제거 (onclick, onerror 등)
javascript: 프로토콜 제거
```

**사용 예:**
```java
// SecurityFilter에서 자동 적용
HttpServletRequest wrappedRequest = new XssRequestWrapper(request);
chain.doFilter(wrappedRequest, response);
```

### 3.4 CSRF 토큰

CSRF 방어는 옵션 기능으로, `web.xml`에서 `csrfEnabled=true`로 활성화합니다.

**동작 방식:**
1. 세션 생성 시 `SecureRandom` 기반 64자리 hex 토큰 자동 생성
2. `DispatcherServlet`에서 `request.setAttribute("_csrf", token)` 으로 JSP에 전달
3. POST 요청 시 파라미터(`_csrf`) 또는 헤더(`X-CSRF-TOKEN`)에서 토큰 검증
4. 외부 API 및 로그인/로그아웃 경로는 CSRF 검증 제외

**JSP 폼에서의 사용:**
```html
<form method="post" action="update.do">
    <input type="hidden" name="_csrf" value="${_csrf}" />
    ...
</form>
```

**AJAX에서의 사용:**
```javascript
fetch('/service/update.do', {
    method: 'POST',
    headers: { 'X-CSRF-TOKEN': csrfToken },
    body: JSON.stringify(data)
});
```

### 3.5 Rate Limiting

IP 기반 Rate Limiting으로 외부 측위 API의 과도한 호출을 제한합니다.

**설정 파라미터:**
| 파라미터 | 기본값 | 설명 |
|---------|:------:|------|
| `rateLimitMaxRequests` | 100 | 윈도우당 최대 요청 수 |
| `rateLimitWindowMs` | 60000 | 윈도우 크기 (밀리초, 기본 1분) |

**적용 대상 경로:**
- `/authLocation.do`, `/findFLocation.do`, `/getServerPosition.do`
- `/getPosition.do`, `/getCellids.do`, `/getLTECellInfo.do`

**제한 초과 시 응답:**
```json
HTTP 429 Too Many Requests
{"result": 429, "msg": "Too many requests. Please try again later."}
```

**내부 구현:**
- `ConcurrentHashMap` 기반 슬라이딩 윈도우 카운터
- 엔트리 1000개 초과 시 만료 엔트리 자동 정리

### 3.6 역할 기반 접근 제어 (RBAC)

`ServiceInterceptor`가 `DispatcherServlet`에서 호출되어 `@ControllerMethodInfo`의 `loginRequired`와 `requiredSecurityLevel`을 기반으로 접근 권한을 검증합니다.

**처리 흐름:**
```
DispatcherServlet.process()
    ↓
PageBean의 loginRequired / requiredSecurityLevel 확인
    ↓
ServiceInterceptor.checkPermission()
    ├─ 미로그인 → redirect:/login.do
    ├─ 보안레벨 부족 → redirect:/accessDenied.do
    └─ null 반환 → 접근 허용
```

**사용 예:**
```java
@ControllerMethodInfo(
    id = "/admin/RequestLogView.do",
    loginRequired = true,
    requiredSecurityLevel = 1  // General 이상
)
public String requestLogView(...) { ... }
```

### 3.7 보안 감사 로그

`ServiceInterceptor.auditLog()`가 보안 관련 이벤트를 구조화된 형식으로 기록합니다.

**로그 형식:**
```
[AUDIT] ACCESS_DENIED | IP=192.168.1.1 | URI=/admin/manage.do | Method=GET | SessionID=a1b2c3d4... | User admin (level=1) attempted to access resource requiring level=3
```

**이벤트 유형:**
- `LOGIN`, `LOGOUT`: 인증 이벤트
- `ACCESS_DENIED`: 접근 거부

---

## 4. API 문서화 (Swagger/OpenAPI)

### 4.1 @ApiInfo 어노테이션

`@ApiInfo`는 `@ControllerMethodInfo`와 함께 사용하여 API 문서를 자동 생성합니다.

**사용 예:**
```java
@ControllerMethodInfo(id = "/authLocation.do")
@ApiInfo(
    summary = "위치 인증",
    description = "복합신호 기반 위치 인증 API",
    tag = "인증 API",
    method = "POST",
    parameters = {
        @ApiInfo.Param(name = "req_posmethod", type = "string",
                       description = "측위 방식 (AGNSS/WiFi/CellID/Fused)", required = true),
        @ApiInfo.Param(name = "latitude", type = "number",
                       description = "위도", example = "37.5665")
    },
    responseDescription = "인증 결과 및 매칭 정보"
)
public String authLocation(...) { ... }
```

**어노테이션 속성:**

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `summary` | String | `""` | API 요약 (한 줄) |
| `description` | String | `""` | API 상세 설명 |
| `tag` | String | `""` | API 그룹 태그 |
| `method` | String | `"GET/POST"` | HTTP 메서드 |
| `parameters` | Param[] | `{}` | 파라미터 목록 |
| `responseDescription` | String | `""` | 응답 설명 |

**@ApiInfo.Param 속성:**

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `name` | String | (필수) | 파라미터 이름 |
| `type` | String | `"string"` | 데이터 타입 (string/int/number/boolean/array/object) |
| `description` | String | `""` | 파라미터 설명 |
| `required` | boolean | `false` | 필수 여부 |
| `example` | String | `""` | 예시 값 |

### 4.2 SwaggerServlet

`SwaggerServlet`은 `@ControllerMethodInfo`와 `@ApiInfo` 어노테이션을 리플렉션으로 스캔하여 OpenAPI 3.0 JSON을 자동 생성하고 Swagger UI를 제공합니다.

**URL 매핑:**
| URL | 기능 |
|-----|------|
| `/docs/` | Swagger UI 페이지 (swagger-ui.jsp) |
| `/docs/api-docs` | OpenAPI 3.0 JSON |
| `/docs/api-docs?refresh=true` | 스펙 강제 재생성 |

**web.xml 설정:**
```xml
<servlet>
    <servlet-name>SwaggerServlet</servlet-name>
    <servlet-class>com.ithows.base.SwaggerServlet</servlet-class>
    <load-on-startup>2</load-on-startup>
</servlet>
<servlet-mapping>
    <servlet-name>SwaggerServlet</servlet-name>
    <url-pattern>/docs/*</url-pattern>
</servlet-mapping>
```

**자동 추론 기능:**
- `@ApiInfo`가 없는 엔드포인트도 `@ControllerMethodInfo`만으로 기본 문서 생성
- 컨트롤러 클래스명과 controllerPage에서 태그 자동 분류
- 메서드명에서 summary 자동 생성 (camelCase → 띄어쓰기)
- `loginRequired`/`requiredSecurityLevel` 정보 자동 표시

**공통 응답 스키마 (ApiResponse):**
```json
{
    "result": "OK/NO/ERROR",
    "msg": "결과 메시지",
    "resultMap": { ... },
    "resultList": [ ... ]
}
```

---

## 5. DAO 레이어

### 5.1 JdbcDao 메서드

**SELECT 연산:**
```java
// List<ResultMap> 반환
List<ResultMap> list = JdbcDao.queryForMapList(sql, params);

// 단건 ResultMap 반환
ResultMap map = JdbcDao.queryForMap(sql, params);

// 단건 String 반환
String value = JdbcDao.queryForString(sql, params);

// 단건 int 반환
int count = JdbcDao.queryForInt(sql, params);

// 단건 long 반환
long id = JdbcDao.queryForLong(sql, params);
```

**INSERT/UPDATE/DELETE 연산:**
```java
// 영향받은 행 수 반환
int rowCount = JdbcDao.update(sql, params);
```

**저장 프로시저:**
```java
JdbcDao.executeCallable(sql, params);
```

### 5.2 ResultMap 클래스

**타입 안전한 Getter:**
```java
ResultMap map = JdbcDao.queryForMap(sql, params);

String name = map.getString("name");
int age = map.getInt("age");
long id = map.getLong("id");
double score = map.getDouble("score");
byte[] data = map.getBytes("data");

// 기본값 지정
int count = map.getInt("count", 0);
String status = map.getString("status", "active");
```

### 5.3 표준 DAO 패턴

```java
public class UserDAO {

    // SELECT - 목록
    public static List<ResultMap> getAllUsers() {
        String sql = "SELECT * FROM user WHERE active = ?";
        try {
            return JdbcDao.queryForMapList(sql, new Object[]{true});
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // SELECT - 단건
    public static ResultMap getUserById(String userId) {
        String sql = "SELECT * FROM user WHERE userId = ?";
        try {
            return JdbcDao.queryForMap(sql, new Object[]{userId});
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // INSERT
    public static int insertUser(String userId, String userName, String password) {
        String sql = "INSERT INTO user (userId, userName, password) VALUES (?, ?, ?)";
        try {
            return JdbcDao.update(sql, new Object[]{userId, userName, password});
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // UPDATE
    public static int updateUser(String userId, String userName) {
        String sql = "UPDATE user SET userName = ? WHERE userId = ?";
        try {
            return JdbcDao.update(sql, new Object[]{userName, userId});
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // DELETE
    public static int deleteUser(String userId) {
        String sql = "DELETE FROM user WHERE userId = ?";
        try {
            return JdbcDao.update(sql, new Object[]{userId});
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // COUNT
    public static int countUsers() {
        String sql = "SELECT COUNT(*) FROM user";
        try {
            return JdbcDao.queryForInt(sql, new Object[]{});
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}
```

### 5.4 파라미터 바인딩

**위치 기반 파라미터:**
```java
String sql = "SELECT * FROM user WHERE userId = ? AND userClass = ?";
Object[] params = new Object[]{userId, userClass};
List<ResultMap> result = JdbcDao.queryForMapList(sql, params);
```

**복합 조건:**
```java
String sql = "SELECT * FROM logs WHERE " +
             "createTime BETWEEN ? AND ? " +
             "AND mnc = ? " +
             "AND tag LIKE ?";
Object[] params = new Object[]{startTime, endTime, mnc, "%" + tag + "%"};
```

---

## 6. 유틸리티 클래스

### 6.1 HttpUtil

```java
// 파라미터 추출
String value = HttpUtil.getParameterString(request, "key", "default");
int num = HttpUtil.getParameterInt(request, "num", 0);
double decimal = HttpUtil.getParameterDouble(request, "decimal", 0.0);
long bigNum = HttpUtil.getParameterLong(request, "bigNum", 0L);
boolean flag = HttpUtil.getParameterBoolean(request, "flag");

// JSON body
JSONObject json = HttpUtil.getBodyJson(request);

// 세션
SessionInfo sInfo = HttpUtil.getSessionInfo(session);

// 클라이언트 IP (X-Forwarded-For 지원)
String ip = HttpUtil.getClientIp(request);

// 파일 다운로드
HttpUtil.sendBinaryFileToClient(request, response, filePath);
```

### 6.2 DateTimeUtils

```java
// 현재 시간 문자열
String now = DateTimeUtils.getTimeDateNow();           // 2024-01-01 12:00:00
String now2 = DateTimeUtils.getTimeDateNow2();         // 20240101120000
long timestamp = DateTimeUtils.getTimestampNow();      // 밀리초 타임스탬프

// 타임스탬프 변환
String dateStr = DateTimeUtils.convertTimestampToDate(timestamp);

// 날짜 포맷
String formatted = DateTimeUtils.formatDate(date, "yyyy-MM-dd");
```

### 6.3 UtilString

```java
// 문자열 트리밍
String trimmed = UtilString.trimString(str, 1, false);  // 마지막 문자 제거

// 요소 연산
String distinct = UtilString.elementDistinctAndSort(str, ",");
int count = UtilString.countElementDistinct(str, ",");

// 배열 트리밍
String[] limited = UtilString.trimArray(str, 10);

// MAC 주소 변환
String mac = UtilString.convertMacString("aa:bb:cc:dd:ee:ff");

// 그룹 정렬
String[] sorted = UtilString.elementGroupDistinctAndSortByOther(
    keys, values, ",", false
);
```

### 6.4 UtilJSON

```java
// ArrayList → JSONArray
JSONArray arr = UtilJSON.convertArrayListToJSONArray(arrayList);

// ResultMap → JSONObject
JSONObject obj = UtilJSON.convertResultMapToJSONObject(resultMap);
```

### 6.5 DBUtils

```java
// ResultMap 리스트 정렬
DBUtils.sortResultMapList(list, "fieldName", descending);

// 리스트 필터링
List<ResultMap> filtered = DBUtils.filterList(list, "status", "active");
```

---

## 7. 요청 처리 흐름

```
HTTP Request (.do)
    ↓
SecurityFilter (서블릿 필터)
    ├─ HTTP 보안 헤더 설정
    ├─ XSS 필터링 (XssRequestWrapper)
    ├─ Rate Limiting 체크 (외부 API)
    └─ CSRF 토큰 검증/생성 (옵션)
    ↓
DispatcherServlet.doGet() / doPost()
    ↓
DispatcherServlet.process()
    ├─ URL 추출: /api/getData.do
    ├─ PageBeanContainer에서 PageBean 조회
    ├─ 권한 체크 (loginRequired, requiredSecurityLevel)
    │   └─ ServiceInterceptor.checkPermission()
    ├─ CommandManager 실행 (Request → Command 객체 변환)
    ├─ CSRF 토큰을 request attribute로 전달
    ├─ 리플렉션으로 컨트롤러 메서드 호출
    └─ 반환값 (JSP 경로) 획득
    ↓
Controller Method 실행
    ├─ HttpUtil.getParameterXxx() - 파라미터 추출
    ├─ 파라미터 검증
    ├─ DAO 메서드 호출
    ├─ 비즈니스 로직 처리
    ├─ request.setAttribute() - 응답 데이터 설정
    └─ return JSP 경로 또는 특수 상수
    ↓
JSP 렌더링 (Forward)
    ├─ resultJson.jsp
    ├─ resultRawJson.jsp
    └─ 커스텀 뷰 페이지
    ↓
JSON/HTML Response
```

---

## 8. 설정 파일

### 8.1 web.xml

```xml
<!-- DispatcherServlet -->
<servlet>
    <servlet-name>DispatcherServlet</servlet-name>
    <servlet-class>com.ithows.base.DispatcherServlet</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>DispatcherServlet</servlet-name>
    <url-pattern>*.do</url-pattern>
</servlet-mapping>

<!-- Swagger UI & OpenAPI JSON -->
<servlet>
    <servlet-name>SwaggerServlet</servlet-name>
    <servlet-class>com.ithows.base.SwaggerServlet</servlet-class>
    <load-on-startup>2</load-on-startup>
</servlet>
<servlet-mapping>
    <servlet-name>SwaggerServlet</servlet-name>
    <url-pattern>/docs/*</url-pattern>
</servlet-mapping>

<!-- 앱 설정 -->
<servlet>
    <servlet-name>AppConfig</servlet-name>
    <servlet-class>com.ithows.AppConfig</servlet-class>
    <load-on-startup>1</load-on-startup>
</servlet>

<!-- 문자 인코딩 필터 -->
<filter>
    <filter-name>Character Encoding</filter-name>
    <filter-class>com.ithows.CharacterEncodingFilter</filter-class>
    <init-param>
        <param-name>encoding</param-name>
        <param-value>UTF-8</param-value>
    </init-param>
</filter>
<filter-mapping>
    <filter-name>Character Encoding</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>

<!-- 보안 필터: CSRF, XSS, Rate Limiting, HTTP 보안 헤더 -->
<filter>
    <filter-name>SecurityFilter</filter-name>
    <filter-class>com.ithows.base.SecurityFilter</filter-class>
    <init-param>
        <param-name>rateLimitMaxRequests</param-name>
        <param-value>100</param-value>
    </init-param>
    <init-param>
        <param-name>rateLimitWindowMs</param-name>
        <param-value>60000</param-value>
    </init-param>
</filter>
<filter-mapping>
    <filter-name>SecurityFilter</filter-name>
    <url-pattern>*.do</url-pattern>
</filter-mapping>

<!-- 세션 타임아웃 -->
<session-config>
    <session-timeout>180</session-timeout>
</session-config>
```

### 8.2 dispatcher-servlet.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans>
    <template id="main"
              top="/template/main/top.jsp"
              bottom="/template/main/bottom.jsp"
              templatePage="/template/main/template.jsp" />
</beans>
```

### 8.3 configplatform.xml

```xml
<entry key="site_domain">Site Title</entry>
<entry key="location_server_api">http://api.example.com/endpoint</entry>
<entry key="temp_dir">temp/</entry>
<entry key="context_path">AppName/</entry>
<entry key="select_area_size">200</entry>
```

---

## 9. URL 매핑 규칙

**형식:** `/{모듈}/{동작}.do`

**예시:**
```
GET/POST  /authLocation.do                 (외부 API - 루트 레벨)
GET/POST  /api/requestNewKey.do            (외부 API - /api/ 모듈)
GET       /api/checkHealth.do              (헬스체크 API)
GET/POST  /app/registDevice.do             (디바이스 API)
POST      /admin/getRequestlogList.do      (관리자 - 로그인 필요)
GET/POST  /service/searchLogData.do        (서비스 - 로그인 필요)
```

**특징:**
- `.do` 확장자 사용
- 모듈 접두사: `/api/`, `/app/`, `/service/`, `/admin/`
- 루트 레벨 외부 API: `/authLocation.do`, `/findFLocation.do` 등
- 동작명: `getData`, `updateItem`, `deleteRecord` 형태

---

## 10. 전체 API 예시

### 10.1 간단한 GET API

```java
@ControllerMethodInfo(id = "/api/getUserInfo.do")
public String getUserInfo(HttpSession session, HttpServletRequest request,
                         HttpServletResponse response, Object command) {

    String userId = HttpUtil.getParameterString(request, "userId", "");

    if (userId.equals("")) {
        request.setAttribute("result", "ERROR");
        request.setAttribute("msg", "userId is required");
        return "RESULT_COMMON_JSON";
    }

    try {
        ResultMap user = UserDAO.getUserById(userId);

        if (user == null) {
            request.setAttribute("result", "NO");
            request.setAttribute("msg", "User not found");
        } else {
            request.setAttribute("result", "OK");
            request.setAttribute("msg", "Success");
            request.setAttribute("resultMap", user);
        }
    } catch (Exception e) {
        e.printStackTrace();
        request.setAttribute("result", "ERROR");
        request.setAttribute("msg", "Server Error");
    }

    return "RESULT_COMMON_JSON";
}
```

### 10.2 POST JSON API (ApiInfo 어노테이션 포함)

```java
@ControllerMethodInfo(id = "/api/createUser.do")
@ApiInfo(
    summary = "사용자 생성",
    description = "새로운 사용자를 생성합니다",
    tag = "User API",
    method = "POST",
    parameters = {
        @ApiInfo.Param(name = "userId", type = "string", description = "사용자 ID", required = true),
        @ApiInfo.Param(name = "userName", type = "string", description = "사용자 이름", required = true),
        @ApiInfo.Param(name = "password", type = "string", description = "비밀번호", required = true)
    }
)
public String createUser(HttpSession session, HttpServletRequest request,
                        HttpServletResponse response, Object command) {

    JSONObject jParam = null;
    try {
        jParam = HttpUtil.getBodyJson(request);
    } catch (Exception e) {
        jParam = null;
    }

    if (jParam == null) {
        request.setAttribute("result", "ERROR");
        request.setAttribute("msg", "No Parameter");
        return "RESULT_COMMON_JSON";
    }

    try {
        String userId = jParam.getString("userId");
        String userName = jParam.getString("userName");
        String password = jParam.getString("password");

        int result = UserDAO.insertUser(userId, userName, password);

        if (result > 0) {
            request.setAttribute("result", "OK");
            request.setAttribute("msg", "User created successfully");
        } else {
            request.setAttribute("result", "ERROR");
            request.setAttribute("msg", "Failed to create user");
        }
    } catch (JSONException e) {
        request.setAttribute("result", "ERROR");
        request.setAttribute("msg", "Invalid JSON format");
    } catch (Exception e) {
        e.printStackTrace();
        request.setAttribute("result", "ERROR");
        request.setAttribute("msg", "Server Error");
    }

    return "RESULT_COMMON_JSON";
}
```

### 10.3 로그인 필수 + 보안 레벨 API

```java
@ControllerMethodInfo(
    id = "/admin/RequestLogView.do",
    loginRequired = true,
    requiredSecurityLevel = 1
)
public String requestLogView(HttpSession session, HttpServletRequest request,
                             HttpServletResponse response, Object command) {
    // loginRequired=true, requiredSecurityLevel=1은
    // DispatcherServlet에서 자동으로 ServiceInterceptor.checkPermission()을 호출하여 검증
    // 여기에 도달했다면 이미 권한 검증 완료

    // 비즈니스 로직...
    return "RESULT_COMMON_JSON";
}
```

---

## 11. 모범 사례

### 11.1 파라미터 검증
```java
// 항상 기본값 제공
String type = HttpUtil.getParameterString(request, "type", "default");

// 필수 파라미터 검증
if (userId == null || userId.equals("")) {
    request.setAttribute("result", "ERROR");
    request.setAttribute("msg", "userId is required");
    return "RESULT_COMMON_JSON";
}

// 숫자 범위 검증
if (pageNo < 1) pageNo = 1;
if (pageSize > 100) pageSize = 100;
```

### 11.2 Null 안전 처리
```java
// DAO 결과 항상 확인
if (resultList != null && !resultList.isEmpty()) {
    // 데이터 처리
} else {
    // 빈 결과 처리
}

// 안전한 JSON 접근
try {
    String value = jParam.getString("key");
} catch (JSONException e) {
    // 누락된 키 처리
}
```

### 11.3 에러 응답 일관성
```java
// 표준 에러 코드
"OK"    - 성공
"ERROR" - 검증 오류 또는 비즈니스 로직 오류
"NO"    - 데이터 없음
"ERR"   - 시스템 오류

// 항상 의미 있는 메시지 제공
request.setAttribute("msg", "User ID already exists");
request.setAttribute("msg", "Invalid email format");
request.setAttribute("msg", "Database connection failed");
```

### 11.4 트랜잭션 처리
```java
try {
    JdbcDao.beginTransaction();

    UserDAO.insertUser(userId, userName, password);
    UserKeyDAO.makeUserKey(userId, userClass);
    HistoryDAO.insertHistory(userId, action, timestamp);

    JdbcDao.commit();
    request.setAttribute("result", "OK");

} catch (Exception e) {
    JdbcDao.rollback();
    request.setAttribute("result", "ERROR");
    request.setAttribute("msg", "Transaction failed");
}
```

### 11.5 로깅
```java
// 디버그 로그
System.out.println("API Called: " + request.getRequestURI());

// 구조화된 로깅
BaseDebug.info("User login: " + userId);
BaseDebug.error("Database error", e);

// 보안 감사 로그
ServiceInterceptor.auditLog(request, "LOGIN", "User " + userId + " logged in");
```

---

## 12. 보안 고려사항

### 12.1 SQL 인젝션 방어
```java
// 올바른 방법: 파라미터화된 쿼리 사용
String sql = "SELECT * FROM user WHERE userId = ?";
ResultMap user = JdbcDao.queryForMap(sql, new Object[]{userId});

// 잘못된 방법: 문자열 연결 (절대 사용 금지)
String sql = "SELECT * FROM user WHERE userId = '" + userId + "'";
```

### 12.2 XSS 방어
```java
// SecurityFilter가 자동으로 XssRequestWrapper를 적용하여 입력값 필터링
// 추가적으로 JSP에서도 이스케이프 처리

// JSTL 사용
<c:out value="${userInput}" />

// EL 함수 사용
${fn:escapeXml(userInput)}
```

### 12.3 세션 관리
```java
// 세션 정보 조회
SessionInfo sInfo = HttpUtil.getSessionInfo(session);

// 인증 확인 — @ControllerMethodInfo의 loginRequired=true로 선언적 처리 권장
// 또는 프로그래밍 방식:
if (sInfo == null || !sInfo.isLogin()) {
    request.setAttribute("result", "ERROR");
    request.setAttribute("msg", "Authentication required");
    return "RESULT_COMMON_JSON";
}
```

### 12.4 입력 검증
```java
// 이메일 형식 검증
if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
    request.setAttribute("result", "ERROR");
    request.setAttribute("msg", "Invalid email format");
    return "RESULT_COMMON_JSON";
}

// 숫자 범위 검증
if (age < 0 || age > 150) {
    request.setAttribute("result", "ERROR");
    request.setAttribute("msg", "Invalid age");
    return "RESULT_COMMON_JSON";
}

// 파일 경로 안전 처리
String fileName = FilenameUtils.getName(uploadedFileName);
```

---

## 13. 성능 최적화

### 13.1 데이터베이스 쿼리 최적화
```java
// SELECT * 대신 필요한 컬럼만 조회
String sql = "SELECT userId, userName, email FROM user WHERE active = ?";

// 페이지네이션에 LIMIT 사용
String sql = "SELECT * FROM logs ORDER BY createTime DESC LIMIT ?, ?";
Object[] params = new Object[]{offset, pageSize};

// 인덱스가 있는 컬럼으로 조회
String sql = "SELECT * FROM user WHERE userId = ?";  // 인덱스 컬럼
```

### 13.2 커넥션 풀 설정
```java
// context.xml
maxActive="100"
maxIdle="30"
minIdle="10"
maxWait="10000"
```

### 13.3 캐싱 전략
```java
// 정적 설정 캐싱
private static Map<String, String> configCache = null;

public static String getConfig(String key) {
    if (configCache == null) {
        configCache = loadConfigFromDB();
    }
    return configCache.get(key);
}
```

---

## 변경 이력

### 2026-03-10
- 문서 전체를 한국어로 전환
- 보안 레이어 섹션 신규 추가 (SecurityFilter, XssRequestWrapper, CSRF, Rate Limiting, RBAC)
- API 문서화 섹션 신규 추가 (@ApiInfo, SwaggerServlet, OpenAPI 3.0)
- ControllerMethodInfo에 추가된 `loginRequired`, `requiredSecurityLevel` 속성 반영
- ServiceInterceptor의 `checkPermission()`, `auditLog()` 메서드 문서화
- 요청 처리 흐름에 SecurityFilter 및 권한 체크 단계 추가
- web.xml 설정에 SecurityFilter, SwaggerServlet 추가 반영

### 2026-02-10
- 초기 문서 작성 (영문)

---

## RAG 검색 키워드

SSF Framework, SSF 프레임워크, Java 웹 프레임워크, MVC 패턴, 어노테이션 기반 라우팅, @ControllerClassInfo, @ControllerMethodInfo, @ApiInfo, HttpServletRequest, HttpServletResponse, HttpSession, JdbcDao, ResultMap, JSP Forward, URL 매핑, 파라미터 처리, HttpUtil, DateTimeUtils, UtilString, 데이터베이스 접근, JDBC Template, 커넥션 풀, MariaDB, JSON 처리, 요청 처리 흐름, 응답 처리, 에러 처리, DAO 패턴, 서비스 레이어, 컨트롤러 레이어, 뷰 레이어, REST API, 웹 애플리케이션, 서블릿, DispatcherServlet, 설정, web.xml, dispatcher-servlet.xml, SecurityFilter, XssRequestWrapper, XSS 방어, CSRF 토큰, Rate Limiting, 보안 헤더, RBAC, 역할 기반 접근 제어, loginRequired, requiredSecurityLevel, ServiceInterceptor, 보안 감사 로그, SwaggerServlet, OpenAPI, Swagger UI, API 문서화, 보안 필터, 클릭재킹, HSTS, Content-Security-Policy, SQL 인젝션 방지, 세션 관리, 입력 검증, 성능 최적화, 코딩 규칙
