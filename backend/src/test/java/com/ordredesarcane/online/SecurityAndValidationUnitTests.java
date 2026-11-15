package com.ordredesarcane.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ordredesarcane.online.config.JwtProperties;
import com.ordredesarcane.online.game.model.AiDifficulty;
import com.ordredesarcane.online.game.model.GameMode;
import com.ordredesarcane.online.lobby.dto.StartAiMatchRequest;
import com.ordredesarcane.online.security.JwtService;
import com.ordredesarcane.online.security.PlayerPrincipal;
import com.ordredesarcane.online.security.RateLimiterService;
import com.ordredesarcane.online.security.XssSanitizer;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

class SecurityAndValidationUnitTests {

    private static final String TEST_SECRET =
            "test-only-64-byte-arcane-hs512-secret-key-with-enough-entropy-123456";

    @Test
    void jwtServiceSignsAndVerifiesTokenAndBuildsStrictCookie() {
        var props = new JwtProperties(TEST_SECRET, 3600, "ARCANE_SESSION", true);
        var clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC);
        var jwtService = new JwtService(props, clock);
        var principal = new PlayerPrincipal(UUID.randomUUID(), "Archimage", "USER");

        String token = jwtService.issueToken(principal);
        assertThat(jwtService.verifyToken(token)).contains(principal);
        assertThat(jwtService.verifyToken(token + "tampered")).isEmpty();

        ResponseCookie cookie = jwtService.createSessionCookie(token);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }

    @Test
    void jwtServiceRejectsExpiredToken() {
        var props = new JwtProperties(TEST_SECRET, 60, "ARCANE_SESSION", false);
        var currentTime = new AtomicReference<>(Instant.parse("2026-11-15T00:00:00Z"));
        Clock mutableClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentTime.get();
            }
        };

        var jwtService = new JwtService(props, mutableClock);
        String token = jwtService.issueToken(new PlayerPrincipal(UUID.randomUUID(), "Mage", "USER"));

        currentTime.set(Instant.parse("2026-11-15T00:02:00Z"));
        assertThat(jwtService.verifyToken(token)).isEmpty();
    }

    @Test
    void jacksonStrictRejectsUnknownProperties() {
        JsonMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        String payload = """
                {"action":"END_TURN","unexpectedCheatField":999}
                """;
        assertThatThrownBy(() -> mapper.readValue(payload, SampleActionDto.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void jakartaValidationEnforcesDtoConstraints() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            var invalid = new SampleActionDto(null, "INVALID");
            assertThat(validator.validate(invalid))
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("action", "action", "posture");

            var valid = new SampleActionDto("PLAY_CARD", "ATTACK");
            assertThat(validator.validate(valid)).isEmpty();
        }
    }

    @Test
    void rateLimiterEnforcesGameAndChatLimits() {
        var now = new AtomicReference<>(Instant.parse("2026-11-15T00:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };

        var limiter = new RateLimiterService(clock);
        String playerId = "player-1";

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allowGameAction(playerId)).isTrue();
        }
        assertThat(limiter.allowGameAction(playerId)).isFalse();

        assertThat(limiter.allowChatMessage(playerId)).isTrue();
        assertThat(limiter.allowChatMessage(playerId)).isFalse();

        now.set(now.get().plusMillis(1001));
        assertThat(limiter.allowGameAction(playerId)).isTrue();
        assertThat(limiter.allowChatMessage(playerId)).isTrue();
    }

    @Test
    void xssSanitizerEscapesHtmlAndStripsControlCharacters() {
        var sanitizer = new XssSanitizer();
        String sanitized = sanitizer.sanitize("  <script>alert('xss')</script>\u0007 Bonjour!  ");
        assertThat(sanitized).doesNotContain("<script>").contains("&lt;script&gt;").endsWith("Bonjour!");
    }

    @Test
    void startAiMatchRequestValidatesDifficultyAndStrictJson() {
        JsonMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        UUID deckId = UUID.randomUUID();

        String legacyJson = """
                {"mode":"DUEL_1V1","deckId":"%s","botFaction":"FIRE"}
                """.formatted(deckId);
        StartAiMatchRequest legacyReq = mapper.readValue(legacyJson, StartAiMatchRequest.class);
        assertThat(legacyReq.mode()).isEqualTo(GameMode.DUEL_1V1);
        assertThat(AiDifficulty.resolveOrDefault(legacyReq.difficulty())).isEqualTo(AiDifficulty.MEDIUM);

        String hardJson = """
                {"mode":"TEAM_2V2","deckId":"%s","botFaction":null,"difficulty":"HARD"}
                """.formatted(deckId);
        StartAiMatchRequest hardReq = mapper.readValue(hardJson, StartAiMatchRequest.class);
        assertThat(hardReq.difficulty()).isEqualTo(AiDifficulty.HARD);

        String rogueJson = """
                {"mode":"DUEL_1V1","deckId":"%s","difficulty":"EASY","hackFlag":true}
                """.formatted(deckId);
        assertThatThrownBy(() -> mapper.readValue(rogueJson, StartAiMatchRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    record SampleActionDto(
            @NotNull @NotBlank String action,
            @Pattern(regexp = "ATTACK|DEFENSE") String posture
    ) {
    }
}
