package com.example.restaurant.config;

import com.example.restaurant.entity.*;
import com.example.restaurant.repository.*;
import com.example.restaurant.service.QrCodeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner initData(RoleRepository roleRepository,
                               EmployeeRepository employeeRepository,
                               CategoryRepository categoryRepository,
                               FoodRepository foodRepository,
                               DiningTableRepository diningTableRepository,
                               PasswordEncoder passwordEncoder,
                               QrCodeService qrCodeService) {
        return args -> {
            Role adminRole = roleRepository.findByTenVaiTro("ADMIN")
                    .orElseGet(() -> roleRepository.save(new Role("ADMIN")));
            roleRepository.findByTenVaiTro("WAITER")
                    .orElseGet(() -> roleRepository.save(new Role("WAITER")));
            roleRepository.findByTenVaiTro("KITCHEN")
                    .orElseGet(() -> roleRepository.save(new Role("KITCHEN")));
            roleRepository.findByTenVaiTro("CASHIER")
                    .orElseGet(() -> roleRepository.save(new Role("CASHIER")));

            if (!employeeRepository.existsByTenDangNhap("admin")) {
                Employee admin = new Employee();
                admin.setHoTen("Quản trị viên");
                admin.setEmail("admin@restaurant.local");
                admin.setTenDangNhap("admin");
                admin.setMatKhau(passwordEncoder.encode("123456"));
                admin.setVaiTro(adminRole);
                admin.setTrangThai("DANG_LAM_VIEC");
                employeeRepository.save(admin);
            }

            if (categoryRepository.count() == 0) {
                Category monChinh = new Category();
                monChinh.setTenDanhMuc("Món chính");
                monChinh.setMoTa("Các món ăn chính trong thực đơn");
                categoryRepository.save(monChinh);

                Category nuocUong = new Category();
                nuocUong.setTenDanhMuc("Nước uống");
                nuocUong.setMoTa("Đồ uống giải khát");
                categoryRepository.save(nuocUong);

                Food pho = new Food();
                pho.setDanhMuc(monChinh);
                pho.setTenMonAn("Phở bò");
                pho.setGia(BigDecimal.valueOf(45000));
                pho.setMoTa("Phở bò truyền thống");
                foodRepository.save(pho);

                Food traDa = new Food();
                traDa.setDanhMuc(nuocUong);
                traDa.setTenMonAn("Trà đá");
                traDa.setGia(BigDecimal.valueOf(5000));
                traDa.setMoTa("Trà đá mát lạnh");
                foodRepository.save(traDa);
            }

            if (diningTableRepository.count() == 0) {
                for (int i = 1; i <= 5; i++) {
                    DiningTable table = new DiningTable();
                    table.setTenBan(String.format("Bàn %02d", i));
                    table.setTrangThai("TRONG");
                    table.setKhuVuc("Tầng 1 - Khu vực trong nhà");
                    table.setSucChua(i % 3 == 0 ? 6 : 4);
                    DiningTable savedTable = diningTableRepository.save(table);
                    initializeQr(savedTable, qrCodeService);
                    diningTableRepository.save(savedTable);
                }
            } else {
                // Nâng cấp dữ liệu cũ để khớp giao diện Bàn & QR mới.
                for (DiningTable table : diningTableRepository.findAllByOrderByMaBanAsc()) {
                    boolean changed = false;
                    boolean qrTokenCreated = false;

                    if (table.getQrToken() == null || table.getQrToken().isBlank()) {
                        table.setQrToken(generateQrToken());
                        changed = true;
                        qrTokenCreated = true;
                    }

                    if (table.getKhuVuc() == null || table.getKhuVuc().isBlank()) {
                        table.setKhuVuc("Khu vực chung");
                        changed = true;
                    }
                    if (table.getSucChua() == null || table.getSucChua() < 1) {
                        table.setSucChua(4);
                        changed = true;
                    }

                    if (table.getAnhQr() != null && !table.getAnhQr().isBlank()) {
                        boolean legacyQr = table.getMaQr() == null || !table.getMaQr().matches("QR\\d{4,}");
                        if (legacyQr) {
                            table.setMaQr(formatQrCode(table.getMaBan()));
                            changed = true;
                        }
                        boolean legacyStorage = !qrCodeService.isCloudinaryImage(table.getAnhQr());
                        if (legacyQr || qrTokenCreated || legacyStorage) {
                            table.setAnhQr(qrCodeService.generateTableQr(table));
                            changed = true;
                        }
                        if (table.getTrangThaiQr() == null || table.getTrangThaiQr().isBlank()
                                || "CHUA_TAO".equals(table.getTrangThaiQr())) {
                            table.setTrangThaiQr("DANG_HOAT_DONG");
                            changed = true;
                        }
                        if (table.getNgayTaoQr() == null) {
                            table.setNgayTaoQr(LocalDateTime.now());
                            changed = true;
                        }
                        if (table.getNgayCapNhatQr() == null) {
                            table.setNgayCapNhatQr(table.getNgayTaoQr());
                            changed = true;
                        }
                    } else if (table.getTrangThaiQr() == null || table.getTrangThaiQr().isBlank()) {
                        table.setTrangThaiQr("CHUA_TAO");
                        changed = true;
                    }

                    if (changed) {
                        diningTableRepository.save(table);
                    }
                }
            }
        };
    }

    private void initializeQr(DiningTable table, QrCodeService qrCodeService) {
        LocalDateTime now = LocalDateTime.now();
        table.setMaQr(formatQrCode(table.getMaBan()));
        if (table.getQrToken() == null || table.getQrToken().isBlank()) {
            table.setQrToken(generateQrToken());
        }
        table.setAnhQr(qrCodeService.generateTableQr(table));
        table.setTrangThaiQr("DANG_HOAT_DONG");
        table.setNgayTaoQr(now);
        table.setNgayCapNhatQr(now);
    }

    private static String formatQrCode(Integer tableId) {
        return "QR" + String.format("%04d", tableId);
    }

    private static String generateQrToken() {
        return UUID.randomUUID().toString();
    }
}
