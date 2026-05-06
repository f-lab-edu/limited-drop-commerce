package com.mist.commerce.domain.user.entity;

import com.mist.commerce.domain.company.entity.Company;
import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "field")
    private UserType userType;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private String name;

    private String password;

    @Column(unique = true)
    private String email;

    private String phone;

    private String address;

    private String birthDate;

    public static User createPersonal(String email, String name) {
        User user = new User();
        user.userType = UserType.USER;
        user.status = UserStatus.ACTIVE;
        user.email = email;
        user.name = name;
        return user;
    }

    public void activate() {
        if (this.status == UserStatus.ACTIVE) {
            throw new IllegalStateException("Already active user");
        }
        if (this.status == UserStatus.DELETED) {
            throw new IllegalStateException("Deleted user cannot be activated");
        }
        this.status = UserStatus.ACTIVE;
    }

    public void suspend() {
        if (this.status == UserStatus.SUSPENDED) {
            throw new IllegalStateException("Already suspended user");
        }
        if (this.status == UserStatus.DELETED) {
            throw new IllegalStateException("Deleted user cannot be suspended");
        }
        this.status = UserStatus.SUSPENDED;
    }

    public void delete() {
        if (this.status == UserStatus.DELETED) {
            throw new IllegalStateException("Already deleted user");
        }
        this.status = UserStatus.DELETED;
    }
}
