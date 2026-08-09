package com.example.restaurant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "hoan_tien_giao_hang",
        uniqueConstraints = @UniqueConstraint(name = "uk_hoan_tien_giao_hang_ma_giao_dich", columnNames = "ma_giao_dich")
)
public class DeliveryRefund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_hoan_tien")
    private Long maHoanTien;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ma_giao_hang", nullable = false)
    private OrderDelivery giaoHang;

    @Column(name = "so_tien", precision = 12, scale = 2, nullable = false)
    private BigDecimal soTien;

    @Column(name = "ma_giao_dich", length = 100, nullable = false, unique = true)
    private String maGiaoDich;

    @Column(name = "ghi_chu", length = 500)
    private String ghiChu;

    @Column(name = "thoi_gian_hoan", nullable = false)
    private LocalDateTime thoiGianHoan = LocalDateTime.now();

    @PrePersist
    void prePersist() {
        if (thoiGianHoan == null) {
            thoiGianHoan = LocalDateTime.now();
        }
        if (soTien == null) {
            soTien = BigDecimal.ZERO;
        }
    }
}
