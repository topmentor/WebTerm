<%@page contentType="application/json;charset=UTF-8" pageEncoding="UTF-8"%><%@page import="com.ithows.PageBean"%><%@page import="org.json.JSONObject"%><%
    PageBean pageBean = (PageBean) request.getAttribute("pageBean");
    Throwable error = (Throwable) request.getAttribute("controllerException");
    String pageId = pageBean == null || pageBean.getId() == null ? "" : pageBean.getId();
    String message = error == null || error.getMessage() == null
            ? "API controller error"
            : error.getMessage();

    JSONObject json = new JSONObject();
    json.put("result", "ERROR");
    json.put("msg", message);
    json.put("pageId", pageId);
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    out.print(json.toString());
%>
