package com.example.restaurant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "nhan_vien_push_device",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_push_device_fid_channel",
                columnNames = {"firebase_installation_id", "kenh"}
        )
)
public class PushDeviceRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_push_device")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ma_nhan_vien", nullable = false)
    private Employee employee;

    @Column(name = "firebase_installation_id", nullable = false, length = 512)
    private String firebaseInstallationId;

    /** KITCHEN hoặc WAITER. ADMIN có thể đăng ký một trong hai kênh khi kiểm thử. */
    @Column(name = "kenh", nullable = false, length = 30)
    private String channel;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "hoat_dong", nullable = false)
    private boolean active = true;

    @Column(name = "thoi_gian_tao", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "thoi_gian_cap_nhat", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
