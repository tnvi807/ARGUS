package com.argus.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the single-page React app for any non-file route (/login, /agents/xyz, ...) so the
 * whole product lives on one origin: http://localhost:8080 (API + UI). Asset paths containing a
 * dot (/assets/*.js, /favicon.ico) fall through to the static resource handler, and unmatched
 * API/actuator paths return 404 instead of HTML.
 */
@Controller
public class SpaForwardController {

    private static final String[] SERVER_PREFIXES = {"/api", "/actuator", "/v3", "/swagger-ui"};

    @GetMapping(value = {
            "/{path:[^\\.]*}",
            "/{path:[^\\.]*}/{sub:[^\\.]*}",
            "/{path:[^\\.]*}/{sub:[^\\.]*}/{sub2:[^\\.]*}"})
    public String forward(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String prefix : SERVER_PREFIXES) {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        return "forward:/index.html";
    }
}
