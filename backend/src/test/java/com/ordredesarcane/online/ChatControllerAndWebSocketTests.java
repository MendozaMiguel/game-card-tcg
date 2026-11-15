package com.ordredesarcane.online;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ordredesarcane.online.config.CorsProperties;
import com.ordredesarcane.online.config.JwtProperties;
import com.ordredesarcane.online.config.WebSocketSecurityConfig;
import com.ordredesarcane.online.game.dto.GameErrorEvent;
import com.ordredesarcane.online.game.engine.GameEngineService;
import com.ordredesarcane.online.lobby.ChatController;
import com.ordredesarcane.online.lobby.dto.ChatMessageCommand;
import com.ordredesarcane.online.lobby.dto.ChatMessageDto;
import com.ordredesarcane.online.security.JwtService;
import com.ordredesarcane.online.security.PlayerPrincipal;
import com.ordredesarcane.online.security.RateLimiterService;
import com.ordredesarcane.online.security.XssSanitizer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

class ChatControllerAndWebSocketTests {

    private static final String TEST_SECRET =
            "test-only-64-byte-arcane-hs512-secret-key-with-enough-entropy-123456";

    private AtomicReference<Instant> now;
    private RateLimiterService rateLimiterService;
    private SimpMessagingTemplate messagingTemplate;
    private ChatController chatController;
    private ChannelInterceptor inboundInterceptor;

    @BeforeEach
    void setUp() {
        now = new AtomicReference<>(Instant.parse("2026-11-15T19:00:00Z"));
        Clock mutableClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };

        rateLimiterService = new RateLimiterService(mutableClock);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        chatController = new ChatController(
                messagingTemplate,
                new XssSanitizer(),
                rateLimiterService,
                mock(GameEngineService.class));

        var jwtProps = new JwtProperties(TEST_SECRET, 3600, "ARCANE_SESSION", false);
        var wsConfig = new WebSocketSecurityConfig(
                new JwtService(jwtProps, mutableClock),
                jwtProps,
                new CorsProperties(List.of("http://localhost")),
                rateLimiterService);

        List<ChannelInterceptor> captured = new ArrayList<>();
        ChannelRegistration registration = new ChannelRegistration() {
            @Override
            public ChannelRegistration interceptors(ChannelInterceptor... interceptors) {
                captured.addAll(List.of(interceptors));
                return super.interceptors(interceptors);
            }
        };
        wsConfig.configureClientInboundChannel(registration);
        inboundInterceptor = captured.getFirst();
    }

    @Test
    void globalChatDoesNotDoubleConsumeRateLimitAndBroadcastsSanitizedMessage() {
        var user = new PlayerPrincipal(UUID.randomUUID(), "Archimage", "USER");
        Message<byte[]> stompSend = buildStompSend("/app/chat.global", user);

        // First message passes inbound interceptor AND ChatController without double rate-limit rejection
        inboundInterceptor.preSend(stompSend, mock(MessageChannel.class));
        chatController.sendGlobalChat(
                new ChatMessageCommand(null, false, "  Salut <script>alert(1)</script>  "),
                user);

        ArgumentCaptor<ChatMessageDto> dtoCaptor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/chat.global"), dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().senderUsername()).isEqualTo("Archimage");
        assertThat(dtoCaptor.getValue().content()).contains("&lt;script&gt;").doesNotContain("<script>");

        // Second message within 1s passes inbound interceptor (keeps WS session alive)
        // and is cleanly converted to a GameErrorEvent by ChatController's @MessageExceptionHandler
        inboundInterceptor.preSend(stompSend, mock(MessageChannel.class));
        assertThatThrownBy(() -> chatController.sendGlobalChat(
                        new ChatMessageCommand(null, false, "Trop vite"), user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limite chat dépassée");

        GameErrorEvent errEvent = chatController.handleChatException(
                new IllegalArgumentException("Limite chat dépassée (max 1 message/seconde)."));
        assertThat(errEvent.code()).isEqualTo("CHAT_ERROR");
        assertThat(errEvent.message()).contains("Limite chat dépassée");

        // After 1 second window elapses, next message succeeds again
        now.set(now.get().plusMillis(1001));
        inboundInterceptor.preSend(stompSend, mock(MessageChannel.class));
        chatController.sendGlobalChat(new ChatMessageCommand(null, false, "Message suivant"), user);
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/chat.global"), dtoCaptor.capture());
    }

    @Test
    void unauthenticatedSendStillRejectedAndGameActionRateLimitRemainsActive() {
        Message<byte[]> unauthSend = buildStompSend("/app/chat.global", null);
        assertThatThrownBy(() -> inboundInterceptor.preSend(unauthSend, mock(MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);

        var user = new PlayerPrincipal(UUID.randomUUID(), "Duelist", "USER");
        Message<byte[]> gameSend = buildStompSend("/app/game.action", user);
        for (int i = 0; i < 5; i++) {
            inboundInterceptor.preSend(gameSend, mock(MessageChannel.class));
        }
        assertThatThrownBy(() -> inboundInterceptor.preSend(gameSend, mock(MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private static Message<byte[]> buildStompSend(String destination, PlayerPrincipal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
