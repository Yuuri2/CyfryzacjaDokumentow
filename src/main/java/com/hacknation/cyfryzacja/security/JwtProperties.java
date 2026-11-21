package com.hacknation.cyfryzacja.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Konfiguracja JWT (app.jwt.*). Sekret jest wymagany i musi mieć min. 32 znaki (256 bitów, HS256) –
 * aplikacja nie wystartuje bez poprawnej wartości.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT_SECRET musi mieć co najmniej 32 znaki") String secret,
        @NotBlank String issuer,
        @Positive long ttlSeconds
) {}
