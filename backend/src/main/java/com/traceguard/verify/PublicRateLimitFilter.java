package com.traceguard.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window limiter for the unauthenticated /api/public endpoints, keyed by client address.
 * In-memory, so each server instance counts separately; enough for the demo deployment.
 */
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private record Window(long start, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;

    public PublicRateLimitFilter(@Value("${traceguard.public.rate-limit-per-minute:60}") int limit) {
        this.limit = limit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > 10_000) windows.values().removeIf(w -> now - w.start() >= WINDOW_MS);

        Window w = windows.compute(request.getRemoteAddr(),
                (k, old) -> old == null || now - old.start() >= WINDOW_MS ? new Window(now, 1) : new Window(old.start(), old.count() + 1));

        if (w.count() > limit) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Too many verification requests; try again in a minute\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
