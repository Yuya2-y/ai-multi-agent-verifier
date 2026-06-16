package com.example.test2.controller;

import com.example.test2.service.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;

@Controller
public class AuthController {

    private static final String SESSION_USER_KEY = "loggedInUser";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage(Model model, HttpSession session) {
        if (session.getAttribute(SESSION_USER_KEY) != null) {
            return "redirect:/";
        }
        return "login";
    }

    @PostMapping("/login")
    public String loginSubmit(@RequestParam("username") String username,
                              @RequestParam("password") String password,
                              Model model,
                              HttpSession session) {
        if (authService.authenticate(username, password)) {
            session.setAttribute(SESSION_USER_KEY, username);
            return "redirect:/";
        }
        model.addAttribute("error", "ユーザー名またはパスワードが正しくありません。");
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    public static String getSessionUser(HttpSession session) {
        Object user = session.getAttribute(SESSION_USER_KEY);
        return user != null ? user.toString() : null;
    }
}
