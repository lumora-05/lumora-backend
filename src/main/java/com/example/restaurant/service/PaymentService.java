package com.example.restaurant.service;

import com.example.restaurant.config.VietQrProperties;
import com.example.restaurant.dto.LoyaltyPreviewResponse;
import com.example.restaurant.dto.PaymentRequest;
import com.example.restaurant.dto.PaymentSlipItemResponse;
import com.example.restaurant.dto.PaymentSlipResponse;
import com.example.restaurant.dto.RevenueResponse;
import com.example.restaurant.dto.VietQrResponse;
import com.example.restaurant.entity.DiningTable;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.Invoice;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.repository.EmployeeRepository;
import com.example.restaurant.repository.InvoiceRepository;
import com.example.restaurant.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PaymentService {
    private static final String METHOD_CASH = "TIEN_MAT";
    private static final String METHOD_BANK_TRANSFER = "CHUYEN_KHOAN";

    private static final Set<String> ALLOWED_PAYMENT_METHODS = Set.of(
            METHOD_CASH,
            METHOD_BANK_TRANSFER
    );

    private static final Set<String> OPEN_ORDER_STATUSES = Set.of(
            "CHO_XAC_NHAN",
            "DA_XAC_NHAN",
            "DANG_CHUAN_BI",
            "DANG_CHE_BIEN",
            "SAN_SANG",
            "SAN_SANG_PHUC_VU",
            "DA_HOAN_THANH",
            "DA_PHUC_VU",
            "CHO_THANH_TOAN",
            "SAN_SANG_THANH_TOAN"
    );

    /**
     * Thu ngân chỉ được thanh toán sau khi khách hoặc nhân viên phục vụ đã gửi
     * yêu cầu thanh toán. Không cho phép bỏ qua bước CHO_THANH_TOAN.
     */
    private static final Set<String> PAYABLE_ORDER_STATUSES = Set.of(
            "CHO_THANH_TOAN",
            "SAN_SANG_THANH_TOAN"
    );

    private static final Pattern SAFE_PATH_PART = Pattern.compile("[A-Za-z0-9_]+");

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final EmployeeRepository employeeRepository;
    private final RealtimeNotificationService realtimeNotificationService;
    private final SystemActivityService systemActivityService;
    private final VietQrProperties vietQrProperties;
    private final OrderPricingService orderPricingService;
    private final TableArrangementService tableArrangementService;
    private final ReservationService reservationService;
    private final LoyaltyService loyaltyService;

    public PaymentService(InvoiceRepository invoiceRepository,
                          OrderRepository orderRepository,
                          EmployeeRepository employeeRepository,
                          RealtimeNotificationService realtimeNotificationService,
                          SystemActivityService systemActivityService,
                          VietQrProperties vietQrProperties,
                          OrderPricingService orderPricingService,
                          TableArrangementService tableArrangementService,
                          ReservationService reservationService,
                          LoyaltyService loyaltyService) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.employeeRepository = employeeRepository;
        this.realtimeNotificationService = realtimeNotificationService;
        this.systemActivityService = systemActivityService;
        this.vietQrProperties = vietQrProperties;
        this.orderPricingService = orderPricingService;
        this.tableArrangementService = tableArrangementService;
        this.reservationService = reservationService;
        this.loyaltyService = loyaltyService;
    }

    /**
     * Xác nhận thanh toán cuối cùng. In phiếu tạm tính hoặc tạo VietQR không gọi
     * phương thức này, vì vậy không thể vô tình chuyển đơn sang DA_THANH_TOAN.
     */
    @Transactional
    public Invoice createInvoice(PaymentRequest request, String username) {
        Order order = orderRepository.findByIdForUpdate(request.maDonHang())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng: " + request.maDonHang()
                ));

        if (invoiceRepository.findByDonHang_MaDonHang(order.getMaDonHang()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng đã được thanh toán");
        }

        orderPricingService.recalculate(order);
        ensurePayable(order);
        Employee cashier = requireCashier(username);
        LoyaltyService.PreparedLoyalty loyalty = loyaltyService.prepareForPayment(
                request.soDienThoaiKhachHang(),
                request.hoTenKhachHang(),
                request.diemSuDung(),
                order.getTongTien()
        );
        String paymentMethod = normalizePaymentMethod(request.phuongThucThanhToan());
        PaymentAmounts amounts = validatePaymentAmounts(loyalty.finalAmount(), request, paymentMethod);
        String transactionCode = METHOD_BANK_TRANSFER.equals(paymentMethod)
                ? normalizeTransactionCode(request.maGiaoDich())
                : null;

        if (transactionCode != null
                && invoiceRepository.existsByMaGiaoDichIgnoreCase(transactionCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã giao dịch đã được sử dụng");
        }

        LocalDateTime paidAt = LocalDateTime.now();
        Invoice invoice = new Invoice();
        invoice.setDonHang(order);
        invoice.setNhanVien(cashier);
        invoice.setKhachHang(loyalty.customer());
        invoice.setTamTinh(normalizedMoney(order.getTamTinh()));
        invoice.setTienGiam(normalizedMoney(order.getTienGiam()));
        invoice.setPhiGiaoHang(BigDecimal.ZERO.setScale(2));
        invoice.setDiemDaSuDung(loyalty.pointsUsed());
        invoice.setTienGiamTuDiem(loyalty.pointDiscount());
        invoice.setDiemDuocCong(loyalty.pointsEarned());
        invoice.setMaCodeKhuyenMai(
                order.getKhuyenMai() == null ? null : order.getKhuyenMai().getMaCode()
        );
        invoice.setTongTien(normalizedMoney(loyalty.finalAmount()));
        invoice.setThoiGianTao(paidAt);
        invoice.setThoiGianThanhToan(paidAt);
        invoice.setPhuongThucThanhToan(paymentMethod);
        invoice.setTrangThaiThanhToan("DA_THANH_TOAN");
        invoice.setTienKhachDua(amounts.cashReceived());
        invoice.setTienThua(amounts.changeAmount());
        invoice.setMaGiaoDich(transactionCode);
        invoice.setGhiChu(trimToNull(request.ghiChu()));
        invoice.setNoiDungChuyenKhoan(
                METHOD_BANK_TRANSFER.equals(paymentMethod)
                        ? buildTransferDescription(order.getMaDonHang())
                        : null
        );

        // Lưu hóa đơn trước; ràng buộc unique ma_don_hang/ma_giao_dich là lớp
        // bảo vệ cuối cùng nếu có hai request thanh toán đồng thời.
        Invoice savedInvoice = invoiceRepository.saveAndFlush(invoice);

        order.setKhachHang(loyalty.customer());
        order.setDiemDaSuDung(loyalty.pointsUsed());
        order.setTienGiamTuDiem(loyalty.pointDiscount());
        order.setDiemDuocCong(loyalty.pointsEarned());
        order.setTongTien(loyalty.finalAmount());
        order.setTrangThai("DA_THANH_TOAN");
        Order savedOrder = orderRepository.saveAndFlush(order);
        loyaltyService.completePayment(loyalty, savedOrder);
        reservationService.completeByOrder(savedOrder);
        releaseTableWhenNoOtherOpenOrder(savedOrder);

        systemActivityService.record(
                "PAYMENT_COMPLETED",
                "Đơn hàng #DH" + savedOrder.getMaDonHang() + " đã được thanh toán",
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyPaymentCompleted(savedInvoice);
        realtimeNotificationService.notifyCustomerOrderChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedInvoice);
        return savedInvoice;
    }

    /** Xem trước số điểm được dùng/cộng và tổng tiền sau khi đổi điểm. */
    @Transactional(readOnly = true)
    public LoyaltyPreviewResponse previewLoyalty(Integer orderId,
                                                 String phone,
                                                 Integer pointsToUse) {
        Order order = findPayableOrder(orderId);
        return loyaltyService.preview(phone, pointsToUse, order.getTongTien());
    }

    /** Tạo VietQR động nhưng không ghi dữ liệu vào database. */
    @Transactional(readOnly = true)
    public VietQrResponse createVietQr(Integer orderId) {
        return createVietQr(orderId, null, 0);
    }

    /** Tạo VietQR theo tổng tiền sau khi xem trước đổi điểm. */
    @Transactional(readOnly = true)
    public VietQrResponse createVietQr(Integer orderId, String phone, Integer pointsToUse) {
        Order order = findPayableOrder(orderId);
        LoyaltyPreviewResponse preview = loyaltyService.preview(phone, pointsToUse, order.getTongTien());
        return buildVietQr(order, preview.tongThanhToan());
    }

    /**
     * Tạo dữ liệu phiếu tạm tính có VietQR. Endpoint này chỉ đọc dữ liệu, không
     * tạo hóa đơn, không đổi trạng thái đơn và không giải phóng bàn.
     */
    @Transactional(readOnly = true)
    public PaymentSlipResponse createPaymentSlip(Integer orderId) {
        Order order = findPayableOrder(orderId);
        List<PaymentSlipItemResponse> items = order.getChiTietDonHang().stream()
                .filter(item -> !"DA_HUY".equalsIgnoreCase(item.getTrangThaiMon()))
                .map(this::toSlipItem)
                .toList();

        DiningTable table = order.getBanAn();
        String waiterName = order.getNhanVien() != null
                ? order.getNhanVien().getHoTen()
                : null;

        return new PaymentSlipResponse(
                order.getMaDonHang(),
                String.format("DH%07d", order.getMaDonHang()),
                table != null ? table.getMaBan() : null,
                table != null ? table.getTenBan() : null,
                order.getThoiGianDat(),
                order.getThoiGianYeuCauThanhToan(),
                waiterName,
                order.getKhuyenMai() == null ? null : order.getKhuyenMai().getMaCode(),
                normalizedMoney(order.getTamTinh()),
                normalizedMoney(order.getTienGiam()),
                normalizedMoney(order.getTongTien()),
                order.getTrangThai(),
                items,
                buildVietQr(order),
                LocalDateTime.now(),
                "PHIEU_TAM_TINH"
        );
    }

    @Transactional(readOnly = true)
    public Invoice findByOrderId(Integer orderId) {
        return invoiceRepository.findByDonHang_MaDonHang(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Đơn hàng chưa có hóa đơn: " + orderId
                ));
    }

    @Transactional(readOnly = true)
    public RevenueResponse revenue(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng ngày không hợp lệ");
        }
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu không được sau ngày kết thúc");
        }

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay().minusNanos(1);
        BigDecimal total = invoiceRepository.totalRevenue("DA_THANH_TOAN", start, end);
        long count = invoiceRepository.countPaidInvoices(
                "DA_THANH_TOAN",
                start,
                end
        );
        return new RevenueResponse(from, to, total, count);
    }

    private Order findPayableOrder(Integer orderId) {
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã đơn hàng không hợp lệ");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng: " + orderId
                ));

        if (invoiceRepository.findByDonHang_MaDonHang(orderId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng đã thanh toán, vui lòng in hóa đơn chính thức"
            );
        }

        orderPricingService.recalculate(order);
        ensurePayable(order);
        return order;
    }

    private void ensurePayable(Order order) {
        String status = normalizeText(order.getTrangThai());
        if (!PAYABLE_ORDER_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng chưa sẵn sàng để thanh toán"
            );
        }
        if (order.getTongTien() == null || order.getTongTien().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tổng tiền đơn hàng không hợp lệ");
        }
    }

    private Employee requireCashier(String username) {
        String normalizedUsername = trimToNull(username);
        if (normalizedUsername == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Không xác định được tài khoản đang đăng nhập"
            );
        }

        Employee cashier = employeeRepository.findByTenDangNhap(normalizedUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy nhân viên từ tài khoản đăng nhập"
                ));

        String roleName = cashier.getVaiTro() != null
                ? normalizeText(cashier.getVaiTro().getTenVaiTro())
                : null;
        if (!"CASHIER".equals(roleName)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ nhân viên thu ngân mới được xác nhận thanh toán"
            );
        }

        if (!"DANG_LAM_VIEC".equals(normalizeText(cashier.getTrangThai()))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nhân viên thu ngân hiện không còn làm việc"
            );
        }
        return cashier;
    }

    private String normalizePaymentMethod(String rawMethod) {
        String method = normalizeText(rawMethod);
        if (!ALLOWED_PAYMENT_METHODS.contains(method)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ hỗ trợ hai phương thức thanh toán: TIEN_MAT và CHUYEN_KHOAN"
            );
        }
        return method;
    }

    private PaymentAmounts validatePaymentAmounts(BigDecimal payableTotal,
                                                   PaymentRequest request,
                                                   String method) {
        BigDecimal total = normalizedMoney(payableTotal);

        if (METHOD_CASH.equals(method)) {
            BigDecimal received = request.tienKhachDua();
            if (received == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Vui lòng nhập số tiền khách đưa"
                );
            }
            if (received.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tiền khách đưa phải lớn hơn 0"
                );
            }
            if (received.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tiền khách đưa phải là số nguyên"
                );
            }
            if (received.compareTo(total) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tiền khách đưa không được nhỏ hơn tổng tiền"
                );
            }

            BigDecimal normalizedReceived = received.setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal change = normalizedReceived.subtract(total).setScale(2, RoundingMode.UNNECESSARY);
            return new PaymentAmounts(normalizedReceived, change);
        }

        // Với chuyển khoản, frontend chỉ gọi API sau khi thu ngân tích xác nhận
        // đã kiểm tra tài khoản và nhận đủ tiền.
        return new PaymentAmounts(null, BigDecimal.ZERO.setScale(2));
    }

    private VietQrResponse buildVietQr(Order order) {
        return buildVietQr(order, order.getTongTien());
    }

    private VietQrResponse buildVietQr(Order order, BigDecimal payableAmount) {
        String bankId = requireConfig(vietQrProperties.getBankId(), "VIETQR_BANK_ID");
        String accountNo = requireConfig(vietQrProperties.getAccountNo(), "VIETQR_ACCOUNT_NO");
        String accountName = requireConfig(vietQrProperties.getAccountName(), "VIETQR_ACCOUNT_NAME");
        String template = trimToNull(vietQrProperties.getTemplate());
        if (template == null) {
            template = "compact2";
        }

        validateSafePathPart(bankId, "Mã ngân hàng VietQR");
        validateSafePathPart(accountNo, "Số tài khoản VietQR");
        validateSafePathPart(template, "Mẫu VietQR");

        BigDecimal amount;
        try {
            amount = normalizedMoney(payableAmount).setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền VietQR phải là số nguyên"
            );
        }

        String amountText = amount.toPlainString();
        if (amountText.length() > 13) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số tiền vượt giới hạn tạo VietQR"
            );
        }

        String addInfo = buildTransferDescription(order.getMaDonHang());
        String baseUrl = "https://img.vietqr.io/image/"
                + bankId + "-" + accountNo + "-" + template + ".png";
        String qrUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("amount", amountText)
                .queryParam("addInfo", addInfo)
                .queryParam("accountName", accountName)
                .build()
                .encode()
                .toUriString();

        String bankName = trimToNull(vietQrProperties.getBankName());
        if (bankName == null) {
            bankName = bankId;
        }

        return new VietQrResponse(
                order.getMaDonHang(),
                bankId,
                bankName,
                accountNo,
                accountName,
                amount,
                addInfo,
                template,
                qrUrl
        );
    }

    private PaymentSlipItemResponse toSlipItem(OrderItem item) {
        BigDecimal unitPrice = item.getDonGia() != null
                ? item.getDonGia()
                : BigDecimal.ZERO;
        int quantity = item.getSoLuong() != null ? item.getSoLuong() : 0;
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        String foodName = item.getMonAn() != null
                ? item.getMonAn().getTenMonAn()
                : "Món ăn";

        return new PaymentSlipItemResponse(
                item.getMaChiTiet(),
                foodName,
                quantity,
                unitPrice,
                lineTotal,
                item.getGhiChu()
        );
    }

    private void releaseTableWhenNoOtherOpenOrder(Order paidOrder) {
        DiningTable table = paidOrder.getBanAn();
        if (table == null || table.getMaBan() == null) {
            return;
        }

        boolean hasOtherOpenOrder = orderRepository.existsByBanAn_MaBanAndTrangThaiInAndMaDonHangNot(
                table.getMaBan(),
                OPEN_ORDER_STATUSES,
                paidOrder.getMaDonHang()
        );
        if (hasOtherOpenOrder) {
            tableArrangementService.updateServiceStatus(table, "DANG_SU_DUNG");
        } else {
            tableArrangementService.releaseAfterTerminalOrder(table);
        }
    }

    private String buildTransferDescription(Integer orderId) {
        String prefix = trimToNull(vietQrProperties.getDescriptionPrefix());
        if (prefix == null) {
            prefix = "LUMORA";
        }

        prefix = removeVietnameseAccents(prefix)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (prefix.isBlank()) {
            prefix = "LUMORA";
        }

        String description = prefix + " DH" + orderId;
        if (description.length() > 50) {
            description = description.substring(0, 50).trim();
        }
        return description;
    }

    private String normalizeTransactionCode(String value) {
        String code = trimToNull(value);
        if (code == null) {
            return null;
        }

        code = code.replaceAll("[\\r\\n\\t]", "")
                .toUpperCase(Locale.ROOT)
                .trim();
        if (code.length() < 4 || code.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã giao dịch phải từ 4 đến 100 ký tự hoặc để trống"
            );
        }
        return code;
    }

    private BigDecimal normalizedMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String requireConfig(String value, String environmentVariable) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình VietQR. Vui lòng khai báo biến " + environmentVariable
            );
        }
        return normalized;
    }

    private void validateSafePathPart(String value, String fieldName) {
        if (!SAFE_PATH_PART.matcher(value).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " chứa ký tự không hợp lệ"
            );
        }
    }

    private String normalizeText(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String removeVietnameseAccents(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
    }

    private record PaymentAmounts(BigDecimal cashReceived, BigDecimal changeAmount) {
    }
}
