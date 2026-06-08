(function () {
    var terminalElement = document.getElementById('terminal');
    var form = document.getElementById('connectionForm');
    var hostInput = document.getElementById('hostInput');
    var portInput = document.getElementById('portInput');
    var usernameInput = document.getElementById('usernameInput');
    var passwordInput = document.getElementById('passwordInput');
    var connectButton = document.getElementById('connectButton');
    var disconnectButton = document.getElementById('disconnectButton');
    var statusText = document.getElementById('statusText');
    var statusDot = document.getElementById('statusDot');

    var term = new Terminal({
        cursorBlink: true,
        convertEol: false,
        fontFamily: 'Menlo, Consolas, "Courier New", monospace',
        fontSize: 14,
        lineHeight: 1.15,
        scrollback: 5000,
        theme: {
            background: '#050708',
            foreground: '#e8edf1',
            cursor: '#8ee0ff',
            selectionBackground: '#29485a'
        }
    });
    var fitAddon = new FitAddon.FitAddon();
    var socket = null;
    var connected = false;

    term.loadAddon(fitAddon);
    term.open(terminalElement);
    attachTerminalHelpers(term);
    fit();
    term.writeln('Web SSH Terminal');
    term.writeln('접속 정보를 입력한 뒤 접속 버튼을 누르세요.');
    term.writeln('');

    function fit() {
        fitAddon.fit();
        if (socket && socket.readyState === WebSocket.OPEN) {
            send({
                type: 'resize',
                cols: term.cols,
                rows: term.rows
            });
        }
    }

    function wsUrl() {
        var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return protocol + '//' + window.location.host + servletPath + '/ssh-terminal';
    }

    function send(payload) {
        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify(payload));
        }
    }

    function attachTerminalHelpers(targetTerm) {
        var isMac = navigator.platform && navigator.platform.toLowerCase().indexOf('mac') >= 0;

        targetTerm.attachCustomKeyEventHandler(function (event) {
            if (event.type !== 'keydown') return true;
            var key = event.key ? event.key.toLowerCase() : '';
            var copyKey = (event.ctrlKey && event.shiftKey && key === 'c')
                || (isMac && event.metaKey && !event.ctrlKey && key === 'c');
            var pasteKey = (event.ctrlKey && event.shiftKey && key === 'v')
                || (isMac && event.metaKey && !event.ctrlKey && key === 'v');

            if (copyKey) {
                var selection = targetTerm.getSelection();
                if (selection && navigator.clipboard) {
                    navigator.clipboard.writeText(selection).catch(function () {});
                    return false;
                }
            }

            if (pasteKey) {
                if (navigator.clipboard) {
                    navigator.clipboard.readText().then(function (text) {
                        if (text) targetTerm.paste(text);
                    }).catch(function () {});
                }
                return false;
            }

            return true;
        });

        if (!targetTerm.element) return;

        targetTerm.element.addEventListener('mousedown', function (event) {
            if (!event.shiftKey || event.button !== 0) return;
            if (targetTerm.getSelection()) {
                event.preventDefault();
                event.stopPropagation();
            }
        }, true);

        targetTerm.element.addEventListener('click', function (event) {
            if (!event.shiftKey || event.button !== 0) return;
            var selection = targetTerm.getSelection();
            if (selection) {
                if (navigator.clipboard) {
                    navigator.clipboard.writeText(selection).catch(function () {});
                }
            } else if (navigator.clipboard) {
                navigator.clipboard.readText().then(function (text) {
                    if (text) targetTerm.paste(text);
                }).catch(function () {});
            }
            event.preventDefault();
            event.stopPropagation();
        }, true);
    }

    function setStatus(state, message) {
        statusText.textContent = message || state;
        statusDot.classList.remove('connected');
        statusDot.classList.remove('error');

        if (state === 'CONNECTED') {
            statusDot.classList.add('connected');
        } else if (state === 'ERROR') {
            statusDot.classList.add('error');
        }
    }

    function setConnected(value) {
        connected = value;
        connectButton.disabled = value;
        disconnectButton.disabled = !value;
        hostInput.disabled = value;
        portInput.disabled = value;
        usernameInput.disabled = value;
        passwordInput.disabled = value;
    }

    function connect() {
        if (socket) {
            socket.close();
        }

        term.clear();
        setStatus('CONNECTING', 'WebSocket 연결 중...');
        socket = new WebSocket(wsUrl());

        socket.onopen = function () {
            fit();
            send({
                type: 'connect',
                host: hostInput.value.trim(),
                port: parseInt(portInput.value, 10) || 22,
                username: usernameInput.value.trim(),
                password: passwordInput.value,
                cols: term.cols,
                rows: term.rows
            });
            passwordInput.value = '';
        };

        socket.onmessage = function (event) {
            var payload = JSON.parse(event.data);
            if (payload.type === 'output') {
                term.write(payload.data);
            } else if (payload.type === 'status') {
                setStatus(payload.state, payload.message);
                setConnected(payload.state === 'CONNECTED');
                if (payload.state === 'DISCONNECTED') {
                    connected = false;
                    setConnected(false);
                }
            } else if (payload.type === 'error') {
                setStatus('ERROR', payload.message);
                term.writeln('\r\n[ERROR] ' + payload.message);
                setConnected(false);
            }
        };

        socket.onclose = function () {
            if (connected) {
                term.writeln('\r\n[연결 종료]');
            }
            setConnected(false);
            setStatus('DISCONNECTED', '연결 종료');
        };

        socket.onerror = function () {
            setStatus('ERROR', 'WebSocket 오류');
            setConnected(false);
        };
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        connect();
    });

    disconnectButton.addEventListener('click', function () {
        send({ type: 'disconnect' });
        if (socket) {
            socket.close();
        }
    });

    term.onData(function (data) {
        send({
            type: 'input',
            data: data
        });
    });

    window.addEventListener('resize', function () {
        window.clearTimeout(window.__terminalFitTimer);
        window.__terminalFitTimer = window.setTimeout(fit, 80);
    });
})();
