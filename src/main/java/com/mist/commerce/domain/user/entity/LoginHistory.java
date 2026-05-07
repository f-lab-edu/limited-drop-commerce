package com.mist.commerce.domain.user.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "login_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    @Enumerated(EnumType.STRING)
    private LoginType loginType;

    private String ipAddress;

    private String userAgent;

    private String successYn;

    private String failureReason;

    public static LoginHistory success(Long memberId, LoginType loginType, String ipAddress, String userAgent) {
        LoginHistory loginHistory = new LoginHistory();
        loginHistory.memberId = memberId;
        loginHistory.loginType = loginType;
        loginHistory.ipAddress = ipAddress;
        loginHistory.userAgent = userAgent;
        loginHistory.successYn = "Y";
        return loginHistory;
    }

    public static LoginHistory failure(
            Long memberId,
            LoginType loginType,
            String ipAddress,
            String userAgent,
            String failureReason
    ) {
        LoginHistory loginHistory = new LoginHistory();
        loginHistory.memberId = memberId;
        loginHistory.loginType = loginType;
        loginHistory.ipAddress = ipAddress;
        loginHistory.userAgent = userAgent;
        loginHistory.successYn = "N";
        loginHistory.failureReason = failureReason;
        return loginHistory;
    }
}
