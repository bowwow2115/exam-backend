package com.psh.exam.account;

import com.psh.exam.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 서비스 자체 계정입니다.
 * 현재 인증 방식은 HTTP Basic이지만, 비밀번호는 항상 해시만 저장합니다.
 */
@Entity
@Table(
        name = "accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_accounts_email", columnNames = "email")
)
public class Account extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    /**
     * BCrypt 결과를 저장합니다. 원문 비밀번호는 어떤 엔티티에도 보관하지 않습니다.
     */
    @Column(nullable = false, name = "password_hash", length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountRole role;

    protected Account() {
    }

    public Account(String email, String passwordHash, String displayName, AccountRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountRole getRole() {
        return role;
    }
}
