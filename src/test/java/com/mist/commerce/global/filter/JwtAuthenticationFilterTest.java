package com.mist.commerce.global.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mist.commerce.domain.user.exception.InvalidTokenException;
import com.mist.commerce.domain.user.service.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    TokenService tokenService;

    @InjectMocks
    JwtAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ===== 6.1 shouldNotFilter 경로 =====

    @Test
    void shouldNotFilter_login_경로는_true() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/login");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_login_하위_경로_true() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/login/oauth2/code/google");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_oauth2_경로는_true() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/oauth2/authorization/google");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_일반_API_경로는_false() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/products");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    // ===== 6.2 doFilterInternal — 토큰 추출 및 인증 세팅 =====

    @Test
    void doFilterInternal_Authorization_헤더_없으면_SecurityContext_미설정_chain_진행() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(tokenService);
    }

    @Test
    void doFilterInternal_유효한_Bearer_토큰이면_SecurityContext에_인증_세팅() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        given(tokenService.validateToken("valid-token")).willReturn(true);
        given(tokenService.getUserIdFromToken("valid-token")).willReturn(1L);

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).isNotEmpty();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doFilterInternal_validateToken_false면_SecurityContext_미설정() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        given(tokenService.validateToken("invalid")).willReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verify(tokenService, never()).getUserIdFromToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doFilterInternal_Bearer_prefix_누락이면_SecurityContext_미설정() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "token-without-prefix");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(tokenService);
    }

    @Test
    void doFilterInternal_Bearer_뒤_토큰_비어있으면_SecurityContext_미설정() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(tokenService);
    }

    @Test
    void doFilterInternal_소문자_bearer_prefix도_허용() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "bearer xyz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        given(tokenService.validateToken("xyz")).willReturn(true);
        given(tokenService.getUserIdFromToken("xyz")).willReturn(2L);

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(2L);
    }

    @Test
    void doFilterInternal_validateToken_true이지만_getUserIdFromToken_예외면_SecurityContext_미설정() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer broken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        given(tokenService.validateToken("broken")).willReturn(true);
        willThrow(new InvalidTokenException()).given(tokenService).getUserIdFromToken("broken");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
