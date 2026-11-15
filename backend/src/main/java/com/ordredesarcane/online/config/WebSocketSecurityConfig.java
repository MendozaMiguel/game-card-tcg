package com.ordredesarcane.online.config;

import com.ordredesarcane.online.security.JwtService;
import com.ordredesarcane.online.security.PlayerPrincipal;
import com.ordredesarcane.online.security.RateLimiterService;
import jakarta.servlet.http.Cookie;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {

    private static final String ATTR_PRINCIPAL = "ARCANE_PRINCIPAL";

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;
    private final RateLimiterService rateLimiterService;

    public WebSocketSecurityConfig(
            JwtService jwtService,
            JwtProperties jwtProperties,
            CorsProperties corsProperties,
            RateLimiterService rateLimiterService) {
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(corsProperties.allowedOrigins().toArray(String[]::new))
                .addInterceptors(jwtHandshakeInterceptor())
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(
                            ServerHttpRequest request,
                            WebSocketHandler wsHandler,
                            Map<String, Object> attributes) {
                        Object principal = attributes.get(ATTR_PRINCIPAL);
                        return principal instanceof Principal p ? p : null;
                    }
                });
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                StompCommand command = accessor.getCommand();
                if (command == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(command)
                        || StompCommand.SEND.equals(command)
                        || StompCommand.SUBSCRIBE.equals(command)) {
                    if (!(accessor.getUser() instanceof PlayerPrincipal principal)) {
                        throw new MessageDeliveryException(message, "Session WebSocket non authentifiée.");
                    }
                    if (StompCommand.SEND.equals(command)) {
                        enforceRateLimit(message, accessor.getDestination(), principal);
                    }
                }
                return message;
            }
        });
    }

    private void enforceRateLimit(Message<?> message, String destination, PlayerPrincipal principal) {
        if (destination == null) {
            return;
        }
        String playerId = principal.getName();
        if (destination.startsWith("/app/game.") && !rateLimiterService.allowGameAction(playerId)) {
            throw new MessageDeliveryException(
                    message, "Limite de fréquence dépassée (max 5 actions/seconde).");
        }
        // /app/chat* rate limiting (1 msg/s) is enforced once in ChatController so @MessageExceptionHandler
        // returns a GameErrorEvent on /user/queue/errors instead of closing the STOMP WebSocket session.
    }

    private HandshakeInterceptor jwtHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler wsHandler,
                    Map<String, Object> attributes) {
                Optional<PlayerPrincipal> principal = extractSessionToken(request)
                        .flatMap(jwtService::verifyToken);
                if (principal.isEmpty()) {
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                }
                attributes.put(ATTR_PRINCIPAL, principal.get());
                return true;
            }

            @Override
            public void afterHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler wsHandler,
                    Exception exception) {
                // Rien à nettoyer après l'ouverture du tunnel WebSocket
            }
        };
    }

    private Optional<String> extractSessionToken(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return Optional.empty();
        }
        Cookie[] cookies = servletRequest.getServletRequest().getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (jwtProperties.cookieName().equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
