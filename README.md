# WebTerm

WebTerm은 브라우저에서 SSH 터미널과 원격 AI CLI 작업 공간을 함께 쓰기 위한 Java 웹 애플리케이션입니다. xterm.js 기반 터미널을 WebSocket으로 서버와 연결하고, 서버는 JSch로 원격 SSH 셸을 열어 입출력과 터미널 크기 변경을 중계합니다.

기존 SSF(Simple Spring-like Framework) 코드 위에 WebTerm 화면과 API를 얹은 구조라서, `*.do` 컨트롤러 라우팅, JSP 뷰, Embedded Tomcat 실행, Swagger/OpenAPI 문서화도 함께 제공됩니다.

---

## 주요 기능

- 브라우저 SSH 터미널: `/terminal.do`
- SSH/Codex 워크스페이스: `/workspace.do`
- 여러 SSH 세션 탭 관리
- SSH 접속 정보 재사용 기반 SFTP 파일 탐색기 탭
- 원격 파일/디렉터리 목록 조회, 이동, 생성, 이름 변경, 삭제
- 원격 파일 다운로드 및 로컬 파일 업로드
- 내부 SQLite DB 기반 WebTerm 로그인
- 로그아웃 및 재로그인 흐름
- 저장한 SSH 서버 목록 관리
- 자주 쓰는 명령 저장 및 활성 SSH 탭으로 전송
- 원격 SSH 세션에서 `codex` 또는 `claude` CLI 로그인/실행
- 터미널 폰트와 크기 설정
- 모바일 UI: AI 패널을 숨기고 SSH 접속과 자주 쓰는 명령 중심으로 표시
- 터미널 텍스트 복사/붙여넣기 단축키 지원
- 저장 서버 목록 JSON Export
- Embedded Tomcat 단독 실행 및 기존 WAR 배포 지원
- `@ControllerMethodInfo`, `@ApiInfo` 기반 자동 API 문서화

> 주의: 현재 SSH 서버 저장 기능은 `data.db` SQLite 파일에 SSH 접속 정보를 저장합니다. 개인 개발 환경 또는 신뢰할 수 있는 내부망 용도로 사용하고, 운영 환경에서는 암호화/권한/접근 제어를 보강하세요.

---

## 빠른 시작

### 요구 사항

- JDK 17 이상
- Maven 3.x
- 접속 대상 서버의 SSH 계정
- AI CLI를 실행하려면 접속 대상 SSH 서버에 `codex` 또는 `claude` 명령 설치

### 기본 로그인 계정

`/workspace.do` 최초 접속 시 WebTerm 로그인 다이어로그가 표시됩니다.

```text
ID: soxuser
PW: sox2018
```

이 계정은 내부 SQLite DB의 `webterm_users` 테이블에 자동 저장/갱신됩니다. 외부에 노출되는 환경에서는 기본 계정과 비밀번호 정책을 반드시 변경하세요.

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
| `/login.do` | WebTerm 로그인 인증 |
| `/logout.do` | 세션 로그아웃 |
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
| `/api/workspace/sftpList.do` | SFTP 디렉터리 목록 조회 |
| `/api/workspace/sftpMkdir.do` | SFTP 원격 디렉터리 생성 |
| `/api/workspace/sftpRename.do` | SFTP 원격 파일/디렉터리 이름 변경 |
| `/api/workspace/sftpDelete.do` | SFTP 원격 파일/디렉터리 삭제 |
| `/api/workspace/sftpDownload.do` | SFTP 원격 파일 다운로드 |
| `/api/workspace/sftpUpload.do` | SFTP 원격 파일 업로드 |
| `/docs/` | Swagger UI |
| `/docs/api-docs` | OpenAPI 3.0 JSON |

---

## 사용 흐름

1. `/workspace.do`에 접속합니다.
2. 로그인 다이어로그에서 `soxuser / sox2018`로 로그인합니다.
3. `SSH 연결` 버튼으로 호스트, 포트, ID, PW를 입력합니다.
4. 필요하면 접속 정보를 저장합니다.
5. SSH 탭에서 셸을 사용하거나, `자주 쓰는 명령`에 명령을 등록해 빠르게 실행합니다.
6. `SSH 셸` 제목 옆의 `파일보기` 버튼을 누르면 현재 SSH 접속 정보로 SFTP에 연결하고 새 파일 탭을 엽니다.
7. 파일 탭에서 경로 이동, 새 폴더 생성, 이름 변경, 삭제, 다운로드, 업로드를 수행합니다.
8. 데스크톱에서는 오른쪽 AI 도구 영역에서 `Codex` 또는 `Claude Code`를 선택하고 원격 SSH 서버에서 로그인/실행할 수 있습니다.
9. 로그아웃 버튼을 누르면 서버 세션이 종료되고 다시 로그인 다이어로그로 돌아갑니다.

