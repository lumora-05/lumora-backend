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
        name = "giao_dich_diem",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_giao_dich_diem_don_loai",
                columnNames = {"ma_don_hang", "loai_giao_dich"}
        )
)
public class LoyaltyTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_giao_dich_diem")
    private Long maGiaoDichDiem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ma_khach_hang", nullable = false)
    private Customer khachHang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_don_hang")
    private Order donHang;

    @Column(name = "loai_giao_dich", length = 20, nullable = false)
    private String loaiGiaoDich;

    /** Số dương là cộng điểm, số âm là trừ điểm. */
    @Column(name = "so_diem", nullable = false)
    private Integer soDiem;

    @Column(name = "so_du_sau_giao_dich", nullable = false)
    private Integer soDuSauGiaoDich;

    @Column(name = "noi_dung", length = 255, nullable = false)
    private String noiDung;

    @Column(name = "thoi_gian", nullable = false)
    private LocalDateTime thoiGian = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (thoiGian == null) {
            thoiGian = LocalDateTime.now();
        }
    }
}
