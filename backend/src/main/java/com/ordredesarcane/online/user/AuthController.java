package com.ordredesarcane.online.user;

import com.ordredesarcane.online.security.JwtService;
import com.ordredesarcane.online.security.PlayerPrincipal;
import com.ordredesarcane.online.user.AuthService.AuthResult;
import com.ordredesarcane.online.user.dto.LoginRequest;
import com.ordredesarcane.online.user.dto.RegisterRequest;
import com.ordredesarcane.online.user.dto.UserProfileDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ProfileService profileService;
    private final JwtService jwtService;

    public AuthController(
            AuthService authService,
            ProfileService profileService,
            JwtService jwtService) {
        this.authService = authService;
        this.profileService = profileService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserProfileDto> register(@Valid @RequestBody RegisterRequest request) {
        AuthResult result = authService.register(request);
        ResponseCookie cookie = jwtService.createSessionCookie(result.jwtToken());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.profile());
    }

    @PostMapping("/login")
    public ResponseEntity<UserProfileDto> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request);
        ResponseCookie cookie = jwtService.createSessionCookie(result.jwtToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.profile());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cleared = jwtService.clearSessionCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    @GetMapping("/me")
    public UserProfileDto me(@AuthenticationPrincipal PlayerPrincipal principal) {
        return profileService.getProfile(principal.userId());
    }
}
