package com.ithows.controller;

import com.ithows.base.ApiInfo;
import com.ithows.base.ControllerClassInfo;
import com.ithows.base.ControllerMethodInfo;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@ControllerClassInfo(controllerPage = "/_main.jsp")
public class TerminalController {

    @ControllerMethodInfo(id = "/terminal.do")
    @ApiInfo(
        summary = "웹 SSH 터미널",
        description = "브라우저에서 SSH 접속 정보를 입력하고 CLI 터미널을 사용합니다.",
        tag = "터미널",
        method = "GET"
    )
    public String terminal(HttpSession session, HttpServletRequest request,
                           HttpServletResponse response, Object command) {
        return "/terminal.jsp";
    }

    @ControllerMethodInfo(id = "/workspace.do")
    @ApiInfo(
        summary = "SSH/Codex 워크스페이스",
        description = "LSMonitor 워크스페이스 UI를 기반으로 SSH 셸과 Codex/Claude CLI PTY를 함께 사용합니다.",
        tag = "터미널",
        method = "GET"
    )
    public String workspace(HttpSession session, HttpServletRequest request,
                            HttpServletResponse response, Object command) {
        return "/workspace.jsp";
    }
}
