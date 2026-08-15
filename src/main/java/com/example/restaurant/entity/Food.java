package com.example.restaurant.entity;

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
@Table(name = "mon_an")
public class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_mon_an")
    private Integer maMonAn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ma_danh_muc", nullable = false)
    private Category danhMuc;

    @Column(name = "ten_mon_an", length = 100, nullable = false)
    private String tenMonAn;

    @Column(name = "ten_mon_an_en", length = 150)
    private String tenMonAnEn;

    @Column(name = "gia", precision = 12, scale = 2, nullable = false)
    private BigDecimal gia;

    @Column(name = "mo_ta", length = 255)
    private String moTa;

    @Column(name = "mo_ta_en", length = 500)
    private String moTaEn;

    @Column(name = "hinh_anh", length = 500)
    private String hinhAnh;

    @Column(name = "trang_thai", nullable = false)
    private Boolean trangThai = true;

    @Column(name = "ngay_tao", nullable = false)
    private LocalDateTime ngayTao = LocalDateTime.now();
}
