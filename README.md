# WebTerm

WebTerm은 브라우저에서 SSH 터미널과 원격 AI CLI 작업 공간을 함께 쓰기 위한 Java 웹 애플리케이션입니다. xterm.js 기반 터미널을 WebSocket으로 서버와 연결하고, 서버는 JSch로 원격 SSH 셸을 열어 입출력과 터미널 크기 변경을 중계합니다.

기존 SSF(Simple Spring-like Framework) 코드 위에 WebTerm 화면과 API를 얹은 구조라서, `*.do` 컨트롤러 라우팅, JSP 뷰, Embedded Tomcat 실행, Swagger/OpenAPI 문서화도 함께 제공됩니다.

---

## 주요 기능

- 브라우저 SSH 터미널: `/terminal.do`
- SSH/Codex 워크스페이스: `/workspace.do`
- 여러 SSH 세션 탭 관리
- 저장한 SSH 서버 목록 관리
- 자주 쓰는 명령 저장 및 활성 SSH 탭으로 전송
- 원격 SSH 세션 안에서 `codex` 또는 `claude` CLI 실행
- 터미널 폰트와 크기 설정
- 저장 서버 목록 JSON Export
- Embedded Tomcat 단독 실행 및 기존 WAR 배포 지원
- `@ControllerMethodInfo`, `@ApiInfo` 기반 자동 API 문서화

> 주의: 현재 SSH 서버 저장 기능은 `data.db` SQLite 파일에 비밀번호를 평문 저장할 수 있습니다. 개인 개발 환경 또는 신뢰할 수 있는 내부망 용도로 사용하고, 운영 환경에서는 암호화/권한/접근 제어를 보강하세요.

---

## 빠른 시작

### 요구 사항

- JDK 17 이상
- Maven 3.x
- 접속 대상 서버의 SSH 계정
- 원격에서 AI CLI를 실행하려면 해당 서버에 `codex` 또는 `claude` 명령 설치

### Embedded Tomcat으로 실행

```bash
./embedded-build.sh
./embedded-run.sh
```

기본 실행 URL:

```text
http://localhost:8088/WebTerm
```

포트나 컨텍스트 경로를 바꿔 실행할 수 있습니다.

```bash
./embedded-run.sh --port 9090 --context /WebTerm
```

개발 모드로 실행하려면 소스의 `web/` 디렉터리를 직접 사용합니다.

```bash
./embedded-build.sh dev
```

개발 모드 기본값은 `SERVER_PORT=8080`, `CONTEXT_PATH=/WebTerm`입니다.

```bash
SERVER_PORT=8088 CONTEXT_PATH=/WebTerm ./embedded-build.sh dev
```

### WAR 빌드

```bash
mvn clean package
```

Embedded Tomcat용 WAR:

```bash
mvn -f pom-embedded.xml clean package
```

---

## 주요 URL

| URL | 설명 |
|-----|------|
| `/workspace.do` | SSH 탭, 빠른 명령, 원격 Codex/Claude CLI를 함께 쓰는 메인 워크스페이스 |
| `/terminal.do` | 단일 SSH 터미널 화면 |
| `/ssh-terminal` | SSH 터미널 WebSocket 엔드포인트 |
| `/api/workspace/listServers.do` | 저장된 SSH 서버 목록 |
| `/api/workspace/saveServer.do` | SSH 서버 저장 |
| `/api/workspace/deleteServer.do` | SSH 서버 삭제 |
| `/api/workspace/listCommands.do` | 빠른 명령 목록 |
| `/api/workspace/saveCommand.do` | 빠른 명령 저장 |
| `/api/workspace/deleteCommand.do` | 빠른 명령 삭제 |
| `/api/workspace/getSettings.do` | 워크스페이스 설정 조회 |
| `/api/workspace/saveSettings.do` | 워크스페이스 설정 저장 |
| `/api/workspace/exportServers.do` | 저장 서버 목록 JSON Export |
| `/docs/` | Swagger UI |
| `/docs/api-docs` | OpenAPI 3.0 JSON |

---

## 사용 흐름

1. `/workspace.do`에 접속합니다.
2. `SSH 연결` 버튼으로 호스트, 포트, ID, PW를 입력합니다.
3. 필요하면 접속 정보를 저장합니다.
4. SSH 탭에서 셸을 사용하거나, `자주 쓰는 명령`에 명령을 등록해 빠르게 실행합니다.
5. 오른쪽 AI 도구 영역에서 `Codex` 또는 `Claude Code`를 선택하고 시작합니다.
6. `작업 디렉토리`를 입력하면 원격 SSH 세션에서 해당 디렉터리로 이동한 뒤 CLI를 실행합니다.

단일 터미널만 필요하면 `/terminal.do`를 사용하면 됩니다.

---

## 데이터 저장

워크스페이스 설정은 실행 작업 디렉터리의 SQLite 파일에 저장됩니다.

```text
data.db
```

생성되는 테이블:

- `ssh_servers`: SSH 호스트, 포트, 사용자명, 비밀번호
- `quick_commands`: 자주 쓰는 명령
- `workspace_settings`: 터미널 폰트, 폰트 크기

