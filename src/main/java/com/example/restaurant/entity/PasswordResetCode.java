package com.example.restaurant.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ma_xac_nhan_dat_lai_mat_khau",
        indexes = {
                @Index(name = "idx_reset_code_employee", columnList = "ma_nhan_vien"),
                @Index(name = "idx_reset_code_token", columnList = "reset_token_hash", unique = true)
        }
)
public class PasswordResetCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_xac_nhan")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ma_nhan_vien", nullable = false)
    private Employee employee;

    @Column(name = "otp_hash", length = 255, nullable = false)
    private String otpHash;

    @Column(name = "otp_het_han_luc", nullable = false)
    private LocalDateTime otpExpiresAt;

    @Column(name = "so_lan_nhap_sai", nullable = false)
    private int failedAttempts;

    @Column(name = "xac_minh_luc")
    private LocalDateTime verifiedAt;

    @Column(name = "reset_token_hash", length = 64, unique = true)
    private String resetTokenHash;

    @Column(name = "reset_token_het_han_luc")
    private LocalDateTime resetTokenExpiresAt;

    @Column(name = "da_su_dung_luc")
    private LocalDateTime usedAt;

    @Column(name = "gui_luc", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "ngay_tao", nullable = false)
    private LocalDateTime createdAt;

    public PasswordResetCode() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public void setOtpHash(String otpHash) {
        this.otpHash = otpHash;
    }

    public LocalDateTime getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public void setOtpExpiresAt(LocalDateTime otpExpiresAt) {
        this.otpExpiresAt = otpExpiresAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getResetTokenHash() {
        return resetTokenHash;
    }

    public void setResetTokenHash(String resetTokenHash) {
        this.resetTokenHash = resetTokenHash;
    }

    public LocalDateTime getResetTokenExpiresAt() {
        return resetTokenExpiresAt;
    }

    public void setResetTokenExpiresAt(LocalDateTime resetTokenExpiresAt) {
        this.resetTokenExpiresAt = resetTokenExpiresAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
