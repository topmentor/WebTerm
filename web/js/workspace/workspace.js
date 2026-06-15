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

    var DEFAULT_TERMINAL_FONT_FAMILY = '"JetBrains Mono", "Cascadia Mono", Consolas, "Courier New", monospace';
    var terminalShortcutTerms = [];
    var terminalShortcutGuardInstalled = false;

    function isMacPlatform() {
        return navigator.platform && navigator.platform.toLowerCase().indexOf('mac') >= 0;
    }

    function isTerminalCopyKey(event) {
        var key = event.key ? event.key.toLowerCase() : '';
        return (event.ctrlKey && event.shiftKey && key === 'c')
            || (isMacPlatform() && event.metaKey && !event.ctrlKey && key === 'c');
    }

    function isTerminalPasteKey(event) {
        var key = event.key ? event.key.toLowerCase() : '';
        return (event.ctrlKey && event.shiftKey && key === 'v')
            || (isMacPlatform() && event.metaKey && !event.ctrlKey && key === 'v');
    }

    function writeClipboardText(text) {
        if (!text) return;
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).catch(function () {});
            return;
        }

        var textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.setAttribute('readonly', 'readonly');
        textarea.style.position = 'fixed';
        textarea.style.left = '-9999px';
        textarea.style.top = '0';
        document.body.appendChild(textarea);
        textarea.select();
        try { document.execCommand('copy'); } catch (e) {}
        document.body.removeChild(textarea);
    }

    function readClipboardText(callback) {
        if (navigator.clipboard && navigator.clipboard.readText) {
            navigator.clipboard.readText().then(function (text) {
                callback(text || '');
            }).catch(function () {});
        }
    }

    function findShortcutTerminal(event) {
        var target = event.target;
        var activeElement = document.activeElement;
        for (var i = terminalShortcutTerms.length - 1; i >= 0; i--) {
            var term = terminalShortcutTerms[i];
            if (!term || !term.element) continue;
            var activeInside = term.element.contains(activeElement) || term.element.contains(target);
            var selection = term.getSelection ? term.getSelection() : '';
            if (activeInside || selection) return term;
        }
        return null;
    }

    function stopBrowserShortcut(event) {
        event.preventDefault();
        event.stopPropagation();
        if (event.stopImmediatePropagation) event.stopImmediatePropagation();
    }

    function installTerminalShortcutGuard() {
        if (terminalShortcutGuardInstalled) return;
        terminalShortcutGuardInstalled = true;
        document.addEventListener('keydown', function (event) {
            if (!isTerminalCopyKey(event) && !isTerminalPasteKey(event)) return;
            var term = findShortcutTerminal(event);
            if (!term) return;

            stopBrowserShortcut(event);
            if (isTerminalCopyKey(event)) {
                writeClipboardText(term.getSelection ? term.getSelection() : '');
            } else {
                readClipboardText(function (text) {
                    if (text && term.paste) term.paste(text);
                });
            }
        }, true);
    }

    function registerTerminalShortcuts(term) {
        terminalShortcutTerms.push(term);
        installTerminalShortcutGuard();
    }

    function unregisterTerminalShortcuts(term) {
        terminalShortcutTerms = terminalShortcutTerms.filter(function (item) {
            return item !== term;
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
        registerTerminalShortcuts(term);
        attachTerminalHelpers(term);
        fitAddon.fit();
        return { term: term, fitAddon: fitAddon };
    }

    function attachTerminalHelpers(term) {
        term.attachCustomKeyEventHandler(function (event) {
            if (event.type !== 'keydown') return true;
            var copyKey = isTerminalCopyKey(event);
            var pasteKey = isTerminalPasteKey(event);

            if (copyKey) {
                var selection = term.getSelection();
                writeClipboardText(selection);
                return false;
            }

            if (pasteKey) {
                readClipboardText(function (text) {
                    if (text) term.paste(text);
                });
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
                writeClipboardText(selection);
            } else {
                readClipboardText(function (text) {
                    if (text) term.paste(text);
                });
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

    function credentialKey(info) {
        return info.username + '@' + info.host + ':' + info.port;
    }

    function labelOf(info) {
        return info.username + '@' + info.host + ':' + info.port;
    }

    function shellQuote(value) {
        return "'" + String(value).replace(/'/g, "'\"'\"'") + "'";
    }

    var servers = [];
    var quickCommands = [];
    var settings = {
        terminalFontFamily: DEFAULT_TERMINAL_FONT_FAMILY,
        terminalFontSize: 14
    };

    var ssh = {
        sessions: {},
        order: [],
        activeId: null
    };

    var agent = {
        session: null,
        historySession: null,
        running: false
    };

    function activeSession() {
        return ssh.activeId ? ssh.sessions[ssh.activeId] : null;
    }

    function connectionKey(info) {
        return [
            String(info.username || '').trim(),
            String(info.host || '').trim().toLowerCase(),
            String(parseInt(info.port, 10) || 22)
        ].join('@');
    }

    function firstShellSession() {
        for (var i = 0; i < ssh.order.length; i++) {
            var session = ssh.sessions[ssh.order[i]];
            if (session && session.kind === 'shell') return session;
        }
        return null;
    }

    function canOpenSshConnection(info, statusElement) {
        var existing = firstShellSession();
        if (!existing) return true;
        if (connectionKey(existing.info) === connectionKey(info)) return true;

        var message = '다른 서버 연결은 추가로 열 수 없습니다. 현재 연결: ' + labelOf(existing.info);
        setStatus(statusElement || id('sshStatus'), message, 'err');
        return false;
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
            option.textContent = labelOf(server) + ' (PW 입력)';
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
            terminalFontFamily: value.terminalFontFamily || DEFAULT_TERMINAL_FONT_FAMILY,
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
            password: '',
            privateKey: '',
            privateKeyPassphrase: ''
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
            id('sshPassword').value = '';
            id('saveSshInfo').checked = true;
        } else {
            id('sshHost').value = '';
            id('sshPort').value = 22;
            id('sshUser').value = '';
            id('sshPassword').value = '';
            id('saveSshInfo').checked = true;
        }
        id(server ? 'sshPassword' : 'sshHost').focus();
    }

    function closeSshDialog() {
        id('sshDialog').style.display = 'none';
        setStatus(id('sshConnectStatus'), '', '');
    }

    function sftpCredentials(session) {
        return {
            host: session.info.host,
            port: session.info.port,
            username: session.info.username,
            password: session.info.password || '',
            privateKey: session.info.privateKey || '',
            privateKeyPassphrase: session.info.privateKeyPassphrase || ''
        };
    }

    function postSftp(session, action, data) {
        var payload = sftpCredentials(session);
        Object.keys(data || {}).forEach(function (key) {
            payload[key] = data[key];
        });
        return postForm('/api/workspace/' + action + '.do', payload);
    }

    function createFileViewer() {
        var current = activeSession();
        var base = current && current.kind !== 'file' ? current : firstShellSession();
        if (!base || !base.connected) {
            setStatus(id('sshStatus'), '먼저 SSH 서버에 연결하세요.', 'err');
            return;
        }

        setStatus(id('sshStatus'), 'SFTP 연결 중...', 'run');
        postSftp(base, 'sftpList', { path: '.' }).then(function (data) {
            var sessionId = 'file-' + Date.now() + '-' + Math.random().toString(36).slice(2);
            var panel = document.createElement('div');
            panel.className = 'file-browser-panel';
            panel.style.display = 'none';
            id('sshTermContainer').appendChild(panel);

            var session = {
                id: sessionId,
                kind: 'file',
                info: base.info,
                label: '파일 ' + labelOf(base.info),
                panel: panel,
                connected: true,
                path: data.path || '.',
                files: []
            };
            ssh.sessions[sessionId] = session;
            ssh.order.push(sessionId);
            renderFileBrowser(session, data);
            setActiveSession(sessionId);
            setStatus(id('sshStatus'), 'SFTP 연결됨: ' + session.path, 'ok');
        }).catch(function (err) {
            setStatus(id('sshStatus'), 'SFTP 연결 실패: ' + err.message, 'err');
        });
    }

    function renderFileBrowser(session, data) {
        session.path = data.path || session.path || '.';
        session.files = data.files || [];
        var panel = session.panel;
        panel.innerHTML = '';

        var toolbar = document.createElement('div');
        toolbar.className = 'file-toolbar';

        var up = makeFileButton('상위', function () {
            if (data.parent) loadFilePath(session, data.parent);
        });
        up.disabled = !data.parent || data.parent === session.path;

        var refresh = makeFileButton('새로고침', function () {
            loadFilePath(session, session.path);
        });

        var mkdir = makeFileButton('새 폴더', function () {
            var name = window.prompt('새 폴더 이름');
            if (!name) return;
            sftpAction(session, 'sftpMkdir', { path: joinRemotePath(session.path, name) });
        });

        var upload = makeFileButton('업로드', function () {
            var input = panel.querySelector('.file-upload-input');
            if (input) input.click();
        });

        var pathInput = document.createElement('input');
        pathInput.className = 'file-path-input';
        pathInput.value = session.path;
        pathInput.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') loadFilePath(session, pathInput.value);
        });

        var uploadInput = document.createElement('input');
        uploadInput.className = 'file-upload-input';
        uploadInput.type = 'file';
        uploadInput.multiple = true;
        uploadInput.addEventListener('change', function () {
            uploadFiles(session, uploadInput.files);
            uploadInput.value = '';
        });

        toolbar.appendChild(up);
        toolbar.appendChild(refresh);
        toolbar.appendChild(mkdir);
        toolbar.appendChild(upload);
        toolbar.appendChild(pathInput);
        toolbar.appendChild(uploadInput);

        var tableWrap = document.createElement('div');
        tableWrap.className = 'file-table-wrap';
        var table = document.createElement('table');
        table.className = 'file-table';
        table.innerHTML = '<thead><tr><th>이름</th><th>크기</th><th>권한</th><th>수정일</th><th></th></tr></thead>';
        var tbody = document.createElement('tbody');
        session.files.sort(function (a, b) {
            if (a.dir !== b.dir) return a.dir ? -1 : 1;
            return a.name.localeCompare(b.name);
        }).forEach(function (file) {
            tbody.appendChild(renderFileRow(session, file));
        });
        table.appendChild(tbody);
        tableWrap.appendChild(table);

        var status = document.createElement('div');
        status.className = 'file-status status-line';
        status.textContent = session.files.length + '개 항목';

        panel.appendChild(toolbar);
        panel.appendChild(tableWrap);
        panel.appendChild(status);
    }

    function makeFileButton(text, handler) {
        var button = document.createElement('button');
        button.type = 'button';
        button.textContent = text;
        button.addEventListener('click', handler);
        return button;
    }

    function renderFileRow(session, file) {
        var tr = document.createElement('tr');
        tr.className = file.dir ? 'is-dir' : 'is-file';

        var name = document.createElement('td');
        var nameButton = makeFileButton((file.dir ? '[DIR] ' : '') + file.name, function () {
            if (file.dir) {
                loadFilePath(session, file.path);
            } else {
                downloadFile(session, file.path);
            }
        });
        nameButton.className = 'file-name-button';
        name.appendChild(nameButton);

        var size = document.createElement('td');
        size.textContent = file.dir ? '-' : formatBytes(file.size);

        var perms = document.createElement('td');
        perms.textContent = file.permissions || '';

        var modified = document.createElement('td');
        modified.textContent = file.modified ? new Date(file.modified).toLocaleString() : '';

        var actions = document.createElement('td');
        actions.className = 'file-actions';
        if (!file.dir) {
            actions.appendChild(makeFileButton('다운로드', function () { downloadFile(session, file.path); }));
        }
        actions.appendChild(makeFileButton('이름변경', function () {
            var nextName = window.prompt('새 이름', file.name);
            if (!nextName || nextName === file.name) return;
            sftpAction(session, 'sftpRename', {
                from: file.path,
                to: joinRemotePath(session.path, nextName)
            });
        }));
        actions.appendChild(makeFileButton('삭제', function () {
            if (!window.confirm(file.name + ' 삭제?')) return;
            sftpAction(session, 'sftpDelete', { path: file.path, dir: file.dir ? 'true' : 'false' });
        }));

        tr.appendChild(name);
        tr.appendChild(size);
        tr.appendChild(perms);
        tr.appendChild(modified);
        tr.appendChild(actions);
        return tr;
    }

    function loadFilePath(session, path) {
        setStatus(id('sshStatus'), '파일 목록 로딩 중...', 'run');
        postSftp(session, 'sftpList', { path: path }).then(function (data) {
            renderFileBrowser(session, data);
            updateHeaderStatus();
        }).catch(function (err) {
            setStatus(id('sshStatus'), '파일 목록 실패: ' + err.message, 'err');
        });
    }

    function sftpAction(session, action, data) {
        setStatus(id('sshStatus'), 'SFTP 작업 중...', 'run');
        postSftp(session, action, data).then(function () {
            loadFilePath(session, session.path);
        }).catch(function (err) {
            setStatus(id('sshStatus'), 'SFTP 작업 실패: ' + err.message, 'err');
        });
    }

    function uploadFiles(session, files) {
        if (!files || !files.length) return;
        var form = new FormData();
        var credentials = sftpCredentials(session);
        Object.keys(credentials).forEach(function (key) {
            form.append(key, credentials[key] == null ? '' : credentials[key]);
        });
        form.append('path', session.path);
        Array.prototype.forEach.call(files, function (file) {
            form.append('file', file, file.name);
        });
        setStatus(id('sshStatus'), '업로드 중...', 'run');
        fetch(apiUrl('/api/workspace/sftpUpload.do'), { method: 'POST', body: form })
            .then(parseJsonResponse)
            .then(function () { loadFilePath(session, session.path); })
            .catch(function (err) {
                setStatus(id('sshStatus'), '업로드 실패: ' + err.message, 'err');
            });
    }

    function downloadFile(session, path) {
        var form = document.createElement('form');
        form.method = 'POST';
        form.action = apiUrl('/api/workspace/sftpDownload.do');
        form.style.display = 'none';
        var data = sftpCredentials(session);
        data.path = path;
        Object.keys(data).forEach(function (key) {
            var input = document.createElement('input');
            input.type = 'hidden';
            input.name = key;
            input.value = data[key] == null ? '' : data[key];
            form.appendChild(input);
        });
        document.body.appendChild(form);
        form.submit();
        document.body.removeChild(form);
    }

    function joinRemotePath(dir, name) {
        var left = dir || '.';
        var right = String(name || '').replace(/^\/+/, '');
        if (left === '/') return '/' + right;
        return left.replace(/\/+$/, '') + '/' + right;
    }

    function formatBytes(value) {
        var size = Number(value) || 0;
        if (size < 1024) return size + ' B';
        var units = ['KB', 'MB', 'GB', 'TB'];
        var idx = -1;
        do {
            size = size / 1024;
            idx++;
        } while (size >= 1024 && idx < units.length - 1);
        return size.toFixed(size >= 10 ? 0 : 1) + ' ' + units[idx];
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
            label: options.label || labelOf(info),
            panel: panel,
            term: terminalParts.term,
            fitAddon: terminalParts.fitAddon,
            socket: null,
            connected: false,
            closing: false,
            agentLaunch: !!options.agentLaunch,
            agentCommand: options.agentCommand || '',
            agentOutputBuffer: ''
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
        if (!options.detached && !canOpenSshConnection(info, id('sshConnectStatus'))) {
            return null;
        }
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
                privateKey: info.privateKey || '',
                privateKeyPassphrase: info.privateKeyPassphrase || '',
                initialCommand: options.initialCommand || '',
                cols: session.term.cols,
                rows: session.term.rows
            });
        };
        session.socket.onmessage = function (event) {
            var data = JSON.parse(event.data);
            if (data.type === 'output') {
                session.term.write(data.data);
                if (session.kind === 'agent') {
                    updateAgentStatusFromOutput(session, data.data);
                }
            } else if (data.type === 'status') {
                if (data.state === 'CONNECTED') {
                    session.connected = true;
                    closeSshDialog();
                    id('disconnectSsh').disabled = false;
                    updateHeaderStatus();
                    session.term.focus();
                    if (options.onConnected) options.onConnected(session);
                } else if (data.state === 'DISCONNECTED') {
                    if (session.kind === 'agent' && session.preserveTerminalOnClose) {
                        preserveAgentTerminal(session, data.message || '원격 AI 접속이 종료되었습니다.');
                    } else {
                        closeSession(session.id, false);
                    }
                } else if (session.id === ssh.activeId) {
                    setStatus(id('sshStatus'), data.message, 'run');
                }
            } else if (data.type === 'error') {
                session.term.writeln('\r\n[ERROR] ' + data.message);
                if (session.kind === 'agent') {
                    setStatus(id('agentStatus'), data.message, 'err');
                    setAgentPrimaryButtonsDisabled(false);
                    updateAgentActionState();
                }
                if (session.id === ssh.activeId) setStatus(id('sshStatus'), data.message, 'err');
                setStatus(id('sshConnectStatus'), data.message, 'err');
            }
        };
        session.socket.onclose = function () {
            if (session.kind === 'agent' && session.preserveTerminalOnClose) {
                preserveAgentTerminal(session, '원격 AI 접속이 종료되었습니다.');
            } else if (!session.closing) {
                var failedBeforeConnect = !session.connected;
                closeSession(session.id, false);
                if (failedBeforeConnect) {
                    setStatus(id('sshStatus'), 'SSH WebSocket 연결이 닫혔습니다. HTTPS 프록시의 WebSocket upgrade 설정을 확인하세요.', 'err');
                    setStatus(id('sshConnectStatus'), 'SSH WebSocket 연결 실패. wss 프록시 설정 또는 Tomcat WebSocket 배포를 확인하세요.', 'err');
                    if (session.kind === 'agent') {
                        setStatus(id('agentStatus'), '원격 AI SSH 연결 실패. wss 프록시 설정 또는 원격 SSH 인증 정보를 확인하세요.', 'err');
                        setAgentPrimaryButtonsDisabled(false);
                    }
                }
            }
        };
        session.socket.onerror = function () {
            if (session.kind === 'agent') {
                setStatus(id('agentStatus'), '원격 AI WebSocket 오류. HTTPS 프록시의 Upgrade/Connection 헤더 설정을 확인하세요.', 'err');
                setAgentPrimaryButtonsDisabled(false);
            }
            if (session.id === ssh.activeId) {
                setStatus(id('sshStatus'), 'SSH WebSocket 오류. HTTPS 프록시의 Upgrade/Connection 헤더 설정을 확인하세요.', 'err');
            }
            setStatus(id('sshConnectStatus'), 'SSH WebSocket 오류. 서버 프록시가 wss를 지원해야 합니다.', 'err');
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
            password: id('sshPassword').value,
            privateKey: '',
            privateKeyPassphrase: ''
        };
        if (!info.host || !info.username || !info.password) {
            setStatus(id('sshConnectStatus'), '주소, ID, PW를 입력하세요.', 'err');
            return;
        }
        if (!canOpenSshConnection(info, id('sshConnectStatus'))) {
            return;
        }
        var connectNow = function () {
            connectSession(info);
            id('sshPassword').value = '';
        };
        if (id('saveSshInfo').checked) {
            saveServer(info, false)
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
            if (session.term) session.term.focus();
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
            id('openFileViewer').disabled = !firstShellSession() || !firstShellSession().connected;
            updateAgentActionState();
            return;
        }
        id('disconnectSsh').disabled = false;
        id('openFileViewer').disabled = !firstShellSession() || !firstShellSession().connected;
        if (session.kind === 'file') {
            setStatus(id('sshStatus'), 'SFTP: ' + (session.path || session.label), 'ok');
        } else {
            setStatus(id('sshStatus'), session.label + (session.connected ? '' : ' 연결 중'), session.connected ? 'ok' : 'run');
        }
        updateAgentActionState();
    }

    function updateAgentActionState() {
        id('resetAgent').disabled = !(agent.session || agent.historySession);
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
            unregisterTerminalShortcuts(session.term);
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

    function setAgentPrimaryButtonsDisabled(disabled) {
        id('loginAgentAuth').disabled = disabled;
        id('startAgent').disabled = disabled;
    }

    function agentCommandForKind(kind) {
        return kind === 'claude' ? 'claude' : 'codex';
    }

    function agentLoginCommandForKind(kind) {
        return kind === 'claude' ? 'claude auth login' : 'codex login --device-auth';
    }

    function buildRemoteAgentLoginCommand(kind) {
        var command = agentCommandForKind(kind);
        var loginCommand = agentLoginCommandForKind(kind);
        return 'WEBTERM_AI_CMD=' + shellQuote(command)
            + '; WEBTERM_AI_LOGIN=' + shellQuote(loginCommand)
            + '; printf "\\r\\n[WebTerm] Checking remote AI command: %s\\r\\n" "$WEBTERM_AI_CMD"'
            + '; if ! command -v "$WEBTERM_AI_CMD" >/dev/null 2>&1; then '
            + 'printf "\\r\\n[WebTerm] ERROR: command not found: %s\\r\\n" "$WEBTERM_AI_CMD"'
            + '; else '
            + 'printf "\\r\\n[WebTerm] Login command started. Follow the URL/code shown below.\\r\\n"'
            + '; $WEBTERM_AI_LOGIN'
            + '; printf "\\r\\n[WebTerm] Login command finished. You can press 시작.\\r\\n"'
            + '; fi';
    }

    function buildRemoteAgentCommand(kind) {
        var command = agentCommandForKind(kind);
        return 'WEBTERM_AI_CMD=' + shellQuote(command)
            + '; printf "\\r\\n[WebTerm] Checking remote AI command: %s\\r\\n" "$WEBTERM_AI_CMD"'
            + '; if ! command -v "$WEBTERM_AI_CMD" >/dev/null 2>&1; then '
            + 'printf "\\r\\n[WebTerm] ERROR: command not found: %s\\r\\n" "$WEBTERM_AI_CMD"'
            + '; else '
            + 'printf "\\r\\n[WebTerm] Starting ' + command + ' ...\\r\\n"'
            + '; "$WEBTERM_AI_CMD"'
            + '; fi';
    }

    function updateAgentStatusFromOutput(session, output) {
        session.agentOutputBuffer = ((session.agentOutputBuffer || '') + output).slice(-4096);
        var text = session.agentOutputBuffer;
        if (text.indexOf('[WebTerm] Login command started.') >= 0) {
            setStatus(id('agentStatus'), '원격 로그인 진행 중: 터미널의 URL/코드를 브라우저에서 완료하세요.', 'run');
            setAgentPrimaryButtonsDisabled(false);
        } else if (text.indexOf('[WebTerm] Starting ' + session.agentCommand) >= 0) {
            setStatus(id('agentStatus'), '원격 실행 중: ' + session.agentCommand, 'ok');
        } else if (text.indexOf('[WebTerm] ERROR:') >= 0) {
            setStatus(id('agentStatus'), '원격 실행 실패: 터미널 메시지를 확인하세요.', 'err');
            setAgentPrimaryButtonsDisabled(false);
            updateAgentActionState();
        }
    }

    function runAgentRemote(mode) {
        var launch = mode === 'start';
        var login = mode === 'login';
        var current = activeSession();
        var base = current && current.kind === 'shell' ? current : firstShellSession();
        if (!base || !base.connected) {
            setStatus(id('agentStatus'), '먼저 SSH 서버에 연결하세요.', 'err');
            updateAgentActionState();
            return;
        }
        if (agent.session) {
            closeSession(agent.session.id, true);
            agent.session = null;
        }
        if (agent.historySession) {
            try { agent.historySession.term.dispose(); } catch (e) {}
            unregisterTerminalShortcuts(agent.historySession.term);
            agent.historySession = null;
        }

        var kind = selectedAgent();
        var command = agentCommandForKind(kind);
        id('agentPane').classList.remove('agent-history');
        id('agentForm').style.display = 'none';
        id('agentTerm').style.display = '';
        id('agentTerm').innerHTML = '';
        setStatus(id('agentStatus'), (login ? '원격 SSH에서 로그인 시작 중: ' : '원격 SSH에서 시작 중: ') + labelOf(base.info), 'run');
        setAgentPrimaryButtonsDisabled(true);

        var info = {
            host: base.info.host,
            port: base.info.port,
            username: base.info.username,
            password: base.info.password || '',
            privateKey: base.info.privateKey || '',
            privateKeyPassphrase: base.info.privateKeyPassphrase || ''
        };
        var session = connectSession(info, {
            kind: 'agent',
            detached: true,
            container: id('agentTerm'),
            initialCommand: login ? buildRemoteAgentLoginCommand(kind) : buildRemoteAgentCommand(kind),
            agentLaunch: launch,
            agentCommand: command,
            label: 'Remote ' + (kind === 'claude' ? 'Claude Code' : 'Codex') + ' - ' + labelOf(base.info),
            onConnected: function () {
                agent.running = launch;
                id('resetAgent').disabled = false;
                updateAgentActionState();
                setStatus(id('agentStatus'), login
                    ? '원격 로그인 진행 중: ' + command + ' @ ' + labelOf(base.info)
                    : '원격 실행 중: ' + command + ' @ ' + labelOf(base.info), 'run');
            }
        });
        agent.session = session;
        if (session) {
            session.panel.style.display = '';
            window.setTimeout(function () {
                resizeSession(session);
                session.term.focus();
            }, 0);
        }
    }

    function startAgent() {
        runAgentRemote('start');
    }

    function loginAgentAuth() {
        runAgentRemote('login');
    }

    function preserveAgentTerminal(session, message) {
        if (!session || session.preservedTerminal) return;
        session.preservedTerminal = true;
        session.connected = false;
        session.closing = true;
        if (session.socket) {
            try { session.socket.close(); } catch (e) {}
        }
        delete ssh.sessions[session.id];
        if (agent.session && agent.session.id === session.id) {
            agent.session = null;
        }
        agent.historySession = session;
        agent.running = false;
        id('agentPane').classList.add('agent-history');
        id('agentForm').style.display = 'grid';
        id('agentTerm').style.display = '';
        id('resetAgent').disabled = false;
        setAgentPrimaryButtonsDisabled(false);
        updateAgentActionState();
        setStatus(id('agentStatus'), message || '원격 AI CLI가 종료되었습니다.', 'ok');
    }

    function cleanupAgentUi() {
        id('resetAgent').disabled = true;
        setAgentPrimaryButtonsDisabled(false);
        updateAgentActionState();
        id('agentTerm').style.display = 'none';
        id('agentTerm').innerHTML = '';
        id('agentForm').style.display = 'grid';
        id('agentPane').classList.remove('agent-history');
    }

    function resetAgent() {
        setStatus(id('agentStatus'), '터미널 초기화 중...', 'run');
        id('resetAgent').disabled = true;

        if (agent.session) {
            closeSession(agent.session.id, true);
            agent.session = null;
        }
        if (agent.historySession) {
            if (agent.historySession.socket) {
                try { send(agent.historySession.socket, { type: 'disconnect' }); } catch (e) {}
                try { agent.historySession.socket.close(); } catch (e) {}
            }
            if (agent.historySession.term) {
                try { agent.historySession.term.dispose(); } catch (e) {}
                unregisterTerminalShortcuts(agent.historySession.term);
            }
            agent.historySession = null;
        }
        agent.running = false;
        cleanupAgentUi();
        setStatus(id('agentStatus'), '초기화됨', 'ok');
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

    var workspaceInitialized = false;
    var workspaceLoginBusy = false;

    function showWorkspace() {
        document.body.classList.remove('workspace-auth-pending', 'workspace-auth-failed');
        var app = document.querySelector('.workspace-app');
        if (app) app.setAttribute('aria-hidden', 'false');
        initWorkspace();
    }

    function showLoginFailure() {
        document.body.classList.remove('workspace-auth-pending');
        document.body.classList.add('workspace-auth-failed');
        id('workspaceLoginDialog').style.display = 'none';
        id('workspaceLoginFailed').style.display = 'grid';
    }

    function logoutWorkspace() {
        fetch(apiUrl('/logout.do'), { method: 'GET', cache: 'no-store' })
            .catch(function () {})
            .then(function () {
                window.location.href = apiUrl('/workspace.do');
            });
    }

    function loginWorkspace() {
        if (workspaceLoginBusy) return;
        var userId = id('workspaceLoginUserId').value.trim();
        var password = id('workspaceLoginPassword').value;
        if (!userId || !password) {
            setStatus(id('workspaceLoginStatus'), 'ID와 비밀번호를 입력하세요.', 'err');
            id(userId ? 'workspaceLoginPassword' : 'workspaceLoginUserId').focus();
            return;
        }

        workspaceLoginBusy = true;
        id('workspaceLoginConfirm').disabled = true;
        setStatus(id('workspaceLoginStatus'), '확인 중...', 'run');

        postForm('/login.do', { userId: userId, passwd: password })
            .then(function (data) {
                if (data.result === 'OK') {
                    id('workspaceLoginPassword').value = '';
                    showWorkspace();
                } else {
                    showLoginFailure();
                }
            })
            .catch(showLoginFailure)
            .then(function () {
                workspaceLoginBusy = false;
                id('workspaceLoginConfirm').disabled = false;
            });
    }

    function initWorkspace() {
        if (workspaceInitialized) return;
        workspaceInitialized = true;

        id('openSshDialog').addEventListener('click', function () { openSshDialog(); });
        id('openFileViewer').addEventListener('click', createFileViewer);
        id('cancelSshDialog').addEventListener('click', closeSshDialog);
        id('connectSsh').addEventListener('click', connectFromDialog);
        id('disconnectSsh').addEventListener('click', function () {
            var session = activeSession();
            if (session) closeSession(session.id, true);
        });
        id('openSavedServer').addEventListener('click', function () {
            var server = selectedSavedServer();
            if (!server) return;
            openSshDialog(server);
            setStatus(id('sshConnectStatus'), 'PW를 입력한 뒤 연결하세요.', 'run');
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
        id('logoutWorkspace').addEventListener('click', logoutWorkspace);
        id('cancelSettings').addEventListener('click', closeSettingsDialog);
        id('saveSettings').addEventListener('click', saveSettings);
        id('addQuickCommand').addEventListener('click', addQuickCommand);
        id('quickCommandInput').addEventListener('keydown', function (event) {
            if (event.key === 'Enter') addQuickCommand();
        });
        id('startAgent').addEventListener('click', startAgent);
        id('loginAgentAuth').addEventListener('click', loginAgentAuth);
        id('resetAgent').addEventListener('click', resetAgent);

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
        updateAgentActionState();
    }

    id('workspaceLoginConfirm').addEventListener('click', loginWorkspace);
    ['workspaceLoginUserId', 'workspaceLoginPassword'].forEach(function (name) {
        id(name).addEventListener('keydown', function (event) {
            if (event.key === 'Enter') loginWorkspace();
        });
    });
    id('workspaceLoginUserId').addEventListener('focus', function () {
        id('workspaceLoginUserId').select();
    });
    id('workspaceLoginPassword').addEventListener('focus', function () {
        id('workspaceLoginPassword').select();
    });
    id('workspaceLoginPassword').focus();
})();
