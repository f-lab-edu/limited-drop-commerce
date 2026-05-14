package com.mist.commerce.domain.user.dto;

public record AuthTokenResponse(String accessToken, String refreshToken, boolean isNewUser) {
}
