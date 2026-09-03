package com.example.restaurant.service;

import com.example.restaurant.config.VietQrProperties;
import com.example.restaurant.dto.LoyaltyPreviewResponse;
import com.example.restaurant.dto.PaymentRequest;
import com.example.restaurant.dto.PaymentSlipItemResponse;
import com.example.restaurant.dto.PaymentSlipResponse;
import com.example.restaurant.dto.PayOsWebhookResponse;
import com.example.restaurant.dto.RevenueResponse;
import com.example.restaurant.dto.VietQrResponse;
import com.example.restaurant.entity.DiningTable;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.Invoice;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.entity.PayOsPayment;
import com.example.restaurant.repository.EmployeeRepository;
import com.example.restaurant.repository.InvoiceRepository;
import com.example.restaurant.repository.OrderRepository;
import com.example.restaurant.repository.PayOsPaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
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
    private final PayOsGatewayService payOsGatewayService;
    private final PayOsPaymentRepository payOsPaymentRepository;

    public PaymentService(InvoiceRepository invoiceRepository,
                          OrderRepository orderRepository,
                          EmployeeRepository employeeRepository,
                          RealtimeNotificationService realtimeNotificationService,
                          SystemActivityService systemActivityService,
                          VietQrProperties vietQrProperties,
                          OrderPricingService orderPricingService,
                          TableArrangementService tableArrangementService,
                          ReservationService reservationService,
                          LoyaltyService loyaltyService,
                          PayOsGatewayService payOsGatewayService,
                          PayOsPaymentRepository payOsPaymentRepository) {
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
        this.payOsGatewayService = payOsGatewayService;
        this.payOsPaymentRepository = payOsPaymentRepository;
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
        BigDecimal depositCredit = reservationService.depositCreditForOrder(order);
        BigDecimal depositApplied = normalizedMoney(depositCredit).min(normalizedMoney(loyalty.finalAmount()));
        BigDecimal remainingPayable = normalizedMoney(loyalty.finalAmount())
                .subtract(depositApplied)
                .max(BigDecimal.ZERO.setScale(2));

        String paymentMethod;
        PaymentAmounts amounts;
        String transactionCode;
        if (remainingPayable.signum() == 0) {
            paymentMethod = "TIEN_COC";
            amounts = new PaymentAmounts(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
            transactionCode = null;
        } else {
            paymentMethod = normalizePaymentMethod(request.phuongThucThanhToan());
            if (METHOD_BANK_TRANSFER.equals(paymentMethod)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Chuyển khoản VietQR được xác nhận tự động qua webhook payOS; không xác nhận thủ công"
                );
            }
            amounts = validatePaymentAmounts(remainingPayable, request, paymentMethod);
            transactionCode = null;
        }

        if (transactionCode != null) {
            if (invoiceRepository.existsByMaGiaoDichIgnoreCase(transactionCode)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã giao dịch đã được sử dụng");
            }
            reservationService.ensureTransactionCodeNotUsedByDeposit(transactionCode);
        }

        LocalDateTime paidAt = LocalDateTime.now();
        Invoice invoice = new Invoice();
        invoice.setDonHang(order);
        invoice.setNhanVien(cashier);
        invoice.setKhachHang(loyalty.customer());
        invoice.setTamTinh(normalizedMoney(order.getTamTinh()));
        invoice.setTienGiam(normalizedMoney(order.getTienGiam()));
        invoice.setTienCocDaKhauTru(depositApplied);
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
        reservationService.applyDepositByOrder(order, depositApplied);

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

    /**
     * Tạo VietQR cho đơn giao hàng trước khi nhà hàng xác nhận chế biến.
     * Khác đơn tại bàn, đơn giao hàng chưa ở trạng thái CHO_THANH_TOAN.
     */
    @Transactional(readOnly = true)
    public VietQrResponse createVietQrForDelivery(Integer orderId) {
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã đơn hàng không hợp lệ");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng: " + orderId
                ));
        if (!"GIAO_HANG".equals(normalizeText(order.getLoaiDon())) || order.getGiaoHang() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn giao hàng");
        }
        if (invoiceRepository.findByDonHang_MaDonHang(orderId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng đã có hóa đơn");
        }
        orderPricingService.recalculate(order);
        if (order.getTongTien() == null || order.getTongTien().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tổng tiền đơn hàng không hợp lệ");
        }
        return buildVietQr(order);
    }

    /**
     * Tạo hóa đơn khi đơn giao hàng được xác nhận giao thành công.
     * Không giải phóng bàn vì đơn giao hàng không gắn với bàn ăn.
     */
    @Transactional
    public Invoice completeDeliveryPayment(Order order, String username) {
        return completeDeliveryPayment(order, requireCashierOrAdmin(username));
    }

    /**
     * Dùng nhân viên đã được xác minh trước đó (ví dụ lúc Thu ngân xác nhận VietQR).
     * Không kiểm tra lại trạng thái ca làm tại thời điểm webhook giao thành công.
     */
    @Transactional
    public Invoice completeDeliveryPayment(Order order, Employee cashier) {
        if (order == null || order.getMaDonHang() == null
                || !"GIAO_HANG".equals(normalizeText(order.getLoaiDon()))
                || order.getGiaoHang() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn giao hàng không hợp lệ");
        }
        if (cashier == null || cashier.getMaNhanVien() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không xác định được nhân viên đã xác minh thanh toán");
        }

        Invoice existing = invoiceRepository.findByDonHang_MaDonHang(order.getMaDonHang()).orElse(null);
        if (existing != null) {
            order.setTrangThai("DA_THANH_TOAN");
            return existing;
        }

        orderPricingService.recalculate(order);
        String deliveryPaymentMethod = normalizeText(order.getGiaoHang().getPhuongThucThanhToan());
        String invoiceMethod = "VIETQR".equals(deliveryPaymentMethod)
                ? METHOD_BANK_TRANSFER
                : METHOD_CASH;
        String transactionCode = METHOD_BANK_TRANSFER.equals(invoiceMethod)
                ? normalizeTransactionCode(order.getGiaoHang().getMaGiaoDich())
                : null;
        if (METHOD_BANK_TRANSFER.equals(invoiceMethod) && transactionCode == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn VietQR chưa có mã giao dịch đã được xác nhận"
            );
        }
        if (transactionCode != null && invoiceRepository.existsByMaGiaoDichIgnoreCase(transactionCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã giao dịch đã được sử dụng");
        }

        LocalDateTime paidAt = LocalDateTime.now();
        Invoice invoice = new Invoice();
        invoice.setDonHang(order);
        invoice.setNhanVien(cashier);
        invoice.setTamTinh(normalizedMoney(order.getTamTinh()));
        invoice.setTienGiam(normalizedMoney(order.getTienGiam()));
        invoice.setPhiGiaoHang(deliveryFeeOf(order));
        invoice.setDiemDaSuDung(0);
        invoice.setTienGiamTuDiem(BigDecimal.ZERO.setScale(2));
        invoice.setDiemDuocCong(0);
        invoice.setMaCodeKhuyenMai(order.getKhuyenMai() == null ? null : order.getKhuyenMai().getMaCode());
        invoice.setTongTien(normalizedMoney(order.getTongTien()));
        invoice.setThoiGianTao(paidAt);
        invoice.setThoiGianThanhToan(paidAt);
        invoice.setPhuongThucThanhToan(invoiceMethod);
        invoice.setTrangThaiThanhToan("DA_THANH_TOAN");
        invoice.setTienKhachDua(METHOD_CASH.equals(invoiceMethod) ? normalizedMoney(order.getTongTien()) : null);
        invoice.setTienThua(BigDecimal.ZERO.setScale(2));
        invoice.setMaGiaoDich(transactionCode);
        invoice.setGhiChu("Thanh toán đơn giao hàng " + order.getGiaoHang().getMaVanChuyen());
        invoice.setNoiDungChuyenKhoan(
                METHOD_BANK_TRANSFER.equals(invoiceMethod)
                        ? buildTransferDescription(order.getMaDonHang())
                        : null
        );

        Invoice savedInvoice = invoiceRepository.saveAndFlush(invoice);
        order.setTrangThai("DA_THANH_TOAN");
        orderRepository.saveAndFlush(order);

        systemActivityService.record(
                "DELIVERY_PAYMENT_COMPLETED",
                "Đơn giao hàng #DH" + order.getMaDonHang() + " đã hoàn tất thanh toán",
                order.getMaDonHang()
        );
        realtimeNotificationService.notifyPaymentCompleted(savedInvoice);
        realtimeNotificationService.notifyCustomerOrderChanged(order);
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

    /**
     * Tạo mã VietQR thông qua payOS cho đơn tại bàn. Giao dịch PENDING được lưu
     * để webhook có thể đối chiếu đúng đơn, số tiền, điểm sử dụng và nhân viên
     * đã mở thanh toán. Nếu cùng một yêu cầu còn hiệu lực thì trả lại QR cũ.
     */
    @Transactional
    public VietQrResponse createPayOsVietQr(Integer orderId,
                                            String phone,
                                            Integer pointsToUse,
                                            String username) {
        if (!payOsGatewayService.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình payOS cho thanh toán tự động"
            );
        }

        Order order = findPayableOrder(orderId);
        Employee employee = requireCashierOrAdmin(username);
        LoyaltyPreviewResponse preview = loyaltyService.preview(phone, pointsToUse, order.getTongTien());
        BigDecimal depositCredit = reservationService.depositCreditForOrder(order);
        BigDecimal payable = normalizedMoney(preview.tongThanhToan())
                .subtract(normalizedMoney(depositCredit).min(normalizedMoney(preview.tongThanhToan())))
                .max(BigDecimal.ZERO.setScale(2));
        long amount = toPayOsAmount(payable);
        if (amount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng không còn số tiền cần chuyển khoản"
            );
        }

        String normalizedPhone = trimToNull(preview.soDienThoai());
        int normalizedPoints = preview.diemSuDung() == null ? 0 : preview.diemSuDung();
        LocalDateTime now = LocalDateTime.now();

        List<PayOsPayment> pendingPayments = payOsPaymentRepository
                .findByDonHang_MaDonHangAndTrangThaiOrderByThoiGianTaoDesc(orderId, "PENDING");
        for (PayOsPayment pending : pendingPayments) {
            if (pending.getHetHanLuc() != null && !pending.getHetHanLuc().isAfter(now)) {
                pending.setTrangThai("EXPIRED");
                payOsPaymentRepository.save(pending);
                continue;
            }
            if (samePayOsRequest(pending, payable, normalizedPhone, normalizedPoints)
                    && trimToNull(pending.getQrCode()) != null) {
                return toPayOsVietQrResponse(pending);
            }

            // Không để nhiều QR còn hiệu lực cho cùng một đơn với số tiền/điểm khác nhau.
            payOsGatewayService.cancelPayment(pending.getPayOsOrderCode(), "Tạo yêu cầu thanh toán mới");
            pending.setTrangThai("CANCELLED");
            payOsPaymentRepository.save(pending);
        }

        LocalDateTime expiresAt = now.plusMinutes(payOsGatewayService.expireMinutes());
        PayOsPayment payment = new PayOsPayment();
        payment.setDonHang(order);
        payment.setNhanVienKhoiTao(employee);
        payment.setPayOsOrderCode(nextPayOsOrderCode());
        payment.setSoTien(normalizedMoney(payable));
        payment.setSoDienThoaiKhach(normalizedPhone);
        payment.setDiemSuDung(normalizedPoints);
        payment.setNoiDungChuyenKhoan(buildPayOsDescription(order.getMaDonHang()));
        payment.setTrangThai("PENDING");
        payment.setThoiGianTao(now);
        payment.setHetHanLuc(expiresAt);
        payOsPaymentRepository.saveAndFlush(payment);

        PayOsGatewayService.CreatePaymentResult created = payOsGatewayService.createPayment(
                payment.getPayOsOrderCode(),
                amount,
                payment.getNoiDungChuyenKhoan(),
                expiresAt
        );
        if (created.orderCode() != payment.getPayOsOrderCode() || created.amount() != amount) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "payOS trả về sai mã giao dịch hoặc số tiền");
        }

        payment.setPaymentLinkId(trimToNull(created.paymentLinkId()));
        payment.setBinNganHang(trimToNull(created.bin()));
        payment.setSoTaiKhoan(trimToNull(created.accountNumber()));
        payment.setTenTaiKhoan(trimToNull(created.accountName()));
        payment.setQrCode(trimToNull(created.qrCode()));
        payment.setCheckoutUrl(trimToNull(created.checkoutUrl()));
        if (trimToNull(created.description()) != null) {
            payment.setNoiDungChuyenKhoan(created.description().trim());
        }
        payOsPaymentRepository.saveAndFlush(payment);
        return toPayOsVietQrResponse(payment);
    }

    /** Đăng ký URL webhook đã cấu hình với payOS. Chỉ controller ADMIN gọi phương thức này. */
    public JsonNode registerPayOsWebhook() {
        return payOsGatewayService.registerWebhook();
    }

    /**
     * Nhận webhook payOS, xác minh chữ ký rồi tự động tạo hóa đơn và chuyển
     * trạng thái đơn sang DA_THANH_TOAN. Xử lý idempotent khi payOS gửi lại.
     */
    @Transactional
    public PayOsWebhookResponse handlePayOsWebhook(JsonNode webhookBody) {
        PayOsGatewayService.VerifiedWebhook webhook = payOsGatewayService.verifyWebhook(webhookBody);
        if (!webhook.isSuccessfulPayment()) {
            return new PayOsWebhookResponse(true, "Webhook không phải giao dịch thanh toán thành công");
        }
        if (webhook.orderCode() <= 0) {
            return new PayOsWebhookResponse(true, "Webhook mẫu đã được xác minh");
        }

        PayOsPayment payment = payOsPaymentRepository.findByPayOsOrderCodeForUpdate(webhook.orderCode())
                .orElse(null);
        if (payment == null) {
            // payOS gửi dữ liệu mẫu khi đăng ký webhook; vẫn trả 2xx để xác nhận endpoint hoạt động.
            return new PayOsWebhookResponse(true, "Webhook hợp lệ nhưng không thuộc giao dịch Lumora đang theo dõi");
        }
        if ("PAID".equals(normalizeText(payment.getTrangThai()))) {
            return new PayOsWebhookResponse(true, "Giao dịch đã được xử lý trước đó");
        }

        long expectedAmount = toPayOsAmount(payment.getSoTien());
        if (webhook.amount() != expectedAmount) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số tiền webhook payOS không khớp yêu cầu thanh toán");
        }
        String expectedLinkId = trimToNull(payment.getPaymentLinkId());
        String webhookLinkId = trimToNull(webhook.paymentLinkId());
        if (expectedLinkId != null && webhookLinkId != null && !expectedLinkId.equals(webhookLinkId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment link payOS không khớp giao dịch");
        }

        String reference = normalizeTransactionCode(webhook.reference());
        if (reference == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook payOS thiếu mã tham chiếu giao dịch");
        }
        if (payOsPaymentRepository.existsByMaThamChieuIgnoreCase(reference)
                && !reference.equalsIgnoreCase(trimToNull(payment.getMaThamChieu()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã tham chiếu payOS đã được sử dụng");
        }

        Invoice invoice = completePayOsTablePayment(payment, reference);
        payment.setTrangThai("PAID");
        payment.setMaThamChieu(reference);
        payment.setThoiGianThanhToan(invoice.getThoiGianThanhToan());
        payOsPaymentRepository.saveAndFlush(payment);
        return new PayOsWebhookResponse(true, "Đã tự động cập nhật thanh toán cho đơn #DH" + payment.getDonHang().getMaDonHang());
    }

    /** Tạo VietQR động kiểu cũ; giữ lại cho các luồng khác chưa chuyển sang payOS. */
    @Transactional(readOnly = true)
    public VietQrResponse createVietQr(Integer orderId) {
        return createVietQr(orderId, null, 0);
    }

    /** Tạo VietQR theo tổng tiền sau khi xem trước đổi điểm. */
    @Transactional(readOnly = true)
    public VietQrResponse createVietQr(Integer orderId, String phone, Integer pointsToUse) {
        Order order = findPayableOrder(orderId);
        LoyaltyPreviewResponse preview = loyaltyService.preview(phone, pointsToUse, order.getTongTien());
        BigDecimal depositCredit = reservationService.depositCreditForOrder(order);
        BigDecimal payable = normalizedMoney(preview.tongThanhToan())
                .subtract(normalizedMoney(depositCredit).min(normalizedMoney(preview.tongThanhToan())))
                .max(BigDecimal.ZERO.setScale(2));
        return buildVietQr(order, payable);
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

        BigDecimal depositCredit = reservationService.depositCreditForOrder(order);
        BigDecimal total = normalizedMoney(order.getTongTien());
        BigDecimal depositApplied = normalizedMoney(depositCredit).min(total);
        BigDecimal remainingPayable = total.subtract(depositApplied).max(BigDecimal.ZERO.setScale(2));

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
                total,
                depositApplied,
                remainingPayable,
                order.getTrangThai(),
                items,
                buildVietQr(order, remainingPayable),
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

    private Employee requireCashierOrAdmin(String username) {
        String normalizedUsername = trimToNull(username);
        if (normalizedUsername == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Không xác định được tài khoản đang đăng nhập"
            );
        }

        Employee employee = employeeRepository.findByTenDangNhap(normalizedUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy nhân viên từ tài khoản đăng nhập"
                ));
        String roleName = employee.getVaiTro() == null
                ? ""
                : normalizeText(employee.getVaiTro().getTenVaiTro()).replace("ROLE_", "");
        if (!Set.of("CASHIER", "ADMIN").contains(roleName)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ thu ngân hoặc quản trị viên được xác nhận giao hàng thành công"
            );
        }
        if (!"DANG_LAM_VIEC".equals(normalizeText(employee.getTrangThai()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhân viên hiện không còn làm việc");
        }
        return employee;
    }

    private BigDecimal deliveryFeeOf(Order order) {
        if (order == null || order.getGiaoHang() == null || order.getGiaoHang().getPhiGiaoHang() == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return normalizedMoney(order.getGiaoHang().getPhiGiaoHang());
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

        // Nhánh chuyển khoản hiện không được phép đi tới đây; giao dịch VietQR
        // được hoàn tất qua webhook payOS. Giữ giá trị này để không đổi cấu trúc hàm.
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

    private Invoice completePayOsTablePayment(PayOsPayment payment, String reference) {
        Integer orderId = payment.getDonHang().getMaDonHang();
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng: " + orderId));

        Invoice existing = invoiceRepository.findByDonHang_MaDonHang(orderId).orElse(null);
        if (existing != null) {
            String existingReference = trimToNull(existing.getMaGiaoDich());
            if (existingReference != null && existingReference.equalsIgnoreCase(reference)) {
                return existing;
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đơn hàng đã có hóa đơn khác trước khi webhook payOS được xử lý"
            );
        }

        orderPricingService.recalculate(order);
        ensurePayable(order);
        LoyaltyService.PreparedLoyalty loyalty = loyaltyService.prepareForPayment(
                payment.getSoDienThoaiKhach(),
                null,
                payment.getDiemSuDung(),
                order.getTongTien()
        );
        BigDecimal depositCredit = reservationService.depositCreditForOrder(order);
        BigDecimal depositApplied = normalizedMoney(depositCredit).min(normalizedMoney(loyalty.finalAmount()));
        BigDecimal remainingPayable = normalizedMoney(loyalty.finalAmount())
                .subtract(depositApplied)
                .max(BigDecimal.ZERO.setScale(2));
        if (remainingPayable.compareTo(normalizedMoney(payment.getSoTien())) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tổng tiền hiện tại của đơn không còn khớp với giao dịch payOS đã thanh toán"
            );
        }
        if (invoiceRepository.existsByMaGiaoDichIgnoreCase(reference)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã giao dịch đã được sử dụng");
        }
        reservationService.ensureTransactionCodeNotUsedByDeposit(reference);

        LocalDateTime paidAt = LocalDateTime.now();
        Invoice invoice = new Invoice();
        invoice.setDonHang(order);
        invoice.setNhanVien(payment.getNhanVienKhoiTao());
        invoice.setKhachHang(loyalty.customer());
        invoice.setTamTinh(normalizedMoney(order.getTamTinh()));
        invoice.setTienGiam(normalizedMoney(order.getTienGiam()));
        invoice.setTienCocDaKhauTru(depositApplied);
        invoice.setPhiGiaoHang(BigDecimal.ZERO.setScale(2));
        invoice.setDiemDaSuDung(loyalty.pointsUsed());
        invoice.setTienGiamTuDiem(loyalty.pointDiscount());
        invoice.setDiemDuocCong(loyalty.pointsEarned());
        invoice.setMaCodeKhuyenMai(order.getKhuyenMai() == null ? null : order.getKhuyenMai().getMaCode());
        invoice.setTongTien(normalizedMoney(loyalty.finalAmount()));
        invoice.setThoiGianTao(paidAt);
        invoice.setThoiGianThanhToan(paidAt);
        invoice.setPhuongThucThanhToan(METHOD_BANK_TRANSFER);
        invoice.setTrangThaiThanhToan("DA_THANH_TOAN");
        invoice.setTienKhachDua(null);
        invoice.setTienThua(BigDecimal.ZERO.setScale(2));
        invoice.setMaGiaoDich(reference);
        invoice.setGhiChu("Thanh toán tự động qua payOS");
        invoice.setNoiDungChuyenKhoan(payment.getNoiDungChuyenKhoan());

        Invoice savedInvoice = invoiceRepository.saveAndFlush(invoice);
        reservationService.applyDepositByOrder(order, depositApplied);

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
                "PAYOS_PAYMENT_COMPLETED",
                "Đơn hàng #DH" + savedOrder.getMaDonHang() + " được payOS xác nhận thanh toán tự động",
                savedOrder.getMaDonHang()
        );
        realtimeNotificationService.notifyPaymentCompleted(savedInvoice);
        realtimeNotificationService.notifyCustomerOrderChanged(savedOrder);
        realtimeNotificationService.notifyDashboardRefresh(savedInvoice);
        return savedInvoice;
    }

    private boolean samePayOsRequest(PayOsPayment payment,
                                     BigDecimal amount,
                                     String phone,
                                     int points) {
        return normalizedMoney(payment.getSoTien()).compareTo(normalizedMoney(amount)) == 0
                && Objects.equals(trimToNull(payment.getSoDienThoaiKhach()), trimToNull(phone))
                && (payment.getDiemSuDung() == null ? 0 : payment.getDiemSuDung()) == points;
    }

    private synchronized long nextPayOsOrderCode() {
        long candidate = System.currentTimeMillis();
        while (payOsPaymentRepository.existsByPayOsOrderCode(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private long toPayOsAmount(BigDecimal value) {
        try {
            return normalizedMoney(value).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền payOS phải là số nguyên hợp lệ");
        }
    }

    private String buildPayOsDescription(Integer orderId) {
        String raw = "DH" + orderId;
        if (raw.length() <= 9) {
            return raw;
        }
        String digits = String.valueOf(orderId);
        return "D" + digits.substring(Math.max(0, digits.length() - 8));
    }

    private VietQrResponse toPayOsVietQrResponse(PayOsPayment payment) {
        String qrCode = trimToNull(payment.getQrCode());
        if (qrCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "payOS không trả về dữ liệu QR");
        }
        String bankId = trimToNull(payment.getBinNganHang());
        if (bankId == null) {
            bankId = "PAYOS";
        }
        String bankName = trimToNull(vietQrProperties.getBankName());
        if (bankName == null) {
            bankName = bankId;
        }
        return new VietQrResponse(
                payment.getDonHang().getMaDonHang(),
                bankId,
                bankName,
                trimToNull(payment.getSoTaiKhoan()),
                trimToNull(payment.getTenTaiKhoan()),
                normalizedMoney(payment.getSoTien()).setScale(0, RoundingMode.UNNECESSARY),
                payment.getNoiDungChuyenKhoan(),
                "payos",
                qrCodeDataUrl(qrCode)
        );
    }

    private String qrCodeDataUrl(String qrCode) {
        try {
            var matrix = new QRCodeWriter().encode(qrCode, BarcodeFormat.QR_CODE, 320, 320);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo ảnh QR payOS", exception);
        }
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
