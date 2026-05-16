package com.mist.commerce.global.filter;

import com.mist.commerce.domain.user.entity.UserType;
import com.mist.commerce.domain.user.exception.InvalidTokenException;
import com.mist.commerce.domain.user.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return servletPath.startsWith("/login") || servletPath.startsWith("/oauth2");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token != null && tokenService.validateToken(token)) {
            try {
                Long userId = tokenService.getUserIdFromToken(token);
                String userType = tokenService.getUserTypeFromToken(token);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority(toAuthority(userType)))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException ignored) {
                // Invalid tokens are ignored here; exception handling is delegated to protected endpoints.
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }

        String[] parts = authorizationHeader.trim().split("\\s+", 2);
        if (parts.length < 2 || !parts[0].equalsIgnoreCase("Bearer") || parts[1].isBlank()) {
            return null;
        }

        return parts[1].trim();
    }

    private String toAuthority(String userType) {
        if (userType == null || userType.isBlank()) {
            return "ROLE_USER";
        }
        try {
            return "ROLE_" + UserType.valueOf(userType);
        } catch (IllegalArgumentException e) {
            return "ROLE_USER";
        }
    }
}
