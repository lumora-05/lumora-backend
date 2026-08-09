package com.example.restaurant.service;

import com.example.restaurant.config.ChatbotAiProperties;
import com.example.restaurant.config.LoyaltyPolicyProperties;
import com.example.restaurant.config.ReservationPolicyProperties;
import com.example.restaurant.config.RestaurantInfoProperties;
import com.example.restaurant.config.VietQrProperties;
import com.example.restaurant.dto.PublicSystemSettingResponse;
import com.example.restaurant.dto.SystemSettingRequest;
import com.example.restaurant.dto.SystemSettingResponse;
import com.example.restaurant.entity.SystemSetting;
import com.example.restaurant.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Service
public class SystemSettingService {
    private static final int DEFAULT_RESERVATION_DURATION_MINUTES = 120;
    private static final int DEFAULT_RESERVATION_PREPARATION_MINUTES = 30;
    private static final int DEFAULT_RESERVATION_NO_SHOW_GRACE_MINUTES = 15;
    private static final int DEFAULT_RESERVATION_CHECK_IN_EARLY_MINUTES = 30;
    private static final int DEFAULT_RESERVATION_MINIMUM_ADVANCE_MINUTES = 30;
    private static final int DEFAULT_RESERVATION_MAXIMUM_ADVANCE_DAYS = 60;
    private static final BigDecimal DEFAULT_LOYALTY_MONEY_PER_POINT = new BigDecimal("10000");
    private static final BigDecimal DEFAULT_LOYALTY_REDEEM_VALUE = new BigDecimal("1000");
    private static final int DEFAULT_LOYALTY_MINIMUM_REDEEM_POINTS = 20;
    private static final BigDecimal DEFAULT_LOYALTY_MAXIMUM_REDEEM_RATIO = new BigDecimal("0.20");

    private final SystemSettingRepository systemSettingRepository;
    private final RestaurantInfoProperties restaurantInfoProperties;
    private final VietQrProperties vietQrProperties;
    private final ReservationPolicyProperties reservationPolicyProperties;
    private final LoyaltyPolicyProperties loyaltyPolicyProperties;
    private final ChatbotAiProperties chatbotAiProperties;
    private final FileStorageService fileStorageService;
    private final SystemActivityService systemActivityService;

    public SystemSettingService(SystemSettingRepository systemSettingRepository,
                                RestaurantInfoProperties restaurantInfoProperties,
                                VietQrProperties vietQrProperties,
                                ReservationPolicyProperties reservationPolicyProperties,
                                LoyaltyPolicyProperties loyaltyPolicyProperties,
                                ChatbotAiProperties chatbotAiProperties,
                                FileStorageService fileStorageService,
                                SystemActivityService systemActivityService) {
        this.systemSettingRepository = systemSettingRepository;
        this.restaurantInfoProperties = restaurantInfoProperties;
        this.vietQrProperties = vietQrProperties;
        this.reservationPolicyProperties = reservationPolicyProperties;
        this.loyaltyPolicyProperties = loyaltyPolicyProperties;
        this.chatbotAiProperties = chatbotAiProperties;
        this.fileStorageService = fileStorageService;
        this.systemActivityService = systemActivityService;
    }

    @Transactional
    public SystemSettingResponse getSettings() {
        SystemSetting setting = getOrCreate();
        syncRuntimeSettings(setting);
        return toResponse(setting);
    }

    @Transactional
    public PublicSystemSettingResponse getPublicSettings() {
        SystemSetting setting = getOrCreate();
        syncRuntimeSettings(setting);
        return toPublicResponse(setting);
    }

