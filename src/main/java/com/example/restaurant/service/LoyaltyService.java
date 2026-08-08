package com.example.restaurant.service;

import com.example.restaurant.config.LoyaltyPolicyProperties;
import com.example.restaurant.dto.*;
import com.example.restaurant.entity.Customer;
import com.example.restaurant.entity.LoyaltyTransaction;
import com.example.restaurant.entity.Order;
import com.example.restaurant.repository.CustomerRepository;
import com.example.restaurant.repository.LoyaltyTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Service
public class LoyaltyService {
    private static final String STATUS_ACTIVE = "HOAT_DONG";
    private static final String TYPE_EARN = "EARN";
    private static final String TYPE_REDEEM = "REDEEM";
    private static final String TYPE_ADJUST = "ADJUST";

    private final CustomerRepository customerRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final LoyaltyPolicyProperties loyaltyPolicyProperties;

    public LoyaltyService(CustomerRepository customerRepository,
                          LoyaltyTransactionRepository loyaltyTransactionRepository,
                          LoyaltyPolicyProperties loyaltyPolicyProperties) {
        this.customerRepository = customerRepository;
        this.loyaltyTransactionRepository = loyaltyTransactionRepository;
        this.loyaltyPolicyProperties = loyaltyPolicyProperties;
    }

    @Transactional(readOnly = true)
    public LoyaltyPolicyResponse policy() {
        return new LoyaltyPolicyResponse(
                moneyPerEarnedPoint(),
                valuePerRedeemedPoint(),
                minimumRedeemPoints(),
                maximumRedeemRatio()
        );
    }

