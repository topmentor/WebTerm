<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WebTerm</title>
    <link rel="icon" href="${servletPath}/favicon.svg?v=20260609" type="image/svg+xml">
    <link rel="alternate icon" href="${servletPath}/favicon.ico?v=20260609" type="image/x-icon">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Azeret+Mono:wght@400;500&family=Fira+Code:wght@400;500&family=IBM+Plex+Mono:wght@400;500&family=Inconsolata:wght@400;500&family=JetBrains+Mono:wght@400;500&family=Noto+Sans+Mono:wght@400;500&family=Roboto+Mono:wght@400;500&family=Source+Code+Pro:wght@400;500&family=Space+Mono:wght@400;700&family=Ubuntu+Mono:wght@400;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${servletPath}/vendor/xterm/xterm.css">
    <link rel="stylesheet" href="${servletPath}/css/workspace/workspace.css">
    <script>var servletPath = '${servletPath}';</script>
</head>
<body class="workspace-auth-pending">
<section id="workspaceLoginGate" class="workspace-login-gate">
    <div id="workspaceLoginDialog" class="workspace-login-dialog">
        <div class="workspace-login-brand">
            <img src="${servletPath}/images/Logo_LTEX.png" alt="Company Logo">
            <strong>WebTerm</strong>
        </div>
        <label class="workspace-login-field">
            <span>ID</span>
            <input id="workspaceLoginUserId" type="text" value="soxuser" autocomplete="username">
        </label>
        <label class="workspace-login-field">
            <span>비밀번호</span>
            <input id="workspaceLoginPassword" type="password" autocomplete="current-password">
        </label>
        <div class="workspace-login-actions">
            <span id="workspaceLoginStatus" class="status-line"></span>
            <button id="workspaceLoginConfirm" type="button">확인</button>
        </div>
    </div>
    <div id="workspaceLoginFailed" class="workspace-login-failed" style="display:none;">
        <img src="${servletPath}/images/Logo_LTEX.png" alt="Company Logo">
        <h1>WebTerm</h1>
    </div>
</section>

