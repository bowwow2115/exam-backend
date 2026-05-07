package com.psh.exam.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Vue SPA shell for client-side routes (history mode).
 * API paths under {@code /api/**} stay on REST controllers.
 */
@Controller
public class SpaController {

    @GetMapping({
            "/account/**",
            "/exams/**",
            "/attempts/**",
            "/wrong-notes/**"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
