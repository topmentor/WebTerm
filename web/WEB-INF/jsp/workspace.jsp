<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Workspace</title>
    <link rel="shortcut icon" href="${servletPath}/favicon.ico" type="image/x-icon">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Azeret+Mono:wght@400;500&family=Fira+Code:wght@400;500&family=IBM+Plex+Mono:wght@400;500&family=Inconsolata:wght@400;500&family=JetBrains+Mono:wght@400;500&family=Noto+Sans+Mono:wght@400;500&family=Roboto+Mono:wght@400;500&family=Source+Code+Pro:wght@400;500&family=Space+Mono:wght@400;700&family=Ubuntu+Mono:wght@400;700&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${servletPath}/vendor/xterm/xterm.css">
    <link rel="stylesheet" href="${servletPath}/css/workspace/workspace.css">
    <script>var servletPath = '${servletPath}';</script>
</head>
<body>
<main class="workspace-app">
    <header class="workspace-header">
        <div>
            <h1>워크스페이스</h1>
            <span id="workspaceStatus" class="status-line">저장한 SSH 서버를 여러 탭으로 열고 원격 Codex/Claude CLI를 실행합니다.</span>
        </div>
        <div class="header-actions">
            <select id="savedServers"></select>
            <button id="openSavedServer" type="button">저장 서버 열기</button>
            <button id="deleteSavedServer" type="button">삭제</button>
            <button id="exportSshServers" type="button">JSON Export</button>
            <button id="openSettings" type="button">설정</button>
            <button id="openSshDialog" type="button">SSH 연결</button>
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
                <label>
                    <span>ID</span>
                    <input id="sshUser" type="text" autocomplete="username">
                </label>
                <label>
                    <span>PW</span>
                    <input id="sshPassword" type="password" autocomplete="current-password">
                </label>
                <label class="save-field">
                    <span>저장</span>
                    <span class="check-row"><input id="saveSshInfo" type="checkbox" checked> 접속 정보 저장</span>
                </label>
                <label class="save-field">
                    <span>PW 저장</span>
                    <span class="check-row"><input id="saveSshPassword" type="checkbox"> 브라우저에 PW 저장</span>
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
                    <option value='"JetBrains Mono"'>JetBrains Mono</option>
                    <option value='"Fira Code"'>Fira Code</option>
                    <option value='"Source Code Pro"'>Source Code Pro</option>
                    <option value='"Roboto Mono"'>Roboto Mono</option>
                    <option value='"IBM Plex Mono"'>IBM Plex Mono</option>
                    <option value='"Noto Sans Mono"'>Noto Sans Mono</option>
                    <option value='"Inconsolata"'>Inconsolata</option>
                    <option value='"Ubuntu Mono"'>Ubuntu Mono</option>
                    <option value='"Space Mono"'>Space Mono</option>
                    <option value='"Azeret Mono"'>Azeret Mono</option>
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

    <section class="quick-cmds-section">
        <div class="quick-cmds-input-row">
            <strong>자주 쓰는 명령</strong>
            <input id="quickCommandInput" type="text" placeholder="명령 입력 후 추가 또는 Enter">
            <button id="addQuickCommand" type="button">추가</button>
            <span id="quickCommandStatus" class="status-line"></span>
        </div>
        <div id="quickCommandList" class="quick-cmds-list"></div>
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
                <button id="stopAgent" type="button" disabled>중지</button>
            </div>
            <div class="pane-body">
                <div id="agentForm" class="agent-form">
                    <label>AI 도구</label>
                    <div class="segmented">
                        <label><input type="radio" name="agentKind" value="codex" checked> Codex</label>
                        <label><input type="radio" name="agentKind" value="claude"> Claude Code</label>
                    </div>
                    <label for="agentCwd">작업 디렉토리</label>
                    <input id="agentCwd" type="text" value="" placeholder="비우면 원격 홈 디렉토리">
                    <label for="agentCommand">명령</label>
                    <input id="agentCommand" type="text" placeholder="기본값: codex 또는 claude">
                    <div class="form-actions">
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
