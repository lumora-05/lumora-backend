package com.example.restaurant.service;

import com.example.restaurant.config.RestaurantInfoProperties;
import com.example.restaurant.dto.SystemSettingRequest;
import com.example.restaurant.dto.SystemSettingResponse;
import com.example.restaurant.entity.SystemSetting;
import com.example.restaurant.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SystemSettingService {
    private final SystemSettingRepository systemSettingRepository;
    private final RestaurantInfoProperties restaurantInfoProperties;
    private final FileStorageService fileStorageService;
    private final SystemActivityService systemActivityService;

    public SystemSettingService(SystemSettingRepository systemSettingRepository,
                                RestaurantInfoProperties restaurantInfoProperties,
                                FileStorageService fileStorageService,
                                SystemActivityService systemActivityService) {
        this.systemSettingRepository = systemSettingRepository;
        this.restaurantInfoProperties = restaurantInfoProperties;
        this.fileStorageService = fileStorageService;
        this.systemActivityService = systemActivityService;
    }

    @Transactional
    public SystemSettingResponse getSettings() {
        SystemSetting setting = getOrCreate();
        syncRuntimeRestaurantInfo(setting);
        return toResponse(setting);
    }

    @Transactional
    public SystemSettingResponse update(SystemSettingRequest request) {
        SystemSetting setting = getOrCreate();
        setting.setTenNhaHang(cleanRequired(request.getRestaurantName()));
        setting.setDiaChi(cleanOptional(request.getAddress()));
        setting.setSoDienThoai(cleanOptional(request.getPhone()));
        setting.setEmail(cleanOptional(request.getEmail()));
        setting.setGioMoCua(cleanOptional(request.getOpeningHours()));
        setting.setReservationUrl(cleanOptional(request.getReservationUrl()));
        setting.setMenuUrl(cleanOptional(request.getMenuUrl()));

        SystemSetting saved = systemSettingRepository.save(setting);
        syncRuntimeRestaurantInfo(saved);
        systemActivityService.record(
                "SYSTEM_SETTINGS_UPDATED",
                "Cập nhật thông tin và cấu hình chung của nhà hàng",
                SystemSetting.SINGLETON_ID
        );
        return toResponse(saved);
    }

    public SystemSettingResponse updateLogo(MultipartFile file) {
        String newUrl = fileStorageService.saveBrandLogoImage(file);
        SystemSetting setting = getOrCreateWithoutTransaction();
        String oldUrl = setting.getLogoUrl();
        SystemSetting saved;
        try {
            setting.setLogoUrl(newUrl);
            saved = systemSettingRepository.saveAndFlush(setting);
        } catch (RuntimeException ex) {
            fileStorageService.deleteBrandImage(newUrl);
            throw ex;
        }

        fileStorageService.deleteBrandImageIfDifferent(oldUrl, newUrl);
        systemActivityService.record(
                "SYSTEM_LOGO_UPDATED",
                "Thay đổi logo nhà hàng",
                SystemSetting.SINGLETON_ID
        );
        return toResponse(saved);
    }

    public SystemSettingResponse updateBanner(MultipartFile file) {
        String newUrl = fileStorageService.saveHomeBannerImage(file);
        SystemSetting setting = getOrCreateWithoutTransaction();
        String oldUrl = setting.getBannerUrl();
        SystemSetting saved;
        try {
            setting.setBannerUrl(newUrl);
            saved = systemSettingRepository.saveAndFlush(setting);
        } catch (RuntimeException ex) {
            fileStorageService.deleteBrandImage(newUrl);
            throw ex;
        }

        fileStorageService.deleteBrandImageIfDifferent(oldUrl, newUrl);
        systemActivityService.record(
                "SYSTEM_BANNER_UPDATED",
                "Thay đổi banner trang chủ",
                SystemSetting.SINGLETON_ID
        );
        return toResponse(saved);
    }

    public SystemSettingResponse removeLogo() {
        SystemSetting setting = getOrCreateWithoutTransaction();
        String oldUrl = setting.getLogoUrl();
        setting.setLogoUrl(null);
        SystemSetting saved = systemSettingRepository.saveAndFlush(setting);
        fileStorageService.deleteBrandImage(oldUrl);
        systemActivityService.record(
                "SYSTEM_LOGO_REMOVED",
                "Xóa logo tùy chỉnh của nhà hàng",
                SystemSetting.SINGLETON_ID
        );
        return toResponse(saved);
    }

    public SystemSettingResponse removeBanner() {
        SystemSetting setting = getOrCreateWithoutTransaction();
        String oldUrl = setting.getBannerUrl();
        setting.setBannerUrl(null);
        SystemSetting saved = systemSettingRepository.saveAndFlush(setting);
        fileStorageService.deleteBrandImage(oldUrl);
        systemActivityService.record(
                "SYSTEM_BANNER_REMOVED",
                "Xóa banner tùy chỉnh của trang chủ",
                SystemSetting.SINGLETON_ID
        );
        return toResponse(saved);
    }

    @Transactional
    public void initializeRuntimeSettings() {
        syncRuntimeRestaurantInfo(getOrCreate());
    }

    private SystemSetting getOrCreate() {
        return systemSettingRepository.findById(SystemSetting.SINGLETON_ID)
                .orElseGet(() -> systemSettingRepository.save(createFromApplicationProperties()));
    }

    private SystemSetting getOrCreateWithoutTransaction() {
        return systemSettingRepository.findById(SystemSetting.SINGLETON_ID)
                .orElseGet(() -> systemSettingRepository.saveAndFlush(createFromApplicationProperties()));
    }

    private SystemSetting createFromApplicationProperties() {
        SystemSetting setting = new SystemSetting();
        setting.setMaCaiDat(SystemSetting.SINGLETON_ID);
        setting.setTenNhaHang(defaultIfBlank(restaurantInfoProperties.getName(), "LUMORA"));
        setting.setDiaChi(cleanOptional(restaurantInfoProperties.getAddress()));
        setting.setSoDienThoai(cleanOptional(restaurantInfoProperties.getPhone()));
        setting.setEmail(cleanOptional(restaurantInfoProperties.getEmail()));
        setting.setGioMoCua(cleanOptional(restaurantInfoProperties.getOpeningHours()));
        setting.setReservationUrl(defaultIfBlank(restaurantInfoProperties.getReservationUrl(), "/reservations"));
        setting.setMenuUrl(defaultIfBlank(restaurantInfoProperties.getMenuUrl(), "/#menu"));
        return setting;
    }

    private void syncRuntimeRestaurantInfo(SystemSetting setting) {
        restaurantInfoProperties.setName(defaultIfBlank(setting.getTenNhaHang(), "LUMORA"));
        restaurantInfoProperties.setAddress(defaultIfBlank(setting.getDiaChi(), "Chưa cập nhật"));
        restaurantInfoProperties.setPhone(defaultIfBlank(setting.getSoDienThoai(), "Chưa cập nhật"));
        restaurantInfoProperties.setEmail(defaultIfBlank(setting.getEmail(), "Chưa cập nhật"));
        restaurantInfoProperties.setOpeningHours(defaultIfBlank(setting.getGioMoCua(), "Chưa cập nhật"));
        restaurantInfoProperties.setReservationUrl(defaultIfBlank(setting.getReservationUrl(), "/reservations"));
        restaurantInfoProperties.setMenuUrl(defaultIfBlank(setting.getMenuUrl(), "/#menu"));
    }

    private SystemSettingResponse toResponse(SystemSetting setting) {
        return SystemSettingResponse.builder()
                .id(setting.getMaCaiDat())
                .restaurantName(setting.getTenNhaHang())
                .address(setting.getDiaChi())
                .phone(setting.getSoDienThoai())
                .email(setting.getEmail())
                .openingHours(setting.getGioMoCua())
                .reservationUrl(setting.getReservationUrl())
                .menuUrl(setting.getMenuUrl())
                .logoUrl(setting.getLogoUrl())
                .bannerUrl(setting.getBannerUrl())
                .updatedAt(setting.getNgayCapNhat())
                .build();
    }

    private String cleanRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private String cleanOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
