(function () {
    function id(name) {
        return document.getElementById(name);
    }

    function wsUrl(path) {
        var protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return protocol + '//' + window.location.host + servletPath + path;
    }

    function setStatus(el, text, kind) {
        el.textContent = text || '';
        el.className = 'status-line' + (kind ? ' ' + kind : '');
    }

    function apiUrl(path) {
        return servletPath + path;
    }

    function postForm(path, data) {
        var body = new URLSearchParams();
        Object.keys(data || {}).forEach(function (key) {
            body.append(key, data[key] == null ? '' : data[key]);
        });
        return fetch(apiUrl(path), {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: body.toString()
        }).then(parseJsonResponse);
    }

    function getJson(path) {
        return fetch(apiUrl(path), { method: 'GET' }).then(parseJsonResponse);
    }

    function parseJsonResponse(response) {
        return response.text().then(function (text) {
            var json;
            try {
                json = text ? JSON.parse(text) : {};
            } catch (e) {
                json = { result: 'ERROR', msg: text || response.statusText };
            }
            if (!response.ok || json.result === 'ERROR') {
                throw new Error(json.msg || json.error || ('HTTP ' + response.status));
            }
            return json;
        });
    }

    function makeTerminal(container) {
        var term = new Terminal({
            cursorBlink: true,
            convertEol: false,
            fontFamily: settings.terminalFontFamily,
            fontSize: settings.terminalFontSize,
            scrollback: 5000,
            theme: {
                background: '#050708',
                foreground: '#e8edf1',
                cursor: '#8ee0ff',
                selectionBackground: '#29485a'
            }
        });
        var fitAddon = new FitAddon.FitAddon();
        term.loadAddon(fitAddon);
        term.open(container);
        attachTerminalHelpers(term);
        fitAddon.fit();
        return { term: term, fitAddon: fitAddon };
    }

    function attachTerminalHelpers(term) {
        var isMac = navigator.platform && navigator.platform.toLowerCase().indexOf('mac') >= 0;

        term.attachCustomKeyEventHandler(function (event) {
            if (event.type !== 'keydown') return true;
            var key = event.key ? event.key.toLowerCase() : '';
            var copyKey = (event.ctrlKey && event.shiftKey && key === 'c')
                || (isMac && event.metaKey && !event.ctrlKey && key === 'c');
            var pasteKey = (event.ctrlKey && event.shiftKey && key === 'v')
                || (isMac && event.metaKey && !event.ctrlKey && key === 'v');

            if (copyKey) {
                var selection = term.getSelection();
                if (selection && navigator.clipboard) {
                    navigator.clipboard.writeText(selection).catch(function () {});
                    return false;
                }
            }

            if (pasteKey) {
                if (navigator.clipboard) {
                    navigator.clipboard.readText().then(function (text) {
                        if (text) term.paste(text);
                    }).catch(function () {});
                }
                return false;
            }

            return true;
        });

        if (!term.element) return;

        term.element.addEventListener('mousedown', function (event) {
            if (!event.shiftKey || event.button !== 0) return;
            if (term.getSelection()) {
                event.preventDefault();
                event.stopPropagation();
            }
        }, true);

        term.element.addEventListener('click', function (event) {
            if (!event.shiftKey || event.button !== 0) return;
            var selection = term.getSelection();
            if (selection) {
                if (navigator.clipboard) {
                    navigator.clipboard.writeText(selection).catch(function () {});
                }
            } else if (navigator.clipboard) {
                navigator.clipboard.readText().then(function (text) {
                    if (text) term.paste(text);
                }).catch(function () {});
            }
            event.preventDefault();
            event.stopPropagation();
        }, true);
    }

    function send(socket, payload) {
        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify(payload));
        }
    }

    function shellQuote(value) {
        return "'" + String(value).replace(/'/g, "'\\''") + "'";
    }

    function credentialKey(info) {
        return info.username + '@' + info.host + ':' + info.port;
    }

    function labelOf(info) {
        return info.username + '@' + info.host + ':' + info.port;
    }

    var servers = [];
    var quickCommands = [];
    var settings = {
        terminalFontFamily: '"JetBrains Mono"',
        terminalFontSize: 14
    };

    var ssh = {
        sessions: {},
        order: [],
        activeId: null
    };

    var agent = {
        session: null,
        running: false
    };

    function activeSession() {
        return ssh.activeId ? ssh.sessions[ssh.activeId] : null;
    }

    function renderSavedServers() {
        var select = id('savedServers');
        select.innerHTML = '';
        if (!servers.length) {
            var empty = document.createElement('option');
            empty.value = '';
            empty.textContent = '저장 서버 없음';
            select.appendChild(empty);
            id('openSavedServer').disabled = true;
            id('deleteSavedServer').disabled = true;
            return;
        }
        id('openSavedServer').disabled = false;
        id('deleteSavedServer').disabled = false;
        servers.forEach(function (server) {
            var option = document.createElement('option');
            option.value = String(server.id);
            option.textContent = labelOf(server) + (server.password ? '' : ' (PW 필요)');
            select.appendChild(option);
        });
    }

    function loadServers() {
        return getJson('/api/workspace/listServers.do').then(function (data) {
            servers = data.servers || [];
            renderSavedServers();
        }).catch(function (err) {
            setStatus(id('workspaceStatus'), 'SSH 설정 로딩 실패: ' + err.message, 'err');
        });
    }

    function loadSettings() {
        return getJson('/api/workspace/getSettings.do').then(function (data) {
            settings = normalizeSettings(data.settings || settings);
            fillSettingsForm();
            applyTerminalSettings();
        }).catch(function (err) {
            setStatus(id('workspaceStatus'), '설정 로딩 실패: ' + err.message, 'err');
        });
    }

    function normalizeSettings(value) {
        var fontSize = parseInt(value.terminalFontSize, 10);
        if (!isFinite(fontSize)) fontSize = 14;
        fontSize = Math.max(10, Math.min(24, fontSize));
        return {
            terminalFontFamily: value.terminalFontFamily || '"JetBrains Mono"',
            terminalFontSize: fontSize
        };
    }

    function fillSettingsForm() {
        id('terminalFontFamily').value = settings.terminalFontFamily;
        if (id('terminalFontFamily').value !== settings.terminalFontFamily) {
            id('terminalFontFamily').selectedIndex = 0;
        }
        id('terminalFontSize').value = settings.terminalFontSize;
    }

    function openSettingsDialog() {
        fillSettingsForm();
        id('settingsDialog').style.display = 'grid';
    }

    function closeSettingsDialog() {
        id('settingsDialog').style.display = 'none';
        setStatus(id('settingsStatus'), '', '');
    }

    function saveSettings() {
        postForm('/api/workspace/saveSettings.do', {
            terminalFontFamily: id('terminalFontFamily').value,
            terminalFontSize: id('terminalFontSize').value
        }).then(function (data) {
            settings = normalizeSettings(data.settings || settings);
            fillSettingsForm();
            applyTerminalSettings();
            setStatus(id('settingsStatus'), '저장됨', 'ok');
            window.setTimeout(closeSettingsDialog, 600);
        }).catch(function (err) {
            setStatus(id('settingsStatus'), '저장 실패: ' + err.message, 'err');
        });
    }

    function applyTerminalSettings() {
        ssh.order.forEach(function (sessionId) {
            applyTerminalSettingToSession(ssh.sessions[sessionId]);
        });
        if (agent.session) {
            applyTerminalSettingToSession(agent.session);
        }
    }

    function applyTerminalSettingToSession(session) {
        if (!session || !session.term) return;
        session.term.options.fontFamily = settings.terminalFontFamily;
        session.term.options.fontSize = settings.terminalFontSize;
        resizeSession(session);
    }

    function exportSshServers() {
        window.location.href = apiUrl('/api/workspace/exportServers.do');
    }

    function saveServer(info, savePassword) {
        return postForm('/api/workspace/saveServer.do', {
            host: info.host,
            port: info.port,
            username: info.username,
            password: savePassword ? info.password : ''
        }).then(function () {
            return loadServers();
        });
    }

    function selectedSavedServer() {
        var value = id('savedServers').value;
        for (var i = 0; i < servers.length; i++) {
            if (String(servers[i].id) === value) return servers[i];
        }
        return null;
    }

    function renderQuickCommands() {
        var box = id('quickCommandList');
        box.innerHTML = '';
        if (!quickCommands.length) {
            var empty = document.createElement('span');
            empty.className = 'status-line';
            empty.textContent = '등록된 명령이 없습니다.';
            box.appendChild(empty);
            return;
        }
        quickCommands.forEach(function (cmd) {
            var item = document.createElement('span');
            item.className = 'quick-cmd';

            var run = document.createElement('button');
            run.type = 'button';
            run.className = 'quick-cmd-run';
            run.textContent = cmd.command;
            run.title = cmd.command;
            run.addEventListener('click', function () {
                sendQuickCommand(cmd.command);
            });

            var remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'quick-cmd-remove';
            remove.textContent = 'x';
            remove.title = '삭제';
            remove.addEventListener('click', function () {
                postForm('/api/workspace/deleteCommand.do', { id: cmd.id })
                    .then(loadCommands)
                    .catch(function (err) {
                        setStatus(id('quickCommandStatus'), '삭제 실패: ' + err.message, 'err');
                    });
            });

            item.appendChild(run);
            item.appendChild(remove);
            box.appendChild(item);
        });
    }

    function addQuickCommand() {
        var input = id('quickCommandInput');
        var value = input.value.trim();
        if (!value) return;
        postForm('/api/workspace/saveCommand.do', { command: value })
            .then(function () {
                input.value = '';
                setStatus(id('quickCommandStatus'), '추가됨', 'ok');
                window.setTimeout(function () {
                    setStatus(id('quickCommandStatus'), '', '');
                }, 1200);
                return loadCommands();
            })
            .catch(function (err) {
                setStatus(id('quickCommandStatus'), '추가 실패: ' + err.message, 'err');
            });
    }

    function loadCommands() {
        return getJson('/api/workspace/listCommands.do').then(function (data) {
            quickCommands = data.commands || [];
            renderQuickCommands();
        }).catch(function (err) {
            setStatus(id('quickCommandStatus'), '명령 로딩 실패: ' + err.message, 'err');
        });
    }

    function sendQuickCommand(cmd) {
        var session = activeSession();
        if (!session || !session.socket || session.socket.readyState !== WebSocket.OPEN) {
            setStatus(id('quickCommandStatus'), '활성 SSH 탭이 없습니다.', 'err');
            return;
        }
        send(session.socket, { type: 'input', data: cmd + '\n' });
        session.term.focus();
    }

    function openSshDialog(server) {
        id('sshDialog').style.display = 'grid';
        if (server) {
            id('sshHost').value = server.host || '';
            id('sshPort').value = server.port || 22;
            id('sshUser').value = server.username || '';
            id('sshPassword').value = server.password || '';
            id('saveSshInfo').checked = true;
            id('saveSshPassword').checked = !!server.password;
        }
        id('sshHost').focus();
    }

    function closeSshDialog() {
        id('sshDialog').style.display = 'none';
        setStatus(id('sshConnectStatus'), '', '');
    }

    function createSession(info, options) {
        options = options || {};
        var sessionId = 'ssh-' + Date.now() + '-' + Math.random().toString(36).slice(2);
        var panel = document.createElement('div');
        panel.className = 'terminal-box';
        panel.style.display = 'none';
        (options.container || id('sshTermContainer')).appendChild(panel);

        var terminalParts = makeTerminal(panel);
        var session = {
            id: sessionId,
            kind: options.kind || 'shell',
            info: info,
            label: labelOf(info),
            panel: panel,
            term: terminalParts.term,
            fitAddon: terminalParts.fitAddon,
            socket: null,
            connected: false,
            closing: false
        };
        ssh.sessions[sessionId] = session;
        if (!options.detached) {
            ssh.order.push(sessionId);
            setActiveSession(sessionId);
        }
        return session;
    }

    function connectSession(info, options) {
        options = options || {};
        var session = createSession(info, options);
        session.term.writeln('SSH connecting to ' + labelOf(info) + ' ...');
        setStatus(id('sshStatus'), '연결 중', 'run');
        setStatus(id('sshConnectStatus'), '연결 중...', 'run');

        session.socket = new WebSocket(wsUrl('/ssh-terminal'));
        session.socket.onopen = function () {
            resizeSession(session);
            send(session.socket, {
                type: 'connect',
                host: info.host,
                port: info.port,
                username: info.username,
                password: info.password,
                initialCommand: options.initialCommand || '',
                cols: session.term.cols,
                rows: session.term.rows
            });
        };
        session.socket.onmessage = function (event) {
            var data = JSON.parse(event.data);
            if (data.type === 'output') {
                session.term.write(data.data);
            } else if (data.type === 'status') {
                if (data.state === 'CONNECTED') {
                    session.connected = true;
                    closeSshDialog();
                    id('disconnectSsh').disabled = false;
                    updateHeaderStatus();
                    session.term.focus();
                    if (options.onConnected) options.onConnected(session);
                } else if (data.state === 'DISCONNECTED') {
                    closeSession(session.id, false);
                } else if (session.id === ssh.activeId) {
                    setStatus(id('sshStatus'), data.message, 'run');
                }
            } else if (data.type === 'error') {
                session.term.writeln('\r\n[ERROR] ' + data.message);
                if (session.id === ssh.activeId) setStatus(id('sshStatus'), data.message, 'err');
                setStatus(id('sshConnectStatus'), data.message, 'err');
            }
        };
        session.socket.onclose = function () {
            if (!session.closing) closeSession(session.id, false);
        };
        session.socket.onerror = function () {
            if (session.id === ssh.activeId) setStatus(id('sshStatus'), 'WebSocket 오류', 'err');
        };
        session.term.onData(function (value) {
            send(session.socket, { type: 'input', data: value });
        });
        return session;
    }

    function connectFromDialog() {
        var info = {
            host: id('sshHost').value.trim(),
            port: parseInt(id('sshPort').value, 10) || 22,
            username: id('sshUser').value.trim(),
            password: id('sshPassword').value
        };
        if (!info.host || !info.username || !info.password) {
            setStatus(id('sshConnectStatus'), '주소, ID, PW를 입력하세요.', 'err');
            return;
        }
        var connectNow = function () {
            connectSession(info);
            id('sshPassword').value = '';
        };
        if (id('saveSshInfo').checked) {
            saveServer(info, id('saveSshPassword').checked)
                .then(connectNow)
                .catch(function (err) {
                    setStatus(id('sshConnectStatus'), '저장 실패: ' + err.message, 'err');
                });
            return;
        }
        connectSession(info);
        id('sshPassword').value = '';
    }

    function renderTabs() {
        var tabs = id('sshTabs');
        tabs.innerHTML = '';
        ssh.order.forEach(function (sessionId) {
            var session = ssh.sessions[sessionId];
            if (!session) return;
            var tab = document.createElement('button');
            tab.type = 'button';
            tab.className = 'ssh-tab' + (sessionId === ssh.activeId ? ' active' : '');
            tab.title = session.label;

            var title = document.createElement('span');
            title.className = 'ssh-tab-title';
            title.textContent = (session.kind === 'agent' ? 'AI ' : '') + session.label;

            var close = document.createElement('span');
            close.className = 'ssh-tab-close';
            close.textContent = 'x';
            close.addEventListener('click', function (event) {
                event.stopPropagation();
                closeSession(sessionId, true);
            });

            tab.addEventListener('click', function () {
                setActiveSession(sessionId);
            });
            tab.appendChild(title);
            tab.appendChild(close);
            tabs.appendChild(tab);
        });
    }

    function setActiveSession(sessionId) {
        if (ssh.activeId && ssh.sessions[ssh.activeId]) {
            ssh.sessions[ssh.activeId].panel.style.display = 'none';
        }
        ssh.activeId = sessionId && ssh.sessions[sessionId] ? sessionId : null;
        var session = activeSession();
        if (session) {
            session.panel.style.display = '';
            id('sshPlaceholder').style.display = 'none';
            resizeSession(session);
            session.term.focus();
        } else {
            id('sshPlaceholder').style.display = '';
        }
        renderTabs();
        updateHeaderStatus();
    }

    function updateHeaderStatus() {
        var session = activeSession();
        if (!session) {
            setStatus(id('sshStatus'), '미연결', '');
            id('disconnectSsh').disabled = true;
            return;
        }
        id('disconnectSsh').disabled = false;
        setStatus(id('sshStatus'), session.label + (session.connected ? '' : ' 연결 중'), session.connected ? 'ok' : 'run');
    }

    function resizeSession(session) {
        if (!session || !session.fitAddon || !session.term) return;
        try { session.fitAddon.fit(); } catch (e) {}
        send(session.socket, { type: 'resize', cols: session.term.cols, rows: session.term.rows });
    }

    function closeSession(sessionId, notify) {
        var session = ssh.sessions[sessionId];
        if (!session) return;
        session.closing = true;
        if (notify !== false) send(session.socket, { type: 'disconnect' });
        if (session.socket) {
            try { session.socket.close(); } catch (e) {}
        }
        if (session.term) {
            try { session.term.dispose(); } catch (e) {}
        }
        if (session.panel && session.panel.parentNode) session.panel.parentNode.removeChild(session.panel);
        delete ssh.sessions[sessionId];
        ssh.order = ssh.order.filter(function (idValue) { return idValue !== sessionId; });
        if (agent.session && agent.session.id === sessionId) {
            agent.session = null;
            agent.running = false;
            cleanupAgentUi();
        }
        if (ssh.activeId === sessionId) {
            setActiveSession(ssh.order.length ? ssh.order[ssh.order.length - 1] : null);
        } else {
            renderTabs();
            updateHeaderStatus();
        }
    }

    function selectedAgent() {
        var radios = document.getElementsByName('agentKind');
        for (var i = 0; i < radios.length; i++) {
            if (radios[i].checked) return radios[i].value;
        }
        return 'codex';
    }

    function refreshAgentTitle() {
        id('agentTitle').textContent = selectedAgent() === 'codex' ? 'Codex' : 'Claude Code';
    }

    function buildRemoteAgentCommand(kind, cwd, customCommand) {
        var command = customCommand || (kind === 'claude' ? 'claude' : 'codex');
        if (cwd) {
            return 'cd ' + shellQuote(cwd) + ' && ' + command;
        }
        return command;
    }

    function startAgent() {
        var base = activeSession();
        if (!base || !base.connected) {
            setStatus(id('agentStatus'), '활성 SSH 탭이 필요합니다.', 'err');
            return;
        }
        if (agent.session) {
            closeSession(agent.session.id, true);
            agent.session = null;
        }

        var kind = selectedAgent();
        var remoteCommand = buildRemoteAgentCommand(
            kind,
            id('agentCwd').value.trim(),
            id('agentCommand').value.trim()
        );
        id('agentForm').style.display = 'none';
        id('agentTerm').style.display = '';
        id('agentTerm').innerHTML = '';
        setStatus(id('agentStatus'), '원격 SSH에서 시작 중...', 'run');
        id('startAgent').disabled = true;

        var info = {
            host: base.info.host,
            port: base.info.port,
            username: base.info.username,
            password: base.info.password
        };
        var session = connectSession(info, {
            kind: 'agent',
            detached: true,
            container: id('agentTerm'),
            initialCommand: remoteCommand,
            onConnected: function () {
                agent.running = true;
                id('stopAgent').disabled = false;
                setStatus(id('agentStatus'), '원격 실행 중: ' + remoteCommand, 'ok');
            }
        });
        agent.session = session;
        session.panel.style.display = '';
    }

    function cleanupAgentUi() {
        id('stopAgent').disabled = true;
        id('startAgent').disabled = false;
        id('agentTerm').style.display = 'none';
        id('agentTerm').innerHTML = '';
        id('agentForm').style.display = 'grid';
    }

    function stopAgent() {
        if (agent.session) {
            closeSession(agent.session.id, true);
            agent.session = null;
        }
        agent.running = false;
        cleanupAgentUi();
    }

    function setupSplitter() {
        var splitter = id('workspaceSplitter');
        var grid = id('workspaceGrid');
        var dragging = false;
        splitter.addEventListener('mousedown', function () {
            dragging = true;
            splitter.classList.add('dragging');
        });
        window.addEventListener('mouseup', function () {
            dragging = false;
            splitter.classList.remove('dragging');
        });
        window.addEventListener('mousemove', function (event) {
            if (!dragging || window.innerWidth <= 820) return;
            var rect = grid.getBoundingClientRect();
            var ratio = (event.clientX - rect.left) / rect.width;
            ratio = Math.max(0.25, Math.min(0.75, ratio));
            grid.style.setProperty('--split-left', ratio + 'fr');
            grid.style.setProperty('--split-right', (1 - ratio) + 'fr');
            window.setTimeout(function () {
                ssh.order.forEach(function (sessionId) { resizeSession(ssh.sessions[sessionId]); });
                if (agent.session) resizeSession(agent.session);
            }, 0);
        });
    }

    id('openSshDialog').addEventListener('click', function () { openSshDialog(); });
    id('cancelSshDialog').addEventListener('click', closeSshDialog);
    id('connectSsh').addEventListener('click', connectFromDialog);
    id('disconnectSsh').addEventListener('click', function () {
        var session = activeSession();
        if (session) closeSession(session.id, true);
    });
    id('openSavedServer').addEventListener('click', function () {
        var server = selectedSavedServer();
        if (!server) return;
        if (server.password) {
            connectSession({
                host: server.host,
                port: server.port,
                username: server.username,
                password: server.password
            });
        } else {
            openSshDialog(server);
            setStatus(id('sshConnectStatus'), '저장된 PW가 없습니다. PW 입력 후 연결하세요.', 'run');
        }
    });
    id('deleteSavedServer').addEventListener('click', function () {
        var server = selectedSavedServer();
        if (!server) return;
        postForm('/api/workspace/deleteServer.do', { id: server.id })
            .then(loadServers)
            .catch(function (err) {
                setStatus(id('workspaceStatus'), '삭제 실패: ' + err.message, 'err');
            });
    });
    id('exportSshServers').addEventListener('click', exportSshServers);
    id('openSettings').addEventListener('click', openSettingsDialog);
    id('cancelSettings').addEventListener('click', closeSettingsDialog);
    id('saveSettings').addEventListener('click', saveSettings);
    id('addQuickCommand').addEventListener('click', addQuickCommand);
    id('quickCommandInput').addEventListener('keydown', function (event) {
        if (event.key === 'Enter') addQuickCommand();
    });
    id('startAgent').addEventListener('click', startAgent);
    id('stopAgent').addEventListener('click', stopAgent);

    var radios = document.getElementsByName('agentKind');
    for (var i = 0; i < radios.length; i++) {
        radios[i].addEventListener('change', refreshAgentTitle);
    }

    ['sshHost', 'sshUser', 'sshPassword'].forEach(function (name) {
        id(name).addEventListener('keydown', function (event) {
            if (event.key === 'Enter') connectFromDialog();
        });
    });

    window.addEventListener('resize', function () {
        window.clearTimeout(window.__workspaceFitTimer);
        window.__workspaceFitTimer = window.setTimeout(function () {
            ssh.order.forEach(function (sessionId) { resizeSession(ssh.sessions[sessionId]); });
            if (agent.session) resizeSession(agent.session);
        }, 80);
    });

    setupSplitter();
    loadSettings();
    loadServers();
    loadCommands();
    refreshAgentTitle();
    renderTabs();
})();
