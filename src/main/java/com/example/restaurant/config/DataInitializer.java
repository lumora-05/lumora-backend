package com.example.restaurant.config;

import com.example.restaurant.entity.*;
import com.example.restaurant.repository.*;
import com.example.restaurant.service.OrderItemUnitUpgradeService;
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
                               QrCodeService qrCodeService,
                               OrderItemUnitUpgradeService orderItemUnitUpgradeService) {
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
                monChinh.setTenDanhMucEn("Main Courses");
                monChinh.setMoTa("Các món ăn chính trong thực đơn");
                monChinh.setMoTaEn("Main dishes on the menu");
                categoryRepository.save(monChinh);

                Category nuocUong = new Category();
                nuocUong.setTenDanhMuc("Nước uống");
                nuocUong.setTenDanhMucEn("Beverages");
                nuocUong.setMoTa("Đồ uống giải khát");
                nuocUong.setMoTaEn("Refreshing beverages");
                categoryRepository.save(nuocUong);

                Food pho = new Food();
                pho.setDanhMuc(monChinh);
                pho.setTenMonAn("Phở bò");
                pho.setTenMonAnEn("Beef Pho");
                pho.setGia(BigDecimal.valueOf(45000));
                pho.setMoTa("Phở bò truyền thống");
                pho.setMoTaEn("Traditional Vietnamese beef pho");
                foodRepository.save(pho);

                Food traDa = new Food();
                traDa.setDanhMuc(nuocUong);
                traDa.setTenMonAn("Trà đá");
                traDa.setTenMonAnEn("Iced Tea");
                traDa.setGia(BigDecimal.valueOf(5000));
                traDa.setMoTa("Trà đá mát lạnh");
                traDa.setMoTaEn("Refreshing iced tea");
                foodRepository.save(traDa);
            }

            backfillPublicMenuEnglish(foodRepository);

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

            // Dữ liệu đơn cũ đang chờ bếp cũng được chuyển sang từng suất riêng.
            orderItemUnitUpgradeService.splitLegacyWaitingItems();
        };
    }

    private void backfillPublicMenuEnglish(FoodRepository foodRepository) {
        for (Food food : foodRepository.findAll()) {
            boolean changed = false;
            String name = food.getTenMonAn();

            if (isBlank(food.getTenMonAnEn())) {
                String englishName = knownEnglishFoodName(name);
                if (englishName != null) {
                    food.setTenMonAnEn(englishName);
                    changed = true;
                }
            }

            if (isBlank(food.getMoTaEn())) {
                String englishDescription = knownEnglishFoodDescription(name, food.getMoTa());
                if (englishDescription != null) {
                    food.setMoTaEn(englishDescription);
                    changed = true;
                }
            }

            if (changed) {
                foodRepository.save(food);
            }
        }
    }

    private String knownEnglishFoodName(String vietnameseName) {
        if (vietnameseName == null) return null;
        return switch (vietnameseName.trim()) {
            case "Cá Hồi Áp Chảo", "Cá hồi áp chảo" -> "Pan-Seared Salmon";
            case "Mì Ý Bò Bằm" -> "Spaghetti Bolognese";
            case "Steak Bò Mỹ" -> "U.S. Beef Steak";
            case "Panna Cotta Dâu Tây" -> "Strawberry Panna Cotta";
            default -> null;
        };
    }

    private String knownEnglishFoodDescription(String vietnameseName, String vietnameseDescription) {
        if (vietnameseName == null || vietnameseDescription == null) return null;
        String description = vietnameseDescription.trim();
        return switch (vietnameseName.trim()) {
            case "Cá Hồi Áp Chảo", "Cá hồi áp chảo" ->
                    description.equals("Cá hồi Na Uy áp chảo giòn da, sốt bơ chanh và rau mầm tươi.")
                            ? "Crispy-skin Norwegian salmon with lemon butter sauce and fresh microgreens."
                            : null;
            case "Mì Ý Bò Bằm" ->
                    description.equals("Mì Ý dai mềm kết hợp sốt cà chua bò bằm đậm đà, phủ phô mai thơm béo.")
                            ? "Spaghetti tossed with a rich tomato and minced-beef sauce, topped with savory cheese."
                            : null;
            case "Steak Bò Mỹ" ->
                    description.equals("Thịt bò Mỹ áp chảo mềm mọng, đậm vị, dùng kèm rau củ và sốt đặc trưng.")
                            ? "Juicy pan-seared U.S. beef steak served with vegetables and the restaurant signature sauce."
                            : null;
            case "Panna Cotta Dâu Tây" ->
                    description.equals("Panna cotta mềm mịn, béo nhẹ kết hợp sốt dâu tây chua ngọt tươi mát.")
                            ? "Smooth, lightly creamy panna cotta with a refreshing sweet-tart strawberry sauce."
                            : null;
            default -> null;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
