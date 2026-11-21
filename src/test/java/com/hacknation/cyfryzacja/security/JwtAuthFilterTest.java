package com.hacknation.cyfryzacja.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthFilterTest {

    private static final String SECRET = "filter-test-secret-filter-test-secret-12";
    private final JwtAuthFilter filter = new JwtAuthFilter(
            new JwtTokenVerifier(new JwtProperties(SECRET, "formforge", 3600)));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/upload");
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest(), "żądanie musi iść dalej łańcuchem (decyzję 401 podejmuje Spring Security)");
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void validTokenAuthenticates() throws Exception {
        String token = Jwts.builder().issuer("formforge").subject("jury")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        Authentication auth = run("Bearer " + token);
        assertNotNull(auth);
        assertEquals("jury", auth.getPrincipal());
    }

    @Test
    void missingHeaderStaysAnonymous() throws Exception {
        assertNull(run(null));
    }

    @Test
    void invalidTokenStaysAnonymous() throws Exception {
        assertNull(run("Bearer abc.def.ghi"));
        assertNull(run("Basic dXNlcjpwYXNz"));
    }
}
