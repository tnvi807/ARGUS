package com.argus.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                        HttpServletRequest http) {
        return authService.login(request, http);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        authService.logout(http);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthDtos.UserView me() {
        return authService.me();
    }

    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
