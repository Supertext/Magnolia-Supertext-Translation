package com.supertext.magnolia.translation.reporting;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import info.magnolia.objectfactory.Components;

import com.supertext.magnolia.translation.internal.sdk.v1.PingResponse;

/**
 * {@code GET /.supertext/ping?challenge=<c>}: the public callback the Supertext Commando Center calls to see whether
 * this installation is reachable. No login, session or CSRF token (the setup task adds bypasses for the URI security
 * and admin setup filters, and the cache filter if there is one); {@code Cache-Control: no-store}. The answer is the
 * SDK's {@link PingResponse}: the challenge and the plugin name, nothing else; HTTP 404 while version reports are off.
 * Only GET (HttpServlet answers other methods with 405).
 */
public class PingServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PingResponse ping;
        try {
            ping = Components.getComponent(ReportingService.class).ping(request.getParameter("challenge"));
        } catch (RuntimeException e) {
            ping = VersionReports.ping(request.getParameter("challenge"), true);
        }
        write(ping, response);
    }

    static void write(PingResponse ping, HttpServletResponse response) throws IOException {
        response.setStatus(ping.getStatus());
        for (Map.Entry<String, String> header : ping.getHeaders().entrySet()) {
            response.setHeader(header.getKey(), header.getValue());
        }
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(ping.getBody());
    }
}