    @Transactional(readOnly = true)
    public LoyaltyCustomerResponse findByPhone(String rawPhone) {
        String phone = normalizePhone(rawPhone);
        Customer customer = customerRepository.findBySoDienThoai(phone)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy khách hàng theo số điện thoại"
                ));
        return toCustomerResponse(customer);
    }

    @Transactional(readOnly = true)
    public LoyaltyCustomerResponse findById(Integer customerId) {
        return toCustomerResponse(requireCustomer(customerId));
    }

    @Transactional(readOnly = true)
    public Page<LoyaltyCustomerResponse> findPage(int page, int size, String keyword) {
        Specification<Customer> specification = (root, query, cb) -> cb.conjunction();
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("hoTen")), pattern),
                    cb.like(cb.lower(root.get("soDienThoai")), pattern)
            ));
        }

        PageRequest pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("thoiGianCapNhat"), Sort.Order.desc("maKhachHang"))
        );
        return customerRepository.findAll(specification, pageable).map(this::toCustomerResponse);
    }

    @Transactional
    public LoyaltyCustomerResponse create(LoyaltyCustomerRequest request) {
        String phone = normalizePhone(request.soDienThoai());
        if (customerRepository.existsBySoDienThoai(phone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại đã được đăng ký");
        }

        Customer customer = new Customer();
        customer.setHoTen(normalizeName(request.hoTen(), null));
        customer.setSoDienThoai(phone);
        customer.setDiemTichLuy(0);
        customer.setTongChiTieu(BigDecimal.ZERO);
        customer.setTrangThai(STATUS_ACTIVE);
        return toCustomerResponse(customerRepository.saveAndFlush(customer));
    }

    @Transactional
    public LoyaltyCustomerResponse update(Integer customerId, LoyaltyCustomerRequest request) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy khách hàng: " + customerId
                ));
        String phone = normalizePhone(request.soDienThoai());
        customerRepository.findBySoDienThoai(phone).ifPresent(existing -> {
            if (!existing.getMaKhachHang().equals(customerId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Số điện thoại đã được đăng ký");
            }
        });

        customer.setHoTen(normalizeName(request.hoTen(), customer.getHoTen()));
        customer.setSoDienThoai(phone);
        return toCustomerResponse(customerRepository.saveAndFlush(customer));
    }

    @Transactional
    public LoyaltyCustomerResponse adjustPoints(Integer customerId, LoyaltyAdjustPointsRequest request) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy khách hàng: " + customerId
                ));
        int adjustment = request.soDiem();
        if (adjustment == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điểm điều chỉnh phải khác 0");
        }
        int newBalance = safeAdd(customer.getDiemTichLuy(), adjustment);
        if (newBalance < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không thể trừ vượt quá số điểm hiện có"
            );
        }

        customer.setDiemTichLuy(newBalance);
        Customer saved = customerRepository.saveAndFlush(customer);
        saveTransaction(
                saved,
                null,
                TYPE_ADJUST,
                adjustment,
                newBalance,
                request.lyDo().trim()
        );
        return toCustomerResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<LoyaltyTransactionResponse> transactionPage(Integer customerId, int page, int size) {
        requireCustomer(customerId);
        PageRequest pageable = PageRequest.of(normalizePage(page), normalizeSize(size));
        return loyaltyTransactionRepository
                .findByKhachHang_MaKhachHangOrderByThoiGianDescMaGiaoDichDiemDesc(customerId, pageable)
                .map(this::toTransactionResponse);
    }

    /**
     * Chuẩn bị khách hàng và khóa dòng điểm trong cùng transaction thanh toán.
     * Không có số điện thoại thì hóa đơn vẫn thanh toán bình thường nhưng không tích điểm.
     */
    @Transactional
    public PreparedLoyalty prepareForPayment(String rawPhone,
                                             String rawName,
                                             Integer requestedPoints,
                                             BigDecimal amountBeforePoints) {
        int points = requestedPoints == null ? 0 : requestedPoints;
        String phone = trimToNull(rawPhone);
        String name = trimToNull(rawName);

        if (phone == null) {
            if (points > 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Vui lòng nhập số điện thoại khách hàng để sử dụng điểm"
                );
            }
            if (name != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Họ tên khách hàng phải đi kèm số điện thoại"
                );
            }
            return calculate(null, points, amountBeforePoints, false);
        }

        String normalizedPhone = normalizePhone(phone);
        Customer customer = customerRepository.findBySoDienThoaiForUpdate(normalizedPhone)
                .orElseGet(() -> createCustomerForPayment(normalizedPhone, name));
        ensureActive(customer);

        if (name != null && isGeneratedCustomerName(customer.getHoTen())) {
            customer.setHoTen(normalizeName(name, customer.getHoTen()));
            customerRepository.save(customer);
        }
        return calculate(customer, points, amountBeforePoints, false);
    }

    @Transactional(readOnly = true)
    public LoyaltyPreviewResponse preview(String rawPhone,
                                          Integer requestedPoints,
                                          BigDecimal amountBeforePoints) {
        int points = requestedPoints == null ? 0 : requestedPoints;
        String phone = trimToNull(rawPhone);
        Customer customer = null;
        boolean newCustomer = false;
        String normalizedPhone = null;

        if (phone != null) {
            normalizedPhone = normalizePhone(phone);
            customer = customerRepository.findBySoDienThoai(normalizedPhone).orElse(null);
            newCustomer = customer == null;
            if (customer != null) {
                ensureActive(customer);
            }
        } else if (points > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập số điện thoại khách hàng để sử dụng điểm"
            );
        }

        PreparedLoyalty calculation = calculate(customer, points, amountBeforePoints, newCustomer);
        return new LoyaltyPreviewResponse(
                customer == null ? null : customer.getMaKhachHang(),
                customer == null ? null : customer.getHoTen(),
                normalizedPhone,
                newCustomer,
                calculation.pointsBefore(),
                minimumRedeemPoints(),
                calculation.maximumUsablePoints(),
                calculation.pointsUsed(),
                valuePerRedeemedPoint(),
                calculation.amountBeforePoints(),
                calculation.pointDiscount(),
                calculation.finalAmount(),
                calculation.pointsEarned(),
                calculation.pointsAfterPayment()
        );
    }

    /** Ghi sổ điểm sau khi hóa đơn và đơn hàng đã được xác nhận trong cùng transaction. */
    @Transactional
    public void completePayment(PreparedLoyalty calculation, Order paidOrder) {
        Customer customer = calculation.customer();
        if (customer == null) {
            return;
        }
        Integer orderId = paidOrder.getMaDonHang();
        if (loyaltyTransactionRepository.existsByDonHang_MaDonHangAndLoaiGiaoDich(orderId, TYPE_EARN)
                || loyaltyTransactionRepository.existsByDonHang_MaDonHangAndLoaiGiaoDich(orderId, TYPE_REDEEM)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng đã được xử lý tích điểm");
        }

        int balance = calculation.pointsBefore();
        if (calculation.pointsUsed() > 0) {
            balance -= calculation.pointsUsed();
            saveTransaction(
                    customer,
                    paidOrder,
                    TYPE_REDEEM,
                    -calculation.pointsUsed(),
                    balance,
                    "Sử dụng điểm cho đơn #DH" + orderId
            );
        }
        if (calculation.pointsEarned() > 0) {
            balance = safeAdd(balance, calculation.pointsEarned());
            saveTransaction(
                    customer,
                    paidOrder,
                    TYPE_EARN,
                    calculation.pointsEarned(),
                    balance,
                    "Cộng điểm từ đơn #DH" + orderId
            );
        }

        customer.setDiemTichLuy(balance);
        customer.setTongChiTieu(money(customer.getTongChiTieu().add(calculation.finalAmount())));
        customerRepository.saveAndFlush(customer);
    }

    private PreparedLoyalty calculate(Customer customer,
                                      int requestedPoints,
                                      BigDecimal amountBeforePoints,
                                      boolean newCustomer) {
        if (requestedPoints < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điểm sử dụng không được âm");
        }
        BigDecimal baseAmount = money(amountBeforePoints);
        if (baseAmount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tổng tiền đơn hàng không hợp lệ");
        }

        int availablePoints = customer == null ? 0 : customer.getDiemTichLuy();
        int maximumByRatio = baseAmount.multiply(maximumRedeemRatio())
                .divide(valuePerRedeemedPoint(), 0, RoundingMode.FLOOR)
                .intValue();
        int maximumByAmount = baseAmount
                .divide(valuePerRedeemedPoint(), 0, RoundingMode.FLOOR)
                .intValue();
        int maximumUsableRaw = Math.min(availablePoints, Math.min(maximumByRatio, maximumByAmount));
        int maximumUsable = maximumUsableRaw >= minimumRedeemPoints() ? maximumUsableRaw : 0;

        if (requestedPoints > 0 && customer == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    newCustomer
                            ? "Khách hàng mới chưa có điểm để sử dụng"
                            : "Không tìm thấy khách hàng để sử dụng điểm"
            );
        }
        if (requestedPoints > 0 && requestedPoints < minimumRedeemPoints()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cần sử dụng tối thiểu " + minimumRedeemPoints() + " điểm"
            );
        }
        if (requestedPoints > availablePoints) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điểm sử dụng vượt quá điểm hiện có");
        }
        if (requestedPoints > maximumUsable) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hóa đơn này chỉ được sử dụng tối đa " + maximumUsable + " điểm"
            );
        }

        BigDecimal pointDiscount = valuePerRedeemedPoint()
                .multiply(BigDecimal.valueOf(requestedPoints));
        BigDecimal finalAmount = money(baseAmount.subtract(pointDiscount));
        boolean eligibleToEarn = customer != null || newCustomer;
        int earnedPoints = eligibleToEarn
                ? finalAmount.divide(moneyPerEarnedPoint(), 0, RoundingMode.FLOOR).intValue()
                : 0;
        int pointsAfter = safeAdd(availablePoints - requestedPoints, earnedPoints);

        return new PreparedLoyalty(
                customer,
                availablePoints,
                maximumUsable,
                requestedPoints,
                money(pointDiscount),
                baseAmount,
                finalAmount,
                earnedPoints,
                pointsAfter
        );
    }

    private Customer createCustomerForPayment(String phone, String name) {
        Customer customer = new Customer();
        customer.setSoDienThoai(phone);
        customer.setHoTen(normalizeName(name, "Khách hàng " + phone.substring(phone.length() - 4)));
        customer.setDiemTichLuy(0);
        customer.setTongChiTieu(BigDecimal.ZERO);
        customer.setTrangThai(STATUS_ACTIVE);
        return customerRepository.saveAndFlush(customer);
    }

    private void saveTransaction(Customer customer,
                                 Order order,
                                 String type,
                                 int points,
                                 int balanceAfter,
                                 String content) {
        LoyaltyTransaction transaction = new LoyaltyTransaction();
        transaction.setKhachHang(customer);
        transaction.setDonHang(order);
        transaction.setLoaiGiaoDich(type);
        transaction.setSoDiem(points);
        transaction.setSoDuSauGiaoDich(balanceAfter);
        transaction.setNoiDung(content);
        loyaltyTransactionRepository.save(transaction);
    }

    private Customer requireCustomer(Integer customerId) {
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã khách hàng không hợp lệ");
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy khách hàng: " + customerId
                ));
    }

    private void ensureActive(Customer customer) {
        if (!STATUS_ACTIVE.equalsIgnoreCase(customer.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Khách hàng đã ngừng hoạt động");
        }
    }

    private LoyaltyCustomerResponse toCustomerResponse(Customer customer) {
        return new LoyaltyCustomerResponse(
                customer.getMaKhachHang(),
                customer.getHoTen(),
                customer.getSoDienThoai(),
                customer.getDiemTichLuy(),
                money(customer.getTongChiTieu()),
                customer.getTrangThai(),
                customer.getThoiGianTao(),
                customer.getThoiGianCapNhat()
        );
    }

    private LoyaltyTransactionResponse toTransactionResponse(LoyaltyTransaction transaction) {
        return new LoyaltyTransactionResponse(
                transaction.getMaGiaoDichDiem(),
                transaction.getKhachHang().getMaKhachHang(),
                transaction.getDonHang() == null ? null : transaction.getDonHang().getMaDonHang(),
                transaction.getLoaiGiaoDich(),
                transaction.getSoDiem(),
                transaction.getSoDuSauGiaoDich(),
                transaction.getNoiDung(),
                transaction.getThoiGian()
        );
    }

    private String normalizePhone(String rawPhone) {
        String value = trimToNull(rawPhone);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điện thoại không được để trống");
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        if (!digits.matches("0\\d{9}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0"
            );
        }
        return digits;
    }

    private boolean isGeneratedCustomerName(String name) {
        String value = trimToNull(name);
        return value == null || value.startsWith("Khách hàng ");
    }

    private String normalizeName(String rawName, String fallback) {
        String name = trimToNull(rawName);
        if (name == null) {
            name = trimToNull(fallback);
        }
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ tên khách hàng không được để trống");
        }
        if (name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ tên khách hàng tối đa 100 ký tự");
        }
        return name.replaceAll("\\s+", " ").trim();
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private int safeAdd(int left, int right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số điểm vượt giới hạn cho phép");
        }
    }

    private BigDecimal moneyPerEarnedPoint() {
        return loyaltyPolicyProperties.getMoneyPerEarnedPoint();
    }

    private BigDecimal valuePerRedeemedPoint() {
        return loyaltyPolicyProperties.getValuePerRedeemedPoint();
    }

    private int minimumRedeemPoints() {
        return loyaltyPolicyProperties.getMinimumRedeemPoints();
    }

    private BigDecimal maximumRedeemRatio() {
        return loyaltyPolicyProperties.getMaximumRedeemRatio();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record PreparedLoyalty(
            Customer customer,
            int pointsBefore,
            int maximumUsablePoints,
            int pointsUsed,
            BigDecimal pointDiscount,
            BigDecimal amountBeforePoints,
            BigDecimal finalAmount,
            int pointsEarned,
            int pointsAfterPayment
    ) {
    }
}