    @Transactional
    public SystemSettingResponse update(SystemSettingRequest request) {
        SystemSetting setting = getOrCreate();

        // Thông tin nhà hàng - giữ nguyên contract cũ.
        setting.setTenNhaHang(cleanRequired(request.getRestaurantName()));
        setting.setDiaChi(cleanOptional(request.getAddress()));
        setting.setSoDienThoai(cleanOptional(request.getPhone()));
        setting.setEmail(cleanOptional(request.getEmail()));
        setting.setGioMoCua(cleanOptional(request.getOpeningHours()));
        setting.setReservationUrl(cleanOptional(request.getReservationUrl()));
        setting.setMenuUrl(cleanOptional(request.getMenuUrl()));

        // Các nhóm cấu hình mới đều nullable để frontend cũ vẫn gọi PUT bình thường.
        if (request.getReservationDefaultDurationMinutes() != null) {
            setting.setReservationDefaultDurationMinutes(request.getReservationDefaultDurationMinutes());
        }
        if (request.getReservationPreparationMinutes() != null) {
            setting.setReservationPreparationMinutes(request.getReservationPreparationMinutes());
        }
        if (request.getReservationNoShowGraceMinutes() != null) {
            setting.setReservationNoShowGraceMinutes(request.getReservationNoShowGraceMinutes());
        }
        if (request.getReservationCheckInEarlyMinutes() != null) {
            setting.setReservationCheckInEarlyMinutes(request.getReservationCheckInEarlyMinutes());
        }
        if (request.getReservationMinimumAdvanceMinutes() != null) {
            setting.setReservationMinimumAdvanceMinutes(request.getReservationMinimumAdvanceMinutes());
        }
        if (request.getReservationMaximumAdvanceDays() != null) {
            setting.setReservationMaximumAdvanceDays(request.getReservationMaximumAdvanceDays());
        }

        if (request.getVietQrBankId() != null) {
            setting.setVietQrBankId(cleanOptional(request.getVietQrBankId()));
        }
        if (request.getVietQrBankName() != null) {
            setting.setVietQrBankName(cleanOptional(request.getVietQrBankName()));
        }
        if (request.getVietQrAccountNo() != null) {
            setting.setVietQrAccountNo(cleanOptional(request.getVietQrAccountNo()));
        }
        if (request.getVietQrAccountName() != null) {
            setting.setVietQrAccountName(cleanOptional(request.getVietQrAccountName()));
        }
        if (request.getVietQrTemplate() != null) {
            setting.setVietQrTemplate(cleanOptional(request.getVietQrTemplate()));
        }
        if (request.getVietQrDescriptionPrefix() != null) {
            setting.setVietQrDescriptionPrefix(cleanOptional(request.getVietQrDescriptionPrefix()));
        }

        if (request.getLoyaltyMoneyPerEarnedPoint() != null) {
            setting.setLoyaltyMoneyPerEarnedPoint(request.getLoyaltyMoneyPerEarnedPoint());
        }
        if (request.getLoyaltyValuePerRedeemedPoint() != null) {
            setting.setLoyaltyValuePerRedeemedPoint(request.getLoyaltyValuePerRedeemedPoint());
        }
        if (request.getLoyaltyMinimumRedeemPoints() != null) {
            setting.setLoyaltyMinimumRedeemPoints(request.getLoyaltyMinimumRedeemPoints());
        }
        if (request.getLoyaltyMaximumRedeemRatio() != null) {
            setting.setLoyaltyMaximumRedeemRatio(request.getLoyaltyMaximumRedeemRatio());
        }

        if (request.getChatbotEnabled() != null) {
            setting.setChatbotEnabled(request.getChatbotEnabled());
        }
        if (request.getChatbotModel() != null) {
            setting.setChatbotModel(cleanOptional(request.getChatbotModel()));
        }
        if (request.getChatbotTimeoutSeconds() != null) {
            setting.setChatbotTimeoutSeconds(request.getChatbotTimeoutSeconds());
        }
        if (request.getChatbotMaxOutputTokens() != null) {
            setting.setChatbotMaxOutputTokens(request.getChatbotMaxOutputTokens());
        }
        if (request.getChatbotMaxHistoryMessages() != null) {
            setting.setChatbotMaxHistoryMessages(request.getChatbotMaxHistoryMessages());
        }
        if (request.getChatbotMinimumConfidence() != null) {
            setting.setChatbotMinimumConfidence(request.getChatbotMinimumConfidence());
        }

        SystemSetting saved = systemSettingRepository.save(setting);
        syncRuntimeSettings(saved);
        systemActivityService.record(
                "SYSTEM_SETTINGS_UPDATED",
                "Cập nhật cài đặt hệ thống nhà hàng",
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
        syncRuntimeSettings(getOrCreate());
    }

    private SystemSetting getOrCreate() {
        SystemSetting setting = systemSettingRepository.findById(SystemSetting.SINGLETON_ID)
                .orElseGet(() -> systemSettingRepository.save(createFromApplicationProperties()));
        if (backfillMissingConfiguration(setting)) {
            setting = systemSettingRepository.save(setting);
        }
        return setting;
    }

    private SystemSetting getOrCreateWithoutTransaction() {
        SystemSetting setting = systemSettingRepository.findById(SystemSetting.SINGLETON_ID)
                .orElseGet(() -> systemSettingRepository.saveAndFlush(createFromApplicationProperties()));
        if (backfillMissingConfiguration(setting)) {
            setting = systemSettingRepository.saveAndFlush(setting);
        }
        return setting;
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

        setting.setReservationDefaultDurationMinutes(DEFAULT_RESERVATION_DURATION_MINUTES);
        setting.setReservationPreparationMinutes(DEFAULT_RESERVATION_PREPARATION_MINUTES);
        setting.setReservationNoShowGraceMinutes(DEFAULT_RESERVATION_NO_SHOW_GRACE_MINUTES);
        setting.setReservationCheckInEarlyMinutes(DEFAULT_RESERVATION_CHECK_IN_EARLY_MINUTES);
        setting.setReservationMinimumAdvanceMinutes(DEFAULT_RESERVATION_MINIMUM_ADVANCE_MINUTES);
        setting.setReservationMaximumAdvanceDays(DEFAULT_RESERVATION_MAXIMUM_ADVANCE_DAYS);

        setting.setVietQrBankId(cleanOptional(vietQrProperties.getBankId()));
        setting.setVietQrBankName(cleanOptional(vietQrProperties.getBankName()));
        setting.setVietQrAccountNo(cleanOptional(vietQrProperties.getAccountNo()));
        setting.setVietQrAccountName(cleanOptional(vietQrProperties.getAccountName()));
        setting.setVietQrTemplate(defaultIfBlank(vietQrProperties.getTemplate(), "compact2"));
        setting.setVietQrDescriptionPrefix(defaultIfBlank(vietQrProperties.getDescriptionPrefix(), "LUMORA"));

        setting.setLoyaltyMoneyPerEarnedPoint(DEFAULT_LOYALTY_MONEY_PER_POINT);
        setting.setLoyaltyValuePerRedeemedPoint(DEFAULT_LOYALTY_REDEEM_VALUE);
        setting.setLoyaltyMinimumRedeemPoints(DEFAULT_LOYALTY_MINIMUM_REDEEM_POINTS);
        setting.setLoyaltyMaximumRedeemRatio(DEFAULT_LOYALTY_MAXIMUM_REDEEM_RATIO);

        setting.setChatbotEnabled(chatbotAiProperties.isEnabled());
        setting.setChatbotModel(defaultIfBlank(chatbotAiProperties.getModel(), "gpt-5-mini"));
        setting.setChatbotTimeoutSeconds(positiveOrDefault(chatbotAiProperties.getTimeoutSeconds(), 20));
        setting.setChatbotMaxOutputTokens(positiveOrDefault(chatbotAiProperties.getMaxOutputTokens(), 700));
        setting.setChatbotMaxHistoryMessages(nonNegativeOrDefault(chatbotAiProperties.getMaxHistoryMessages(), 8));
        setting.setChatbotMinimumConfidence(BigDecimal.valueOf(clamp(chatbotAiProperties.getMinimumConfidence(), 0d, 1d)));
        return setting;
    }

    /**
     * Hỗ trợ database đã có bản ghi từ phiên bản Cài đặt hệ thống cũ: các cột mới
     * được Hibernate tạo ra sẽ NULL và được điền bằng đúng chính sách/cấu hình cũ.
     */
    private boolean backfillMissingConfiguration(SystemSetting setting) {
        boolean changed = false;

        if (setting.getReservationDefaultDurationMinutes() == null) {
            setting.setReservationDefaultDurationMinutes(DEFAULT_RESERVATION_DURATION_MINUTES);
            changed = true;
        }
        if (setting.getReservationPreparationMinutes() == null) {
            setting.setReservationPreparationMinutes(DEFAULT_RESERVATION_PREPARATION_MINUTES);
            changed = true;
        }
        if (setting.getReservationNoShowGraceMinutes() == null) {
            setting.setReservationNoShowGraceMinutes(DEFAULT_RESERVATION_NO_SHOW_GRACE_MINUTES);
            changed = true;
        }
        if (setting.getReservationCheckInEarlyMinutes() == null) {
            setting.setReservationCheckInEarlyMinutes(DEFAULT_RESERVATION_CHECK_IN_EARLY_MINUTES);
            changed = true;
        }
        if (setting.getReservationMinimumAdvanceMinutes() == null) {
            setting.setReservationMinimumAdvanceMinutes(DEFAULT_RESERVATION_MINIMUM_ADVANCE_MINUTES);
            changed = true;
        }
        if (setting.getReservationMaximumAdvanceDays() == null) {
            setting.setReservationMaximumAdvanceDays(DEFAULT_RESERVATION_MAXIMUM_ADVANCE_DAYS);
            changed = true;
        }

        if (setting.getVietQrBankId() == null && StringUtils.hasText(vietQrProperties.getBankId())) {
            setting.setVietQrBankId(vietQrProperties.getBankId().trim());
            changed = true;
        }
        if (setting.getVietQrBankName() == null && StringUtils.hasText(vietQrProperties.getBankName())) {
            setting.setVietQrBankName(vietQrProperties.getBankName().trim());
            changed = true;
        }
        if (setting.getVietQrAccountNo() == null && StringUtils.hasText(vietQrProperties.getAccountNo())) {
            setting.setVietQrAccountNo(vietQrProperties.getAccountNo().trim());
            changed = true;
        }
        if (setting.getVietQrAccountName() == null && StringUtils.hasText(vietQrProperties.getAccountName())) {
            setting.setVietQrAccountName(vietQrProperties.getAccountName().trim());
            changed = true;
        }
        if (!StringUtils.hasText(setting.getVietQrTemplate())) {
            setting.setVietQrTemplate(defaultIfBlank(vietQrProperties.getTemplate(), "compact2"));
            changed = true;
        }
        if (!StringUtils.hasText(setting.getVietQrDescriptionPrefix())) {
            setting.setVietQrDescriptionPrefix(defaultIfBlank(vietQrProperties.getDescriptionPrefix(), "LUMORA"));
            changed = true;
        }

        if (setting.getLoyaltyMoneyPerEarnedPoint() == null) {
            setting.setLoyaltyMoneyPerEarnedPoint(DEFAULT_LOYALTY_MONEY_PER_POINT);
            changed = true;
        }
        if (setting.getLoyaltyValuePerRedeemedPoint() == null) {
            setting.setLoyaltyValuePerRedeemedPoint(DEFAULT_LOYALTY_REDEEM_VALUE);
            changed = true;
        }
        if (setting.getLoyaltyMinimumRedeemPoints() == null) {
            setting.setLoyaltyMinimumRedeemPoints(DEFAULT_LOYALTY_MINIMUM_REDEEM_POINTS);
            changed = true;
        }
        if (setting.getLoyaltyMaximumRedeemRatio() == null) {
            setting.setLoyaltyMaximumRedeemRatio(DEFAULT_LOYALTY_MAXIMUM_REDEEM_RATIO);
            changed = true;
        }

        if (setting.getChatbotEnabled() == null) {
            setting.setChatbotEnabled(chatbotAiProperties.isEnabled());
            changed = true;
        }
        if (!StringUtils.hasText(setting.getChatbotModel())) {
            setting.setChatbotModel(defaultIfBlank(chatbotAiProperties.getModel(), "gpt-5-mini"));
            changed = true;
        }
        if (setting.getChatbotTimeoutSeconds() == null) {
            setting.setChatbotTimeoutSeconds(positiveOrDefault(chatbotAiProperties.getTimeoutSeconds(), 20));
            changed = true;
        }
        if (setting.getChatbotMaxOutputTokens() == null) {
            setting.setChatbotMaxOutputTokens(positiveOrDefault(chatbotAiProperties.getMaxOutputTokens(), 700));
            changed = true;
        }
        if (setting.getChatbotMaxHistoryMessages() == null) {
            setting.setChatbotMaxHistoryMessages(nonNegativeOrDefault(chatbotAiProperties.getMaxHistoryMessages(), 8));
            changed = true;
        }
        if (setting.getChatbotMinimumConfidence() == null) {
            setting.setChatbotMinimumConfidence(BigDecimal.valueOf(clamp(chatbotAiProperties.getMinimumConfidence(), 0d, 1d)));
            changed = true;
        }
        return changed;
    }

    private void syncRuntimeSettings(SystemSetting setting) {
        // Thông tin nhà hàng
        restaurantInfoProperties.setName(defaultIfBlank(setting.getTenNhaHang(), "LUMORA"));
        restaurantInfoProperties.setAddress(defaultIfBlank(setting.getDiaChi(), "Chưa cập nhật"));
        restaurantInfoProperties.setPhone(defaultIfBlank(setting.getSoDienThoai(), "Chưa cập nhật"));
        restaurantInfoProperties.setEmail(defaultIfBlank(setting.getEmail(), "Chưa cập nhật"));
        restaurantInfoProperties.setOpeningHours(defaultIfBlank(setting.getGioMoCua(), "Chưa cập nhật"));
        restaurantInfoProperties.setReservationUrl(defaultIfBlank(setting.getReservationUrl(), "/reservations"));
        restaurantInfoProperties.setMenuUrl(defaultIfBlank(setting.getMenuUrl(), "/#menu"));

        // Đặt bàn
        reservationPolicyProperties.setDefaultDurationMinutes(
                positiveOrDefault(setting.getReservationDefaultDurationMinutes(), DEFAULT_RESERVATION_DURATION_MINUTES)
        );
        reservationPolicyProperties.setTablePreparationMinutes(
                nonNegativeOrDefault(setting.getReservationPreparationMinutes(), DEFAULT_RESERVATION_PREPARATION_MINUTES)
        );
        reservationPolicyProperties.setNoShowGraceMinutes(
                nonNegativeOrDefault(setting.getReservationNoShowGraceMinutes(), DEFAULT_RESERVATION_NO_SHOW_GRACE_MINUTES)
        );
        reservationPolicyProperties.setCheckInEarlyMinutes(
                nonNegativeOrDefault(setting.getReservationCheckInEarlyMinutes(), DEFAULT_RESERVATION_CHECK_IN_EARLY_MINUTES)
        );
        reservationPolicyProperties.setMinimumAdvanceMinutes(
                nonNegativeOrDefault(setting.getReservationMinimumAdvanceMinutes(), DEFAULT_RESERVATION_MINIMUM_ADVANCE_MINUTES)
        );
        reservationPolicyProperties.setMaximumAdvanceDays(
                positiveOrDefault(setting.getReservationMaximumAdvanceDays(), DEFAULT_RESERVATION_MAXIMUM_ADVANCE_DAYS)
        );

        // Thanh toán VietQR
        vietQrProperties.setBankId(cleanOptional(setting.getVietQrBankId()));
        vietQrProperties.setBankName(cleanOptional(setting.getVietQrBankName()));
        vietQrProperties.setAccountNo(cleanOptional(setting.getVietQrAccountNo()));
        vietQrProperties.setAccountName(cleanOptional(setting.getVietQrAccountName()));
        vietQrProperties.setTemplate(defaultIfBlank(setting.getVietQrTemplate(), "compact2"));
        vietQrProperties.setDescriptionPrefix(defaultIfBlank(setting.getVietQrDescriptionPrefix(), "LUMORA"));

        // Tích điểm
        loyaltyPolicyProperties.setMoneyPerEarnedPoint(
                positiveMoneyOrDefault(setting.getLoyaltyMoneyPerEarnedPoint(), DEFAULT_LOYALTY_MONEY_PER_POINT)
        );
        loyaltyPolicyProperties.setValuePerRedeemedPoint(
                positiveMoneyOrDefault(setting.getLoyaltyValuePerRedeemedPoint(), DEFAULT_LOYALTY_REDEEM_VALUE)
        );
        loyaltyPolicyProperties.setMinimumRedeemPoints(
                positiveOrDefault(setting.getLoyaltyMinimumRedeemPoints(), DEFAULT_LOYALTY_MINIMUM_REDEEM_POINTS)
        );
        loyaltyPolicyProperties.setMaximumRedeemRatio(
                ratioOrDefault(setting.getLoyaltyMaximumRedeemRatio(), DEFAULT_LOYALTY_MAXIMUM_REDEEM_RATIO)
        );

        // Chatbot. API key/base URL vẫn lấy từ biến môi trường, không ghi vào database.
        chatbotAiProperties.setEnabled(Boolean.TRUE.equals(setting.getChatbotEnabled()));
        chatbotAiProperties.setModel(defaultIfBlank(setting.getChatbotModel(), "gpt-5-mini"));
        chatbotAiProperties.setTimeoutSeconds(positiveOrDefault(setting.getChatbotTimeoutSeconds(), 20));
        chatbotAiProperties.setMaxOutputTokens(positiveOrDefault(setting.getChatbotMaxOutputTokens(), 700));
        chatbotAiProperties.setMaxHistoryMessages(nonNegativeOrDefault(setting.getChatbotMaxHistoryMessages(), 8));
        chatbotAiProperties.setMinimumConfidence(
                ratioOrDefault(setting.getChatbotMinimumConfidence(), new BigDecimal("0.45")).doubleValue()
        );
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
                .reservationDefaultDurationMinutes(setting.getReservationDefaultDurationMinutes())
                .reservationPreparationMinutes(setting.getReservationPreparationMinutes())
                .reservationNoShowGraceMinutes(setting.getReservationNoShowGraceMinutes())
                .reservationCheckInEarlyMinutes(setting.getReservationCheckInEarlyMinutes())
                .reservationMinimumAdvanceMinutes(setting.getReservationMinimumAdvanceMinutes())
                .reservationMaximumAdvanceDays(setting.getReservationMaximumAdvanceDays())
                .vietQrBankId(setting.getVietQrBankId())
                .vietQrBankName(setting.getVietQrBankName())
                .vietQrAccountNo(setting.getVietQrAccountNo())
                .vietQrAccountName(setting.getVietQrAccountName())
                .vietQrTemplate(setting.getVietQrTemplate())
                .vietQrDescriptionPrefix(setting.getVietQrDescriptionPrefix())
                .loyaltyMoneyPerEarnedPoint(setting.getLoyaltyMoneyPerEarnedPoint())
                .loyaltyValuePerRedeemedPoint(setting.getLoyaltyValuePerRedeemedPoint())
                .loyaltyMinimumRedeemPoints(setting.getLoyaltyMinimumRedeemPoints())
                .loyaltyMaximumRedeemRatio(setting.getLoyaltyMaximumRedeemRatio())
                .chatbotEnabled(setting.getChatbotEnabled())
                .chatbotModel(setting.getChatbotModel())
                .chatbotTimeoutSeconds(setting.getChatbotTimeoutSeconds())
                .chatbotMaxOutputTokens(setting.getChatbotMaxOutputTokens())
                .chatbotMaxHistoryMessages(setting.getChatbotMaxHistoryMessages())
                .chatbotMinimumConfidence(setting.getChatbotMinimumConfidence())
                .updatedAt(setting.getNgayCapNhat())
                .build();
    }

    private PublicSystemSettingResponse toPublicResponse(SystemSetting setting) {
        return PublicSystemSettingResponse.builder()
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
                .reservationDefaultDurationMinutes(setting.getReservationDefaultDurationMinutes())
                .reservationPreparationMinutes(setting.getReservationPreparationMinutes())
                .reservationNoShowGraceMinutes(setting.getReservationNoShowGraceMinutes())
                .reservationCheckInEarlyMinutes(setting.getReservationCheckInEarlyMinutes())
                .reservationMinimumAdvanceMinutes(setting.getReservationMinimumAdvanceMinutes())
                .reservationMaximumAdvanceDays(setting.getReservationMaximumAdvanceDays())
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

    private int positiveOrDefault(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private int nonNegativeOrDefault(Integer value, int fallback) {
        return value != null && value >= 0 ? value : fallback;
    }

    private int nonNegativeOrDefault(int value, int fallback) {
        return value >= 0 ? value : fallback;
    }

    private BigDecimal positiveMoneyOrDefault(BigDecimal value, BigDecimal fallback) {
        return value != null && value.signum() > 0 ? value : fallback;
    }

    private BigDecimal ratioOrDefault(BigDecimal value, BigDecimal fallback) {
        return value != null && value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0 ? value : fallback;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
