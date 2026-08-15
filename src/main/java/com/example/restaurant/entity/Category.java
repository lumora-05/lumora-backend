package com.example.restaurant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "danh_muc_mon")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_danh_muc")
    private Integer maDanhMuc;

    @Column(name = "ten_danh_muc", length = 100, nullable = false)
    private String tenDanhMuc;

    @Column(name = "ten_danh_muc_en", length = 150)
    private String tenDanhMucEn;

    @Column(name = "mo_ta", length = 255)
    private String moTa;

    @Column(name = "mo_ta_en", length = 500)
    private String moTaEn;

    @Column(name = "trang_thai", nullable = false)
    private Boolean trangThai = true;
}