`data.db`는 로컬 런타임 데이터이므로 Git에 커밋하지 않는 것을 권장합니다.

---

## 기술 스택

| 구분 | 내용 |
|------|------|
| 언어 | Java 17 |
| 웹 런타임 | Servlet/JSP, Embedded Tomcat 9 |
| 터미널 UI | xterm.js, xterm-addon-fit |
| WebSocket | Java WebSocket API |
| SSH | JSch |
| 로컬 저장소 | SQLite JDBC |
| JSON | org.json, Jackson |
| 빌드 | Maven, Ant |
| 뷰 | JSP |

---

## 프로젝트 구조

```text
WebTerm/
├── src/com/ithows/
│   ├── base/                    # DispatcherServlet, SecurityFilter, SwaggerServlet 등 프레임워크 코어
│   ├── controller/
│   │   ├── TerminalController.java
│   │   └── WorkspaceApiController.java
│   ├── service/
│   │   ├── SshTerminalWebSocket.java
│   │   └── WorkspaceStore.java
│   ├── util/
│   └── EmbeddedApplication.java
├── src/com/sox/ltex/            # 기존 측위/지리공간 도메인 모듈
├── web/
│   ├── WEB-INF/jsp/
│   │   ├── terminal.jsp
│   │   └── workspace.jsp
│   ├── js/terminal/terminal.js
│   ├── js/workspace/workspace.js
│   ├── css/terminal/terminal.css
│   ├── css/workspace/workspace.css
│   └── vendor/xterm/
├── lib/                         # 로컬/레거시 JAR
├── pom.xml                      # 일반 WAR 빌드
├── pom-embedded.xml             # Embedded Tomcat 빌드
├── embedded-build.sh / .bat
└── embedded-run.sh / .bat
```

---

## WebSocket 프로토콜

클라이언트는 `/ssh-terminal`로 JSON 메시지를 보냅니다.

### 연결

```json
{
  "type": "connect",
  "host": "example.com",
  "port": 22,
  "username": "user",
  "password": "password",
  "initialCommand": "cd /home/user/project && codex",
  "cols": 120,
  "rows": 32
}
```

### 입력

```json
{
  "type": "input",
  "data": "ls -al\n"
}
```

### 크기 변경

```json
{
  "type": "resize",
  "cols": 100,
  "rows": 30
}
```

### 종료

```json
{
  "type": "disconnect"
}
```

서버 응답 타입:

- `status`: `READY`, `CONNECTING`, `CONNECTED`, `DISCONNECTED`
- `output`: SSH 셸 출력
- `error`: 오류 메시지

---

## 설정 파일

| 파일 | 설명 |
|------|------|
| `web/WEB-INF/web.xml` | DispatcherServlet, SwaggerServlet, 필터, 세션 설정 |
| `web/WEB-INF/dispatcher-servlet.xml` | JSP 템플릿 페이지 정의 |
| `web/WEB-INF/classes/configplatform.xml` | 애플리케이션 설정 |
| `web/WEB-INF/classes/connpool.xml` | DB 커넥션 풀 설정 |
| `web/WEB-INF/classes/log4j.properties` | 로깅 설정 |

---

## 보안 메모

- SSH 연결은 비밀번호 기반 인증을 사용합니다.
- `StrictHostKeyChecking=no`로 설정되어 있어 호스트 키 검증을 하지 않습니다.
- 저장된 SSH 비밀번호는 `data.db`에 평문 저장될 수 있습니다.
- `SecurityFilter`는 `*.do` 요청에 대해 보안 헤더, XSS 필터링, 선택적 CSRF, Rate Limiting을 적용합니다.
- `@ApiKeyRequired`가 붙은 API는 `X-API-Key` 헤더를 검증합니다.
- 외부에 노출되는 환경에서는 HTTPS, 접근 제어, DB 파일 권한, 비밀번호 암호화, 호스트 키 검증을 반드시 검토하세요.

---

## 개발 참고

컨트롤러는 `@ControllerClassInfo`, `@ControllerMethodInfo`로 URL을 선언합니다.

```java
@ControllerClassInfo(controllerPage = "/_main.jsp")
public class TerminalController {
    @ControllerMethodInfo(id = "/workspace.do")
    public String workspace(HttpSession session, HttpServletRequest request,
                            HttpServletResponse response, Object command) {
        return "/workspace.jsp";
    }
}
```

JSP는 `/WEB-INF/jsp/` 하위에 두고, 정적 자산은 `web/js`, `web/css`, `web/vendor`에 둡니다.

API 문서는 `@ApiInfo`를 붙이면 `/docs/`와 `/docs/api-docs`에 자동 반영됩니다.

---

## 관련 파일

- `embedded-build.sh`, `embedded-build.bat`: Embedded Tomcat 빌드/개발 실행
- `embedded-run.sh`, `embedded-run.bat`: 빌드된 Embedded WAR 실행
- `pom.xml`: 일반 WAR 빌드
- `pom-embedded.xml`: Embedded Tomcat 빌드