단일 터미널만 필요하면 `/terminal.do`를 사용하면 됩니다.

### 모바일 UI

화면 폭이 좁은 모바일 환경에서는 AI 패널과 분할바를 숨기고 다음 UI만 표시합니다.

- 서버 연결
- 자주 쓰는 명령
- SSH 터미널

### 터미널 단축키

| 단축키 | 동작 |
|--------|------|
| `Ctrl + Shift + C` | 터미널 선택 텍스트 복사 |
| `Ctrl + Shift + V` | 클립보드 텍스트를 터미널에 붙여넣기 |
| `Shift + 클릭` | 선택 텍스트가 있으면 복사, 없으면 붙여넣기 |

`Ctrl + Shift + C`는 Chrome 계열 브라우저에서 개발자도구 요소 선택 단축키와 충돌할 수 있으므로, 터미널 포커스/선택 상태에서는 WebTerm이 브라우저 단축키를 차단하고 복사를 수행합니다.

---

## 데이터 저장

워크스페이스 설정과 WebTerm 로그인 정보는 SQLite 파일에 저장됩니다.

```text
data.db
```

기본 위치:

- `-Dwebterm.dataDir=/path/to/dir`가 있으면 해당 디렉터리
- Embedded/Tomcat 실행 중 `catalina.base`가 있으면 `${catalina.base}/webterm-data`
- 그 외에는 JVM 실행 작업 디렉터리

생성되는 테이블:

- `webterm_users`: WebTerm 로그인 계정
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
| SSH/SFTP | JSch |
| 로컬 저장소 | SQLite JDBC |
| JSON | org.json, Jackson |
| 클래스 스캔 | Reflections, Javassist |
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
│   │   ├── WebTermAuthStore.java
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

- 기본 WebTerm 로그인 계정은 `soxuser / sox2018`입니다.
- 기본 계정은 앱 기동 시 내부 DB에 자동 저장/갱신됩니다.
- SSH 연결은 비밀번호 기반 인증을 사용합니다.
- `StrictHostKeyChecking=no`로 설정되어 있어 호스트 키 검증을 하지 않습니다.
- 저장된 SSH 접속 정보는 `data.db`에 저장됩니다.
- `SecurityFilter`는 `*.do` 요청에 대해 보안 헤더, XSS 필터링, 선택적 CSRF, Rate Limiting을 적용합니다.
- `@ApiKeyRequired`가 붙은 API는 `X-API-Key` 헤더를 검증합니다.
- SFTP 파일보기 기능은 활성 SSH 탭의 접속 정보를 브라우저 메모리에서 재사용해 요청마다 새 SFTP 연결을 열고 닫습니다.
- SFTP 다운로드/업로드 API는 SSH 비밀번호 또는 private key를 요청 파라미터로 받아 처리하므로 HTTPS 적용을 권장합니다.
- 외부에 노출되는 환경에서는 HTTPS, 접근 제어, DB 파일 권한, 비밀번호 암호화, 기본 계정 변경, 호스트 키 검증을 반드시 검토하세요.

---

## 문제 해결

### Maven clean이 `target/embedded-webapp` 파일 삭제에 실패할 때

Windows에서는 실행 중인 Embedded Tomcat이 `target/embedded-webapp/WEB-INF/lib/*.jar`를 클래스패스로 잡고 있으면 `mvn clean`이 실패할 수 있습니다.

```text
Failed to delete ... target\embedded-webapp\WEB-INF\lib\zip4j-2.11.5.jar
```

해결:

```powershell
Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue
Get-CimInstance Win32_Process |
  Where-Object { $_.CommandLine -like '*com.ithows.EmbeddedApplication*' } |
  Select-Object ProcessId,Name,CommandLine
Stop-Process -Id <ProcessId> -Force
```

서버를 종료한 뒤 `mvn clean package` 또는 `mvn -f pom-embedded.xml clean package`를 다시 실행합니다.

### `invalid constant type: 18`로 앱 기동이 실패할 때

`org.reflections.ReflectionsException`과 함께 아래 오류가 발생하면 오래된 Javassist가 Java 8+ 바이트코드를 읽지 못하는 상태입니다.

```text
Caused by: java.io.IOException: invalid constant type: 18
```

현재 POM은 `reflections`의 구 `javassist:javassist` 전이 의존성을 제외하고 `org.javassist:javassist:3.29.2-GA`를 명시합니다. 빌드 산출물에는 `WEB-INF/lib/javassist-3.29.2-GA.jar`만 포함되어야 합니다.

확인:

```bash
mvn -f pom-embedded.xml clean package
jar tf target/WebTerm-embedded.war | grep javassist
```

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
