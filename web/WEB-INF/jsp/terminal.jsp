<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Web SSH Terminal</title>
    <link rel="shortcut icon" href="${servletPath}/favicon.ico" type="image/x-icon">
    <link rel="stylesheet" href="${servletPath}/vendor/xterm/xterm.css">
    <link rel="stylesheet" href="${servletPath}/css/terminal/terminal.css">
    <script>
        var servletPath = '${servletPath}';
    </script>
</head>
<body>
<main class="terminal-app">
    <section class="connection-bar" aria-label="SSH connection">
        <div class="brand">
            <span class="brand-mark">SSH</span>
            <span class="brand-title">Web Terminal</span>
        </div>
        <form id="connectionForm" class="connection-form" autocomplete="off">
            <div class="field-row address-row">
                <label>
                    <span>주소</span>
                    <input id="hostInput" name="host" type="text" placeholder="example.com" required>
                </label>
                <label class="port-field">
                    <span>포트</span>
                    <input id="portInput" name="port" type="number" min="1" max="65535" value="22" required>
                </label>
            </div>
            <div class="field-row account-row">
                <label>
                    <span>ID</span>
                    <input id="usernameInput" name="username" type="text" autocomplete="username" required>
                </label>
                <label>
                    <span>PW</span>
                    <input id="passwordInput" name="password" type="password" autocomplete="current-password" required>
                </label>
            </div>
            <div class="actions">
                <button id="connectButton" type="submit">접속</button>
                <button id="disconnectButton" type="button" disabled>종료</button>
            </div>
        </form>
    </section>

    <section class="terminal-shell" aria-label="Terminal">
        <div class="terminal-toolbar">
            <span id="statusDot" class="status-dot"></span>
            <span id="statusText">대기 중</span>
        </div>
        <div id="terminal"></div>
    </section>
</main>

<script src="${servletPath}/vendor/xterm/xterm.js"></script>
<script src="${servletPath}/vendor/xterm/xterm-addon-fit.js"></script>
<script src="${servletPath}/js/terminal/terminal.js"></script>
</body>
</html>
