package com.mist.commerce.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

    @Test
    @DisplayName("일반 회원을 생성하면 USER 유형과 ACTIVE 상태를 가진다")
    void createPersonal() {
        User user = User.createPersonal("user@example.com", "홍길동");

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getUserType()).isEqualTo(UserType.USER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("ACTIVE 상태인 회원을 정지한다")
    void suspendActiveUser() {
        User user = User.createPersonal("user@example.com", "홍길동");

        user.suspend();

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("ACTIVE 상태인 회원을 삭제한다")
    void deleteActiveUser() {
        User user = User.createPersonal("user@example.com", "홍길동");

        user.delete();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
    }

    @Test
    @DisplayName("이메일 인증 필요 상태인 회원을 활성화한다")
    void activateEmailVerificationRequiredUser() {
        User user = new User();
        ReflectionTestUtils.setField(user, "status", UserStatus.EMAIL_VERIFICATION_REQUIRED);

        user.activate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("일반 회원은 기업에 연결되지 않는다")
    void createPersonal_companyIsNull() {
        User user = User.createPersonal("user@example.com", "홍길동");

        assertThat(user.getCompany()).isNull();
    }

    @Test
    @DisplayName("삭제된 회원은 다시 활성화할 수 없다")
    void cannotActivateDeletedUser() {
        User user = User.createPersonal("user@example.com", "홍길동");
        user.delete();

        assertThatThrownBy(user::activate)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("소셜 전용 계정은 password가 null이다")
    void createPersonal_passwordIsNull() {
        User user = User.createPersonal("user@example.com", "홍길동");

        assertThat(user.getPassword()).isNull();
    }

    @Test
    @DisplayName("이미 정지된 회원을 다시 정지할 수 없다")
    void cannotSuspendAlreadySuspendedUser() {
        User user = User.createPersonal("user@example.com", "홍길동");
        user.suspend();

        assertThatThrownBy(user::suspend)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 삭제된 회원을 다시 삭제할 수 없다")
    void cannotDeleteAlreadyDeletedUser() {
        User user = User.createPersonal("user@example.com", "홍길동");
        user.delete();

        assertThatThrownBy(user::delete)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 활성 상태인 회원을 다시 활성화할 수 없다")
    void cannotActivateAlreadyActiveUser() {
        User user = User.createPersonal("user@example.com", "홍길동");

        assertThatThrownBy(user::activate)
                .isInstanceOf(IllegalStateException.class);
    }
}