<main class="workspace-app" aria-hidden="true">
    <header class="workspace-header">
        <div>
            <h1>Web Term</h1>
            <span id="workspaceStatus" class="status-line">저장한 SSH 서버를 열고 원격 서버의 Codex/Claude CLI를 실행합니다.</span>
        </div>
        <div class="header-actions">
            <button id="openSettings" type="button">설정</button>
            <button id="logoutWorkspace" type="button">로그아웃</button>
            <button id="disconnectSsh" type="button" disabled>현재 탭 종료</button>
        </div>
    </header>

    <section id="sshDialog" class="ssh-dialog" style="display:none;">
        <div class="ssh-dialog-box">
            <h2>SSH 서버 연결</h2>
            <div class="dialog-grid">
                <label>
                    <span>주소</span>
                    <input id="sshHost" type="text" placeholder="example.com">
                </label>
                <label class="port-field">
                    <span>포트</span>
                    <input id="sshPort" type="number" value="22" min="1" max="65535">
                </label>
                <label class="half-field">
                    <span>ID</span>
                    <input id="sshUser" type="text" autocomplete="username">
                </label>
                <label class="half-field">
                    <span>PW</span>
                    <input id="sshPassword" type="password" autocomplete="current-password">
                </label>
                <label class="save-field">
                    <span>저장</span>
                    <span class="check-row"><input id="saveSshInfo" type="checkbox" checked> 접속 정보 저장</span>
                </label>
            </div>
            <div class="dialog-actions">
                <span id="sshConnectStatus" class="status-line"></span>
                <button id="cancelSshDialog" type="button">취소</button>
                <button id="connectSsh" type="button">연결</button>
            </div>
        </div>
    </section>

    <section id="settingsDialog" class="ssh-dialog" style="display:none;">
        <div class="ssh-dialog-box">
            <h2>설정</h2>
            <div class="settings-grid">
                <label for="terminalFontFamily">터미널 폰트</label>
                <select id="terminalFontFamily">
                    <option value='"JetBrains Mono", "Cascadia Mono", Consolas, "Courier New", monospace'>JetBrains Mono</option>
                    <option value='"Fira Code", "Cascadia Mono", Consolas, "Courier New", monospace'>Fira Code</option>
                    <option value='"Source Code Pro", "Cascadia Mono", Consolas, "Courier New", monospace'>Source Code Pro</option>
                    <option value='"Roboto Mono", "Cascadia Mono", Consolas, "Courier New", monospace'>Roboto Mono</option>
                    <option value='"IBM Plex Mono", "Cascadia Mono", Consolas, "Courier New", monospace'>IBM Plex Mono</option>
                    <option value='"Noto Sans Mono", "Cascadia Mono", Consolas, "Courier New", monospace'>Noto Sans Mono</option>
                    <option value='"Inconsolata", "Cascadia Mono", Consolas, "Courier New", monospace'>Inconsolata</option>
                    <option value='"Ubuntu Mono", "Cascadia Mono", Consolas, "Courier New", monospace'>Ubuntu Mono</option>
                    <option value='"Space Mono", "Cascadia Mono", Consolas, "Courier New", monospace'>Space Mono</option>
                    <option value='"Azeret Mono", "Cascadia Mono", Consolas, "Courier New", monospace'>Azeret Mono</option>
                </select>
                <label for="terminalFontSize">터미널 크기</label>
                <input id="terminalFontSize" type="number" min="10" max="24" step="1" value="14">
            </div>
            <div class="dialog-actions">
                <span id="settingsStatus" class="status-line"></span>
                <button id="cancelSettings" type="button">취소</button>
                <button id="saveSettings" type="button">저장</button>
            </div>
        </div>
    </section>

    <section class="workspace-tools-section">
        <div class="tool-panel saved-server-panel">
            <div class="tool-panel-header">
                <strong>서버 연결</strong>
                <button id="openSshDialog" type="button">SSH 연결</button>
            </div>
            <div class="saved-server-row">
                <select id="savedServers"></select>
                <button id="openSavedServer" type="button">열기</button>
                <button id="deleteSavedServer" type="button">삭제</button>
                <button id="exportSshServers" type="button">Export</button>
            </div>
        </div>
        <div class="tool-panel quick-cmd-panel">
            <div class="quick-cmds-input-row">
                <strong>자주 쓰는 명령</strong>
                <input id="quickCommandInput" type="text" placeholder="명령 입력 후 추가 또는 Enter">
                <button id="addQuickCommand" type="button">추가</button>
                <span id="quickCommandStatus" class="status-line"></span>
            </div>
            <div id="quickCommandList" class="quick-cmds-list"></div>
        </div>
    </section>

    <section class="workspace-grid" id="workspaceGrid">
        <section class="workspace-pane" id="sshPane">
            <div class="pane-header">
                <span>SSH 셸</span>
                <span id="sshStatus" class="status-line">미연결</span>
            </div>
            <div id="sshTabs" class="ssh-tabs"></div>
            <div class="pane-body">
                <div id="sshPlaceholder" class="terminal-placeholder">SSH 연결 버튼으로 서버에 접속하세요.</div>
                <div id="sshTermContainer"></div>
            </div>
        </section>

        <div class="workspace-splitter" id="workspaceSplitter"></div>

        <section class="workspace-pane" id="agentPane">
            <div class="pane-header">
                <span id="agentTitle">Codex</span>
                <span class="pane-actions">
                    <button id="resetAgent" type="button" disabled>초기화</button>
                </span>
            </div>
            <div class="pane-body">
                <div id="agentForm" class="agent-form">
                    <label>AI 도구</label>
                    <div class="segmented">
                        <label><input type="radio" name="agentKind" value="codex" checked> Codex</label>
                        <label><input type="radio" name="agentKind" value="claude"> Claude Code</label>
                    </div>
                    <div class="form-actions">
                        <button id="loginAgentAuth" type="button">로그인</button>
                        <button id="startAgent" type="button">시작</button>
                        <span id="agentStatus" class="status-line"></span>
                    </div>
                </div>
                <div id="agentTerm" class="terminal-box" style="display:none;"></div>
            </div>
        </section>
    </section>
</main>

<script src="${servletPath}/vendor/xterm/xterm.js"></script>
<script src="${servletPath}/vendor/xterm/xterm-addon-fit.js"></script>
<script src="${servletPath}/js/workspace/workspace.js"></script>
</body>
</html>
