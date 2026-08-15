package com.example.restaurant.service;

import com.example.restaurant.dto.IngredientBatchRequest;
import com.example.restaurant.dto.IngredientBatchResponse;
import com.example.restaurant.dto.IngredientBatchStatisticsResponse;
import com.example.restaurant.dto.IngredientBatchUpdateRequest;
import com.example.restaurant.dto.IngredientRequest;
import com.example.restaurant.dto.IngredientResponse;
import com.example.restaurant.dto.InventoryStatisticsResponse;
import com.example.restaurant.dto.InventoryStockRequest;
import com.example.restaurant.dto.InventoryTransactionResponse;
import com.example.restaurant.dto.InventoryWasteReasonResponse;
import com.example.restaurant.dto.InventoryWasteReasonStatisticsResponse;
import com.example.restaurant.dto.InventoryWasteRequest;
import com.example.restaurant.dto.InventoryWasteResponse;
import com.example.restaurant.dto.InventoryWasteStatisticsResponse;
import com.example.restaurant.entity.Ingredient;
import com.example.restaurant.entity.IngredientBatch;
import com.example.restaurant.entity.InventoryTransaction;
import com.example.restaurant.repository.IngredientBatchRepository;
import com.example.restaurant.repository.IngredientRepository;
import com.example.restaurant.repository.InventoryTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class InventoryService {
    public static final String IMPORT = "NHAP";
    public static final String EXPORT = "XUAT";
    public static final String ADJUSTMENT = "DIEU_CHINH";
    public static final String WASTE = "TIEU_HUY";

    public static final String VALID = "CON_HAN";
    public static final String EXPIRING_SOON = "SAP_HET_HAN";
    public static final String EXPIRED = "HET_HAN";
    public static final String USED_UP = "DA_DUNG_HET";
    public static final String NOT_TRACKED = "KHONG_THEO_DOI";

    public static final String SAFETY_SAFE = "AN_TOAN";

    private static final int DEFAULT_EXPIRY_WARNING_DAYS = 3;
    private static final String WASTE_OTHER = "KHAC";
    private static final String WASTE_EXPIRED = "QUA_HAN_SU_DUNG";
    private static final Map<String, String> WASTE_REASONS = buildWasteReasons();

    private final IngredientRepository ingredientRepository;
    private final IngredientBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final SystemActivityService systemActivityService;
    private final AdminNotificationService adminNotificationService;

    public InventoryService(IngredientRepository ingredientRepository,
                            IngredientBatchRepository batchRepository,
                            InventoryTransactionRepository transactionRepository,
                            SystemActivityService systemActivityService,
                            AdminNotificationService adminNotificationService) {
        this.ingredientRepository = ingredientRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.systemActivityService = systemActivityService;
        this.adminNotificationService = adminNotificationService;
    }

    @Transactional(readOnly = true)
    public List<IngredientResponse> findAll(Boolean active) {
        Specification<Ingredient> specification = (root, query, cb) -> cb.conjunction();
        if (active != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("trangThai"), active));
        }
        return ingredientRepository.findAll(specification, Sort.by(Sort.Order.asc("tenNguyenLieu")))
                .stream()
                .map(this::toIngredientResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<IngredientResponse> findPage(int page, int size, String keyword,
                                             Boolean active, String stockStatus) {
        Specification<Ingredient> specification = (root, query, cb) -> cb.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.<String>get("tenNguyenLieu")), pattern),
                    cb.like(cb.lower(root.<String>get("donViTinh")), pattern),
                    cb.like(cb.lower(root.<String>get("moTa")), pattern)
            ));
        }
        if (active != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("trangThai"), active));
        }
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        Sort sort = Sort.by(Sort.Order.desc("ngayCapNhat"), Sort.Order.desc("maNguyenLieu"));
        PageRequest pageable = PageRequest.of(normalizedPage, normalizedSize, sort);

        if (stockStatus == null || stockStatus.isBlank()) {
            return ingredientRepository.findAll(specification, pageable).map(this::toIngredientResponse);
        }

        String normalizedStatus = normalizeStockStatus(stockStatus);
        List<IngredientResponse> filtered = ingredientRepository.findAll(specification, sort)
                .stream()
                .map(this::toIngredientResponse)
                .filter(item -> normalizedStatus.equals(item.trangThaiTonKho()))
                .toList();
        int fromIndex = Math.min(normalizedPage * normalizedSize, filtered.size());
        int toIndex = Math.min(fromIndex + normalizedSize, filtered.size());
        return new PageImpl<>(filtered.subList(fromIndex, toIndex), pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public IngredientResponse findById(Integer id) {
        return toIngredientResponse(requireIngredient(id));
    }

    @Transactional(readOnly = true)
    public List<IngredientResponse> findLowStock() {
        return ingredientRepository.findAll(
                        (root, query, cb) -> cb.equal(root.get("trangThai"), true),
                        Sort.by(Sort.Order.asc("tenNguyenLieu")))
                .stream()
                .map(this::toIngredientResponse)
                .filter(item -> !"CON_HANG".equals(item.trangThaiTonKho()))
                .sorted(Comparator
                        .comparing(IngredientResponse::soLuongKhaDung)
                        .thenComparing(IngredientResponse::tenNguyenLieu, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public IngredientResponse create(IngredientRequest request, String username) {
        String name = cleanRequired(request.tenNguyenLieu());
        validateUniqueName(name, null);

        Ingredient ingredient = new Ingredient();
        ingredient.setTenNguyenLieu(name);
        ingredient.setDonViTinh(cleanRequired(request.donViTinh()));
        ingredient.setSoLuongTon(nonNegativeOrZero(request.soLuongTon(), "Số lượng tồn"));
        ingredient.setMucTonToiThieu(nonNegativeOrZero(request.mucTonToiThieu(), "Mức tồn tối thiểu"));
        ingredient.setGiaNhap(nonNegativeOrNull(request.giaNhap(), "Giá nhập"));
        ingredient.setMoTa(cleanOptional(request.moTa()));
        ingredient.setTrangThai(request.trangThai() == null || request.trangThai());
        Ingredient saved = ingredientRepository.saveAndFlush(ingredient);

        if (saved.getSoLuongTon().compareTo(BigDecimal.ZERO) > 0) {
            recordTransaction(saved, null, IMPORT, saved.getSoLuongTon(), BigDecimal.ZERO,
                    saved.getSoLuongTon(), saved.getGiaNhap(), "Tồn kho ban đầu", username);
        }
        systemActivityService.record(
                "KHO_NGUYEN_LIEU",
                "Thêm nguyên liệu " + saved.getTenNguyenLieu(),
                saved.getMaNguyenLieu()
        );
        adminNotificationService.evaluateStock(saved, null);
        return toIngredientResponse(saved);
    }

    @Transactional
    public IngredientResponse update(Integer id, IngredientRequest request, String username) {
        Ingredient ingredient = requireIngredientForUpdate(id);
        String previousStockStatus = calculateStockStatus(ingredient);
        String name = cleanRequired(request.tenNguyenLieu());
        validateUniqueName(name, id);

        BigDecimal before = safeQuantity(ingredient.getSoLuongTon());
        BigDecimal after = request.soLuongTon() == null
                ? before
                : nonNegativeOrZero(request.soLuongTon(), "Số lượng tồn");
        BigDecimal batchStock = sumBatchRemaining(id);
        if (after.compareTo(batchStock) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tồn kho tổng không được nhỏ hơn số lượng đang được theo dõi theo lô ("
                            + strip(batchStock) + " " + ingredient.getDonViTinh() + ")");
        }

        ingredient.setTenNguyenLieu(name);
        ingredient.setDonViTinh(cleanRequired(request.donViTinh()));
        ingredient.setMucTonToiThieu(request.mucTonToiThieu() == null
                ? ingredient.getMucTonToiThieu()
                : nonNegativeOrZero(request.mucTonToiThieu(), "Mức tồn tối thiểu"));
        ingredient.setGiaNhap(nonNegativeOrNull(request.giaNhap(), "Giá nhập"));
        ingredient.setMoTa(cleanOptional(request.moTa()));
        ingredient.setSoLuongTon(after);
        if (request.trangThai() != null) {
            ingredient.setTrangThai(request.trangThai());
        }
        Ingredient saved = ingredientRepository.saveAndFlush(ingredient);

        if (before.compareTo(after) != 0) {
            recordTransaction(saved, null, ADJUSTMENT, after.subtract(before).abs(), before, after,
                    saved.getGiaNhap(), "Điều chỉnh phần tồn chưa theo dõi theo lô khi cập nhật nguyên liệu", username);
        }
        systemActivityService.record(
                "KHO_NGUYEN_LIEU",
                "Cập nhật nguyên liệu " + saved.getTenNguyenLieu(),
                saved.getMaNguyenLieu()
        );
        adminNotificationService.evaluateStock(saved, previousStockStatus);
        return toIngredientResponse(saved);
    }

    @Transactional
    public IngredientResponse deactivate(Integer id) {
        Ingredient ingredient = requireIngredientForUpdate(id);
        String previousStockStatus = calculateStockStatus(ingredient);
        if (Boolean.TRUE.equals(ingredient.getTrangThai())) {
            ingredient.setTrangThai(false);
            ingredientRepository.saveAndFlush(ingredient);
            systemActivityService.record(
                    "KHO_NGUYEN_LIEU",
                    "Ngừng sử dụng nguyên liệu " + ingredient.getTenNguyenLieu(),
                    ingredient.getMaNguyenLieu()
            );
        }
        adminNotificationService.evaluateStock(ingredient, previousStockStatus);
        return toIngredientResponse(ingredient);
    }

    @Transactional
    public IngredientResponse updateStock(Integer id, InventoryStockRequest request, String username) {
        Ingredient ingredient = requireIngredientForUpdate(id);
        if (!Boolean.TRUE.equals(ingredient.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Nguyên liệu đã ngừng sử dụng, không thể cập nhật tồn kho");
        }

        String previousStockStatus = calculateStockStatus(ingredient);
        String type = normalizeTransactionType(request.loaiGiaoDich());
        BigDecimal quantity = nonNegativeOrZero(request.soLuong(), "Số lượng");
        boolean notificationHandledByBatchCreation = IMPORT.equals(type)
                && request.maLo() == null
                && hasBatchInformation(request);
        IngredientResponse result = switch (type) {
            case IMPORT -> importStock(ingredient, request, quantity, username);
            case EXPORT -> exportStock(ingredient, request, quantity, username);
            case ADJUSTMENT -> adjustStock(ingredient, request, quantity, username);
            case WASTE -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng dùng API tiêu hủy/hao hụt để ghi nhận giao dịch TIEU_HUY");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Loại giao dịch kho không hợp lệ");
        };
        if (!notificationHandledByBatchCreation) {
            adminNotificationService.evaluateStock(ingredient, previousStockStatus);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<InventoryWasteReasonResponse> findWasteReasons() {
        return WASTE_REASONS.entrySet().stream()
                .map(entry -> new InventoryWasteReasonResponse(
                        entry.getKey(),
                        entry.getValue(),
                        WASTE_OTHER.equals(entry.getKey()),
                        WASTE_EXPIRED.equals(entry.getKey())
                ))
                .toList();
    }

    @Transactional
    public InventoryWasteResponse disposeBatch(Long batchId, InventoryWasteRequest request,
                                               String username, int warningDays) {
        IngredientBatch reference = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy lô nguyên liệu: " + batchId));
        return recordWasteInternal(
                reference.getNguyenLieu().getMaNguyenLieu(),
                batchId,
                request,
                username,
                warningDays
        );
    }

    @Transactional
    public InventoryWasteResponse recordWaste(Integer ingredientId, InventoryWasteRequest request,
                                              String username, int warningDays) {
        return recordWasteInternal(
                ingredientId,
                request.maLo(),
                request,
                username,
                warningDays
        );
    }

    private InventoryWasteResponse recordWasteInternal(Integer ingredientId, Long batchId,
                                                        InventoryWasteRequest request, String username,
                                                        int warningDays) {
        Ingredient ingredient = requireIngredientForUpdate(ingredientId);
        String reasonCode = normalizeWasteReason(request.maLyDo());
        String note = cleanOptional(request.ghiChu());
        if (WASTE_OTHER.equals(reasonCode) && note == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập ghi chú khi chọn lý do khác");
        }

        BigDecimal quantity = positive(request.soLuong(), "Số lượng tiêu hủy");
        String previousStockStatus = calculateStockStatus(ingredient);
        BigDecimal physicalBefore = safeQuantity(ingredient.getSoLuongTon());
        if (physicalBefore.compareTo(quantity) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Số lượng tiêu hủy vượt quá tồn vật lý hiện tại");
        }

        IngredientBatch batch = null;
        BigDecimal unitPrice = ingredient.getGiaNhap();
        if (batchId != null) {
            batch = requireBatchForUpdate(batchId);
            validateBatchBelongsToIngredient(batch, ingredient);
            BigDecimal batchBefore = safeQuantity(batch.getSoLuongConLai());
            if (batchBefore.compareTo(quantity) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Lô " + batch.getSoLo() + " chỉ còn " + strip(batchBefore) + " "
                                + ingredient.getDonViTinh());
            }
            if (WASTE_EXPIRED.equals(reasonCode) && !isExpired(batch)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Lô " + batch.getSoLo() + " chưa hết hạn. Hãy chọn lý do tiêu hủy phù hợp");
            }
            batch.setSoLuongConLai(batchBefore.subtract(quantity));
            batchRepository.save(batch);
            unitPrice = batch.getDonGiaNhap() == null ? ingredient.getGiaNhap() : batch.getDonGiaNhap();
        } else {
            if (WASTE_EXPIRED.equals(reasonCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tiêu hủy do quá hạn phải chọn một lô nguyên liệu");
            }
            BigDecimal trackedStock = sumBatchRemaining(ingredientId);
            BigDecimal untrackedStock = physicalBefore.subtract(trackedStock).max(BigDecimal.ZERO);
            if (untrackedStock.compareTo(quantity) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Phần tồn chưa theo dõi theo lô chỉ còn " + strip(untrackedStock) + " "
                                + ingredient.getDonViTinh() + ". Hãy chọn lô cần tiêu hủy");
            }
        }

        BigDecimal physicalAfter = physicalBefore.subtract(quantity);
        ingredient.setSoLuongTon(physicalAfter);
        Ingredient savedIngredient = ingredientRepository.saveAndFlush(ingredient);
        IngredientBatch savedBatch = batch == null ? null : batchRepository.saveAndFlush(batch);
        String reasonLabel = WASTE_REASONS.get(reasonCode);
        String displayReason = note == null ? reasonLabel : reasonLabel + ": " + note;
        InventoryTransaction transaction = recordTransaction(
                savedIngredient, savedBatch, WASTE, quantity, physicalBefore, physicalAfter,
                unitPrice, displayReason, reasonCode, note, username
        );

        systemActivityService.record(
                "KHO_NGUYEN_LIEU",
                "Tiêu hủy " + strip(quantity) + " " + savedIngredient.getDonViTinh() + " "
                        + savedIngredient.getTenNguyenLieu()
                        + (savedBatch == null ? "" : " - lô " + savedBatch.getSoLo())
                        + " (" + reasonLabel + ")",
                savedIngredient.getMaNguyenLieu()
        );
        adminNotificationService.evaluateStock(savedIngredient, previousStockStatus);
        return new InventoryWasteResponse(
                toTransactionResponse(transaction),
                toIngredientResponse(savedIngredient),
                savedBatch == null ? null : toBatchResponse(savedBatch, normalizeWarningDays(warningDays))
        );
    }

    @Transactional(readOnly = true)
    public InventoryWasteStatisticsResponse wasteStatistics(Integer ingredientId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        Specification<InventoryTransaction> specification = (root, query, cb) ->
                cb.equal(root.get("loaiGiaoDich"), WASTE);
        if (ingredientId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("nguyenLieu").get("maNguyenLieu"), ingredientId));
        }
        if (from != null) {
            LocalDateTime start = from.atStartOfDay();
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.<LocalDateTime>get("thoiGian"), start));
        }
        if (to != null) {
            LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
            specification = specification.and((root, query, cb) ->
                    cb.lessThan(root.<LocalDateTime>get("thoiGian"), endExclusive));
        }

        List<InventoryTransaction> transactions = transactionRepository.findAll(specification);
        Set<Integer> ingredientIds = new HashSet<>();
        Set<Long> batchIds = new HashSet<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        for (InventoryTransaction transaction : transactions) {
            ingredientIds.add(transaction.getNguyenLieu().getMaNguyenLieu());
            if (transaction.getLoNguyenLieu() != null) {
                batchIds.add(transaction.getLoNguyenLieu().getMaLo());
            }
            String code = transaction.getMaLyDo() == null
                    ? WASTE_OTHER
                    : normalizeWasteReasonOrOther(transaction.getMaLyDo());
            BigDecimal value = safeQuantity(transaction.getSoLuong())
                    .multiply(safeQuantity(transaction.getDonGiaNhap()));
            counts.merge(code, 1L, Long::sum);
            values.merge(code, value, BigDecimal::add);
            totalValue = totalValue.add(value);
        }

        List<InventoryWasteReasonStatisticsResponse> byReason = new ArrayList<>();
        for (Map.Entry<String, String> reason : WASTE_REASONS.entrySet()) {
            long count = counts.getOrDefault(reason.getKey(), 0L);
            if (count == 0) {
                continue;
            }
            byReason.add(new InventoryWasteReasonStatisticsResponse(
                    reason.getKey(),
                    reason.getValue(),
                    count,
                    values.getOrDefault(reason.getKey(), BigDecimal.ZERO)
            ));
        }
        return new InventoryWasteStatisticsResponse(
                transactions.size(),
                ingredientIds.size(),
                batchIds.size(),
                totalValue,
                byReason
        );
    }

    @Transactional(readOnly = true)
    public Page<InventoryTransactionResponse> findTransactionPage(
            int page, int size, Integer ingredientId, Long batchId,
            String type, LocalDate from, LocalDate to) {
        validateDateRange(from, to);

        Specification<InventoryTransaction> specification = (root, query, cb) -> cb.conjunction();
        if (ingredientId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("nguyenLieu").get("maNguyenLieu"), ingredientId));
        }
        if (batchId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("loNguyenLieu").get("maLo"), batchId));
        }
        if (type != null && !type.isBlank()) {
            String normalizedType = normalizeTransactionType(type);
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("loaiGiaoDich"), normalizedType));
        }
        if (from != null) {
            LocalDateTime start = from.atStartOfDay();
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.<LocalDateTime>get("thoiGian"), start));
        }
        if (to != null) {
            LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
            specification = specification.and((root, query, cb) ->
                    cb.lessThan(root.<LocalDateTime>get("thoiGian"), endExclusive));
        }

        PageRequest pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Order.desc("thoiGian"), Sort.Order.desc("maGiaoDich"))
        );
        return transactionRepository.findAll(specification, pageable).map(this::toTransactionResponse);
    }

    @Transactional(readOnly = true)
    public InventoryStatisticsResponse statistics() {
        List<Ingredient> ingredients = ingredientRepository.findAll();
        long active = 0;
        long lowStock = 0;
        long outOfStock = 0;
        long ingredientsPendingDisposal = 0;
        long batchesPendingDisposal = 0;
        BigDecimal physicalValue = BigDecimal.ZERO;
        BigDecimal usableValue = BigDecimal.ZERO;
        BigDecimal pendingDisposalValue = BigDecimal.ZERO;

        for (Ingredient ingredient : ingredients) {
            if (!Boolean.TRUE.equals(ingredient.getTrangThai())) {
                continue;
            }
            StockBreakdown breakdown = calculateStockBreakdown(ingredient);
            physicalValue = physicalValue.add(breakdown.physicalValue());
            usableValue = usableValue.add(breakdown.usableValue());
            pendingDisposalValue = pendingDisposalValue.add(breakdown.pendingDisposalValue());
            if (breakdown.pendingDisposal().compareTo(BigDecimal.ZERO) > 0) {
                ingredientsPendingDisposal++;
            }
            batchesPendingDisposal += breakdown.expiredBatchCount();
            active++;
            String status = calculateStockStatus(ingredient, breakdown.usable());
            if ("SAP_HET".equals(status)) {
                lowStock++;
            } else if ("HET_HANG".equals(status)) {
                outOfStock++;
            }
        }
        return new InventoryStatisticsResponse(
                ingredients.size(),
                active,
                lowStock,
                outOfStock,
                physicalValue,
                usableValue,
                pendingDisposalValue,
                ingredientsPendingDisposal,
                batchesPendingDisposal
        );
    }

    @Transactional(readOnly = true)
    public Page<IngredientBatchResponse> findBatchPage(
            int page, int size, String keyword, Integer ingredientId, Boolean active,
            String expiryStatus, LocalDate from, LocalDate to, int warningDays) {
        validateDateRange(from, to);
        int normalizedWarningDays = normalizeWarningDays(warningDays);
        LocalDate today = LocalDate.now();
        LocalDate warningEnd = today.plusDays(normalizedWarningDays);

        Specification<IngredientBatch> specification = (root, query, cb) -> cb.conjunction();
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.<String>get("soLo")), pattern),
                    cb.like(cb.lower(root.<String>get("nhaCungCap")), pattern),
                    cb.like(cb.lower(root.get("nguyenLieu").<String>get("tenNguyenLieu")), pattern)
            ));
        }
        if (ingredientId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("nguyenLieu").get("maNguyenLieu"), ingredientId));
        }
        if (active != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("trangThai"), active));
        }
        if (expiryStatus != null && !expiryStatus.isBlank()) {
            specification = specification.and(expiryStatusSpecification(
                    normalizeExpiryStatus(expiryStatus), today, warningEnd));
        }
        if (from != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.<LocalDate>get("hanSuDung"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.<LocalDate>get("hanSuDung"), to));
        }

        PageRequest pageable = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                Sort.by(Sort.Order.asc("hanSuDung"), Sort.Order.desc("ngayNhap"), Sort.Order.desc("maLo"))
        );
        return batchRepository.findAll(specification, pageable)
                .map(batch -> toBatchResponse(batch, normalizedWarningDays));
    }

    @Transactional(readOnly = true)
    public List<IngredientBatchResponse> findBatchesByIngredient(Integer ingredientId, int warningDays) {
        requireIngredient(ingredientId);
        int normalizedWarningDays = normalizeWarningDays(warningDays);
        return batchRepository.findAllByIngredientForFefo(ingredientId)
                .stream()
                .map(batch -> toBatchResponse(batch, normalizedWarningDays))
                .toList();
    }

    @Transactional(readOnly = true)
    public IngredientBatchResponse findBatchById(Long batchId, int warningDays) {
        IngredientBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy lô nguyên liệu: " + batchId));
        return toBatchResponse(batch, normalizeWarningDays(warningDays));
    }

    @Transactional
    public IngredientBatchResponse createBatch(Integer ingredientId, IngredientBatchRequest request,
                                                String username, int warningDays) {
        Ingredient ingredient = requireIngredientForUpdate(ingredientId);
        ensureIngredientActive(ingredient);
        String previousStockStatus = calculateStockStatus(ingredient);

        String batchNumber = cleanRequired(request.soLo());
        validateUniqueBatchNumber(ingredientId, batchNumber, null);
        LocalDate importDate = request.ngayNhap() == null ? LocalDate.now() : request.ngayNhap();
        validateBatchDates(importDate, request.ngaySanXuat(), request.hanSuDung(), true);
        BigDecimal quantity = positive(request.soLuongNhap(), "Số lượng nhập");
        BigDecimal price = request.donGiaNhap() == null
                ? ingredient.getGiaNhap()
                : nonNegativeOrNull(request.donGiaNhap(), "Đơn giá nhập");

        BigDecimal before = safeQuantity(ingredient.getSoLuongTon());
        BigDecimal after = before.add(quantity);
        ingredient.setSoLuongTon(after);
        if (request.donGiaNhap() != null) {
            ingredient.setGiaNhap(price);
        }
        ingredientRepository.saveAndFlush(ingredient);

        IngredientBatch batch = new IngredientBatch();
        batch.setNguyenLieu(ingredient);
        batch.setSoLo(batchNumber);
        batch.setNgayNhap(importDate);
        batch.setNgaySanXuat(request.ngaySanXuat());
        batch.setHanSuDung(request.hanSuDung());
        batch.setSoLuongBanDau(quantity);
        batch.setSoLuongConLai(quantity);
        batch.setDonGiaNhap(price);
        batch.setNhaCungCap(cleanOptional(request.nhaCungCap()));
        batch.setTrangThai(true);
        IngredientBatch savedBatch = batchRepository.saveAndFlush(batch);

        String reason = cleanOptional(request.ghiChu());
        if (reason == null) {
            reason = "Nhập lô " + batchNumber;
        }
        recordTransaction(ingredient, savedBatch, IMPORT, quantity, before, after, price, reason, username);
        systemActivityService.record(
                "KHO_NGUYEN_LIEU",
                "Nhập lô " + batchNumber + " - " + strip(quantity) + " "
                        + ingredient.getDonViTinh() + " " + ingredient.getTenNguyenLieu(),
                ingredient.getMaNguyenLieu()
        );
        adminNotificationService.evaluateStock(ingredient, previousStockStatus);
        return toBatchResponse(savedBatch, normalizeWarningDays(warningDays));
    }

    @Transactional
    public IngredientBatchResponse updateBatch(Long batchId, IngredientBatchUpdateRequest request,
                                                String username, int warningDays) {
        IngredientBatch batch = requireBatchForUpdate(batchId);
        Ingredient ingredient = batch.getNguyenLieu();
        String batchNumber = cleanRequired(request.soLo());
        validateUniqueBatchNumber(ingredient.getMaNguyenLieu(), batchNumber, batchId);
        LocalDate importDate = request.ngayNhap() == null ? batch.getNgayNhap() : request.ngayNhap();
        validateBatchDates(importDate, request.ngaySanXuat(), request.hanSuDung(), false);

        if (Boolean.FALSE.equals(request.trangThai())
                && safeQuantity(batch.getSoLuongConLai()).compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể ngừng sử dụng lô vẫn còn tồn kho");
        }

        batch.setSoLo(batchNumber);
        batch.setNgayNhap(importDate);
        batch.setNgaySanXuat(request.ngaySanXuat());
        batch.setHanSuDung(request.hanSuDung());
        if (request.donGiaNhap() != null) {
            batch.setDonGiaNhap(nonNegativeOrNull(request.donGiaNhap(), "Đơn giá nhập"));
        }
        batch.setNhaCungCap(cleanOptional(request.nhaCungCap()));
        if (request.trangThai() != null) {
            batch.setTrangThai(request.trangThai());
        }
        IngredientBatch saved = batchRepository.saveAndFlush(batch);
        systemActivityService.record(
                "KHO_NGUYEN_LIEU",
                "Cập nhật lô " + saved.getSoLo() + " của " + ingredient.getTenNguyenLieu(),
                ingredient.getMaNguyenLieu()
        );
        return toBatchResponse(saved, normalizeWarningDays(warningDays));
    }

    @Transactional
    public IngredientBatchResponse deactivateBatch(Long batchId, int warningDays) {
        IngredientBatch batch = requireBatchForUpdate(batchId);
        if (safeQuantity(batch.getSoLuongConLai()).compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể ngừng sử dụng lô vẫn còn tồn kho");
        }
        batch.setTrangThai(false);
        IngredientBatch saved = batchRepository.saveAndFlush(batch);
        systemActivityService.record(
                "KHO_NGUYEN_LIEU",
                "Ngừng sử dụng lô " + saved.getSoLo() + " của "
                        + saved.getNguyenLieu().getTenNguyenLieu(),
                saved.getNguyenLieu().getMaNguyenLieu()
        );
        return toBatchResponse(saved, normalizeWarningDays(warningDays));
    }

    @Transactional(readOnly = true)
    public IngredientBatchStatisticsResponse batchStatistics(int warningDays) {
        int normalizedWarningDays = normalizeWarningDays(warningDays);
        List<IngredientBatch> batches = batchRepository.findAll();
        long active = 0;
        long expiring = 0;
        long expired = 0;
        long usedUp = 0;
        long notTracked = 0;
        BigDecimal expiredValue = BigDecimal.ZERO;

        for (IngredientBatch batch : batches) {
            if (Boolean.TRUE.equals(batch.getTrangThai())) {
                active++;
            }
            String status = calculateExpiryStatus(batch, normalizedWarningDays);
            switch (status) {
                case EXPIRING_SOON -> expiring++;
                case EXPIRED -> {
                    expired++;
                    BigDecimal price = batch.getDonGiaNhap() == null
                            ? BigDecimal.ZERO : batch.getDonGiaNhap();
                    expiredValue = expiredValue.add(safeQuantity(batch.getSoLuongConLai()).multiply(price));
                }
                case USED_UP -> usedUp++;
                case NOT_TRACKED -> notTracked++;
                default -> {
                }
            }
        }
        return new IngredientBatchStatisticsResponse(
                batches.size(), active, expiring, expired, usedUp, notTracked, expiredValue
        );
    }

    private IngredientResponse importStock(Ingredient ingredient, InventoryStockRequest request,
                                            BigDecimal quantity, String username) {
        requirePositive(quantity);
        if (request.maLo() != null) {
            return importIntoExistingBatch(ingredient, request, quantity, username);
        }
        if (hasBatchInformation(request)) {
            IngredientBatchRequest batchRequest = new IngredientBatchRequest(
                    request.soLo(), request.ngayNhap(), request.ngaySanXuat(), request.hanSuDung(),
                    quantity, request.donGiaNhap(), request.nhaCungCap(), request.lyDo()
            );
            createBatch(ingredient.getMaNguyenLieu(), batchRequest, username, DEFAULT_EXPIRY_WARNING_DAYS);
            return toIngredientResponse(requireIngredient(ingredient.getMaNguyenLieu()));
        }

        BigDecimal before = safeQuantity(ingredient.getSoLuongTon());
        BigDecimal after = before.add(quantity);
        BigDecimal price = request.donGiaNhap() == null
                ? ingredient.getGiaNhap()
                : nonNegativeOrNull(request.donGiaNhap(), "Đơn giá nhập");
        if (request.donGiaNhap() != null) {
            ingredient.setGiaNhap(price);
        }
        ingredient.setSoLuongTon(after);
        Ingredient saved = ingredientRepository.saveAndFlush(ingredient);
        recordTransaction(saved, null, IMPORT, quantity, before, after, price,
                cleanOptional(request.lyDo()), username);
        recordStockActivity(saved, IMPORT, quantity);
        return toIngredientResponse(saved);
    }

    private IngredientResponse importIntoExistingBatch(Ingredient ingredient, InventoryStockRequest request,
                                                        BigDecimal quantity, String username) {
        IngredientBatch batch = requireBatchForUpdate(request.maLo());
        validateBatchBelongsToIngredient(batch, ingredient);
        if (!Boolean.TRUE.equals(batch.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lô nguyên liệu đã ngừng sử dụng");
        }
        if (!isBatchSafe(batch)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lô nguyên liệu đang bị khóa do vấn đề an toàn thực phẩm");
        }
        if (isExpired(batch)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể nhập thêm vào lô đã hết hạn");
        }

        BigDecimal before = safeQuantity(ingredient.getSoLuongTon());
        BigDecimal after = before.add(quantity);
        BigDecimal price = request.donGiaNhap() == null
                ? batch.getDonGiaNhap()
                : nonNegativeOrNull(request.donGiaNhap(), "Đơn giá nhập");

        batch.setSoLuongBanDau(safeQuantity(batch.getSoLuongBanDau()).add(quantity));
        batch.setSoLuongConLai(safeQuantity(batch.getSoLuongConLai()).add(quantity));
        if (request.donGiaNhap() != null) {
            batch.setDonGiaNhap(price);
            ingredient.setGiaNhap(price);
        }
        ingredient.setSoLuongTon(after);
        batchRepository.save(batch);
        Ingredient saved = ingredientRepository.saveAndFlush(ingredient);
        recordTransaction(saved, batch, IMPORT, quantity, before, after, price,
                cleanOptional(request.lyDo()), username);
        recordStockActivity(saved, IMPORT, quantity);
        return toIngredientResponse(saved);
    }

    private IngredientResponse exportStock(Ingredient ingredient, InventoryStockRequest request,
                                            BigDecimal quantity, String username) {
        requirePositive(quantity);
        if (request.maLo() != null) {
            return exportFromSpecificBatch(ingredient, request, quantity, username);
        }

        List<IngredientBatch> batches = batchRepository
                .findAvailableByIngredientForUpdate(ingredient.getMaNguyenLieu());
        BigDecimal before = safeQuantity(ingredient.getSoLuongTon());
        BigDecimal batchTotal = sumBatchRemaining(ingredient.getMaNguyenLieu());
        BigDecimal legacyStock = before.subtract(batchTotal).max(BigDecimal.ZERO);
        BigDecimal usableBatchStock = batches.stream()
                .filter(batch -> !isExpired(batch))
                .map(IngredientBatch::getSoLuongConLai)
                .map(this::safeQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal usableStock = usableBatchStock.add(legacyStock).min(before);

        if (usableStock.compareTo(quantity) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Số lượng có thể xuất chỉ còn " + strip(usableStock) + " "
                            + ingredient.getDonViTinh()
                            + ". Các lô hết hạn không được phép xuất sử dụng");
        }

        BigDecimal remaining = quantity;
        BigDecimal runningBefore = before;
        String reason = cleanOptional(request.lyDo());
        for (IngredientBatch batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (isExpired(batch)) {
                continue;
            }
            BigDecimal batchStock = safeQuantity(batch.getSoLuongConLai());
            BigDecimal used = batchStock.min(remaining);
            if (used.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            batch.setSoLuongConLai(batchStock.subtract(used));
            batchRepository.save(batch);
            BigDecimal runningAfter = runningBefore.subtract(used);
            recordTransaction(ingredient, batch, EXPORT, used, runningBefore, runningAfter,
                    batch.getDonGiaNhap(), reason, username);
            runningBefore = runningAfter;
            remaining = remaining.subtract(used);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal runningAfter = runningBefore.subtract(remaining);
            recordTransaction(ingredient, null, EXPORT, remaining, runningBefore, runningAfter,
                    ingredient.getGiaNhap(), reason, username);
            runningBefore = runningAfter;
        }

        ingredient.setSoLuongTon(before.subtract(quantity));
        Ingredient saved = ingredientRepository.saveAndFlush(ingredient);
        recordStockActivity(saved, EXPORT, quantity);
        return toIngredientResponse(saved);
    }

    private IngredientResponse exportFromSpecificBatch(Ingredient ingredient, InventoryStockRequest request,
                                                        BigDecimal quantity, String username) {
        IngredientBatch batch = requireBatchForUpdate(request.maLo());
        validateBatchBelongsToIngredient(batch, ingredient);
        if (!Boolean.TRUE.equals(batch.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lô nguyên liệu đã ngừng sử dụng");
        }
        if (!isBatchSafe(batch)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lô " + batch.getSoLo() + " đang bị khóa do vấn đề an toàn thực phẩm");
        }
        if (isExpired(batch)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lô " + batch.getSoLo() + " đã hết hạn, không thể xuất sử dụng");
        }
        BigDecimal batchStock = safeQuantity(batch.getSoLuongConLai());
        if (batchStock.compareTo(quantity) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lô " + batch.getSoLo() + " chỉ còn " + strip(batchStock) + " "
                            + ingredient.getDonViTinh());
        }

        BigDecimal before = safeQuantity(ingredient.getSoLuongTon());
        if (before.compareTo(quantity) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Số lượng xuất vượt quá tồn kho tổng hiện tại");
        }
        BigDecimal after = before.subtract(quantity);
        batch.setSoLuongConLai(batchStock.subtract(quantity));
        ingredient.setSoLuongTon(after);
        batchRepository.save(batch);
        Ingredient saved = ingredientRepository.saveAndFlush(ingredient);
        recordTransaction(saved, batch, EXPORT, quantity, before, after, batch.getDonGiaNhap(),
                cleanOptional(request.lyDo()), username);
        recordStockActivity(saved, EXPORT, quantity);
        return toIngredientResponse(saved);
    }

    private IngredientResponse adjustStock(Ingredient ingredient, InventoryStockRequest request,
                                            BigDecimal targetQuantity, String username) {
        if (request.maLo() != null) {
            return adjustSpecificBatch(ingredient, request, targetQuantity, username);
        }

        BigDecimal before = safeQuantity(ingredient.getSoLuongTon());
        if (before.compareTo(targetQuantity) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng kiểm kho không thay đổi so với tồn hiện tại");
        }
        BigDecimal batchStock = sumBatchRemaining(ingredient.getMaNguyenLieu());
        if (targetQuantity.compareTo(batchStock) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể điều chỉnh tồn tổng thấp hơn tổng tồn theo lô ("
                            + strip(batchStock) + " " + ingredient.getDonViTinh()
                            + "). Hãy chọn từng lô để điều chỉnh");
        }

        ingredient.setSoLuongTon(targetQuantity);
        Ingredient saved = ingredientRepository.saveAndFlush(ingredient);
        recordTransaction(saved, null, ADJUSTMENT, targetQuantity.subtract(before).abs(), before,
                targetQuantity, saved.getGiaNhap(), cleanOptional(request.lyDo()), username);
        recordStockActivity(saved, ADJUSTMENT, targetQuantity.subtract(before).abs());
        return toIngredientResponse(saved);
    }

    private IngredientResponse adjustSpecificBatch(Ingredient ingredient, InventoryStockRequest request,
                                                    BigDecimal targetQuantity, String username) {
        IngredientBatch batch = requireBatchForUpdate(request.maLo());
        validateBatchBelongsToIngredient(batch, ingredient);
        BigDecimal batchBefore = safeQuantity(batch.getSoLuongConLai());
        if (batchBefore.compareTo(targetQuantity) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng kiểm kho của lô không thay đổi");
        }

        BigDecimal difference = targetQuantity.subtract(batchBefore);
        BigDecimal before = safeQuantity(ingredient.getSoLuongTon());
        BigDecimal after = before.add(difference);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tồn kho sau điều chỉnh không hợp lệ");
        }

        batch.setSoLuongConLai(targetQuantity);
        ingredient.setSoLuongTon(after);
        batchRepository.save(batch);
        Ingredient saved = ingredientRepository.saveAndFlush(ingredient);
        recordTransaction(saved, batch, ADJUSTMENT, difference.abs(), before, after,
                batch.getDonGiaNhap(), cleanOptional(request.lyDo()), username);
        recordStockActivity(saved, ADJUSTMENT, difference.abs());
        return toIngredientResponse(saved);
    }

    private Specification<Ingredient> stockStatusSpecification(String status) {
        return (root, query, cb) -> switch (status) {
            case "HET_HANG" -> cb.lessThanOrEqualTo(
                    root.<BigDecimal>get("soLuongTon"), BigDecimal.ZERO
            );
            case "SAP_HET" -> cb.and(
                    cb.greaterThan(root.<BigDecimal>get("soLuongTon"), BigDecimal.ZERO),
                    cb.lessThanOrEqualTo(
                            root.<BigDecimal>get("soLuongTon"),
                            root.<BigDecimal>get("mucTonToiThieu")
                    )
            );
            case "CON_HANG" -> cb.greaterThan(
                    root.<BigDecimal>get("soLuongTon"),
                    root.<BigDecimal>get("mucTonToiThieu")
            );
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái tồn kho không hợp lệ");
        };
    }

    private Specification<IngredientBatch> expiryStatusSpecification(
            String status, LocalDate today, LocalDate warningEnd) {
        return (root, query, cb) -> switch (status) {
            case USED_UP -> cb.lessThanOrEqualTo(
                    root.<BigDecimal>get("soLuongConLai"), BigDecimal.ZERO);
            case NOT_TRACKED -> cb.and(
                    cb.greaterThan(root.<BigDecimal>get("soLuongConLai"), BigDecimal.ZERO),
                    cb.isNull(root.get("hanSuDung"))
            );
            case EXPIRED -> cb.and(
                    cb.greaterThan(root.<BigDecimal>get("soLuongConLai"), BigDecimal.ZERO),
                    cb.lessThan(root.<LocalDate>get("hanSuDung"), today)
            );
            case EXPIRING_SOON -> cb.and(
                    cb.greaterThan(root.<BigDecimal>get("soLuongConLai"), BigDecimal.ZERO),
                    cb.greaterThanOrEqualTo(root.<LocalDate>get("hanSuDung"), today),
                    cb.lessThanOrEqualTo(root.<LocalDate>get("hanSuDung"), warningEnd)
            );
            case VALID -> cb.and(
                    cb.greaterThan(root.<BigDecimal>get("soLuongConLai"), BigDecimal.ZERO),
                    cb.greaterThan(root.<LocalDate>get("hanSuDung"), warningEnd)
            );
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái hạn sử dụng không hợp lệ");
        };
    }

    private InventoryTransaction recordTransaction(Ingredient ingredient, IngredientBatch batch, String type,
                                                   BigDecimal quantity, BigDecimal before, BigDecimal after,
                                                   BigDecimal unitPrice, String reason, String username) {
        return recordTransaction(ingredient, batch, type, quantity, before, after,
                unitPrice, reason, null, null, username);
    }

    private InventoryTransaction recordTransaction(Ingredient ingredient, IngredientBatch batch, String type,
                                                   BigDecimal quantity, BigDecimal before, BigDecimal after,
                                                   BigDecimal unitPrice, String reason, String reasonCode,
                                                   String note, String username) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setNguyenLieu(ingredient);
        transaction.setLoNguyenLieu(batch);
        transaction.setLoaiGiaoDich(type);
        transaction.setSoLuong(quantity);
        transaction.setSoLuongTruoc(before);
        transaction.setSoLuongSau(after);
        transaction.setDonGiaNhap(unitPrice);
        transaction.setLyDo(reason);
        transaction.setMaLyDo(reasonCode);
        transaction.setGhiChu(note);
        transaction.setNguoiThucHien(
                username == null || username.isBlank() ? "Hệ thống" : username
        );
        return transactionRepository.saveAndFlush(transaction);
    }

    private Ingredient requireIngredient(Integer id) {
        return ingredientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy nguyên liệu: " + id));
    }

    private Ingredient requireIngredientForUpdate(Integer id) {
        return ingredientRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy nguyên liệu: " + id));
    }

    private IngredientBatch requireBatchForUpdate(Long id) {
        return batchRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy lô nguyên liệu: " + id));
    }

    private void ensureIngredientActive(Ingredient ingredient) {
        if (!Boolean.TRUE.equals(ingredient.getTrangThai())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Nguyên liệu đã ngừng sử dụng");
        }
    }

    private void validateBatchBelongsToIngredient(IngredientBatch batch, Ingredient ingredient) {
        if (!batch.getNguyenLieu().getMaNguyenLieu().equals(ingredient.getMaNguyenLieu())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lô nguyên liệu không thuộc nguyên liệu đã chọn");
        }
    }

    private void validateUniqueName(String name, Integer excludedId) {
        boolean exists = excludedId == null
                ? ingredientRepository.existsByTenNguyenLieuIgnoreCase(name)
                : ingredientRepository.existsByTenNguyenLieuIgnoreCaseAndMaNguyenLieuNot(name, excludedId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tên nguyên liệu đã tồn tại: " + name);
        }
    }

    private void validateUniqueBatchNumber(Integer ingredientId, String batchNumber, Long excludedBatchId) {
        boolean exists = excludedBatchId == null
                ? batchRepository.existsByNguyenLieuMaNguyenLieuAndSoLoIgnoreCase(ingredientId, batchNumber)
                : batchRepository.existsByNguyenLieuMaNguyenLieuAndSoLoIgnoreCaseAndMaLoNot(
                        ingredientId, batchNumber, excludedBatchId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Số lô đã tồn tại đối với nguyên liệu này: " + batchNumber);
        }
    }

    private void validateBatchDates(LocalDate importDate, LocalDate manufacturingDate,
                                    LocalDate expiryDate, boolean rejectExpiredImport) {
        if (manufacturingDate != null && manufacturingDate.isAfter(importDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ngày sản xuất không được sau ngày nhập");
        }
        if (expiryDate != null && expiryDate.isBefore(importDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hạn sử dụng không được trước ngày nhập");
        }
        if (manufacturingDate != null && expiryDate != null
                && expiryDate.isBefore(manufacturingDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Hạn sử dụng không được trước ngày sản xuất");
        }
        if (rejectExpiredImport && expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể nhập lô đã hết hạn");
        }
    }

    private IngredientResponse toIngredientResponse(Ingredient ingredient) {
        StockBreakdown breakdown = calculateStockBreakdown(ingredient);
        return new IngredientResponse(
                ingredient.getMaNguyenLieu(),
                ingredient.getTenNguyenLieu(),
                ingredient.getDonViTinh(),
                breakdown.physical(),
                breakdown.physical(),
                breakdown.usable(),
                breakdown.pendingDisposal(),
                ingredient.getMucTonToiThieu(),
                ingredient.getGiaNhap(),
                ingredient.getMoTa(),
                ingredient.getTrangThai(),
                calculateStockStatus(ingredient, breakdown.usable()),
                breakdown.physicalValue(),
                breakdown.usableValue(),
                breakdown.pendingDisposalValue(),
                ingredient.getNgayTao(),
                ingredient.getNgayCapNhat()
        );
    }

    private IngredientBatchResponse toBatchResponse(IngredientBatch batch, int warningDays) {
        Ingredient ingredient = batch.getNguyenLieu();
        BigDecimal remaining = safeQuantity(batch.getSoLuongConLai());
        BigDecimal price = batch.getDonGiaNhap() == null ? BigDecimal.ZERO : batch.getDonGiaNhap();
        String expiryStatus = calculateExpiryStatus(batch, warningDays);
        boolean expired = EXPIRED.equals(expiryStatus);
        boolean usable = Boolean.TRUE.equals(batch.getTrangThai())
                && remaining.compareTo(BigDecimal.ZERO) > 0
                && !expired
                && isBatchSafe(batch);
        BigDecimal usableQuantity = usable ? remaining : BigDecimal.ZERO;
        BigDecimal pendingQuantity = expired ? remaining : BigDecimal.ZERO;
        Long daysRemaining = batch.getHanSuDung() == null
                ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), batch.getHanSuDung());
        return new IngredientBatchResponse(
                batch.getMaLo(),
                ingredient.getMaNguyenLieu(),
                ingredient.getTenNguyenLieu(),
                ingredient.getDonViTinh(),
                batch.getSoLo(),
                batch.getNgayNhap(),
                batch.getNgaySanXuat(),
                batch.getHanSuDung(),
                batch.getSoLuongBanDau(),
                remaining,
                usableQuantity,
                pendingQuantity,
                batch.getDonGiaNhap(),
                remaining.multiply(price),
                usableQuantity.multiply(price),
                pendingQuantity.multiply(price),
                batch.getNhaCungCap(),
                batch.getTrangThai(),
                safetyStatus(batch),
                expiryStatus,
                daysRemaining,
                usable,
                remaining.compareTo(BigDecimal.ZERO) > 0,
                batch.getNgayTao(),
                batch.getNgayCapNhat()
        );
    }

    private InventoryTransactionResponse toTransactionResponse(InventoryTransaction transaction) {
        Ingredient ingredient = transaction.getNguyenLieu();
        IngredientBatch batch = transaction.getLoNguyenLieu();
        BigDecimal value = safeQuantity(transaction.getSoLuong())
                .multiply(safeQuantity(transaction.getDonGiaNhap()));
        return new InventoryTransactionResponse(
                transaction.getMaGiaoDich(),
                ingredient.getMaNguyenLieu(),
                ingredient.getTenNguyenLieu(),
                ingredient.getDonViTinh(),
                batch == null ? null : batch.getMaLo(),
                batch == null ? null : batch.getSoLo(),
                batch == null ? null : batch.getHanSuDung(),
                transaction.getLoaiGiaoDich(),
                transaction.getSoLuong(),
                transaction.getSoLuongTruoc(),
                transaction.getSoLuongSau(),
                transaction.getDonGiaNhap(),
                value,
                transaction.getLyDo(),
                transaction.getMaLyDo(),
                transaction.getGhiChu(),
                transaction.getNguoiThucHien(),
                transaction.getThoiGian()
        );
    }

    private String calculateStockStatus(Ingredient ingredient) {
        return calculateStockStatus(ingredient, calculateStockBreakdown(ingredient).usable());
    }

    private String calculateStockStatus(Ingredient ingredient, BigDecimal usableStock) {
        BigDecimal stock = safeQuantity(usableStock);
        BigDecimal minimum = ingredient.getMucTonToiThieu() == null
                ? BigDecimal.ZERO
                : ingredient.getMucTonToiThieu();
        if (stock.compareTo(BigDecimal.ZERO) <= 0) {
            return "HET_HANG";
        }
        if (stock.compareTo(minimum) <= 0) {
            return "SAP_HET";
        }
        return "CON_HANG";
    }

    private StockBreakdown calculateStockBreakdown(Ingredient ingredient) {
        BigDecimal physical = safeQuantity(ingredient.getSoLuongTon());
        BigDecimal defaultPrice = safeQuantity(ingredient.getGiaNhap());
        List<IngredientBatch> batches = batchRepository
                .findAllByIngredientForFefo(ingredient.getMaNguyenLieu());

        BigDecimal tracked = BigDecimal.ZERO;
        BigDecimal usableBatch = BigDecimal.ZERO;
        BigDecimal pendingDisposal = BigDecimal.ZERO;
        BigDecimal pendingDisposalValue = BigDecimal.ZERO;
        long expiredBatchCount = 0;

        for (IngredientBatch batch : batches) {
            BigDecimal remaining = safeQuantity(batch.getSoLuongConLai());
            BigDecimal price = batch.getDonGiaNhap() == null
                    ? defaultPrice
                    : batch.getDonGiaNhap();
            tracked = tracked.add(remaining);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (isExpired(batch)) {
                pendingDisposal = pendingDisposal.add(remaining);
                pendingDisposalValue = pendingDisposalValue.add(remaining.multiply(price));
                expiredBatchCount++;
            } else if (Boolean.TRUE.equals(batch.getTrangThai()) && isBatchSafe(batch)) {
                usableBatch = usableBatch.add(remaining);
            }
        }

        BigDecimal untracked = physical.subtract(tracked).max(BigDecimal.ZERO);
        BigDecimal usable = untracked.add(usableBatch).min(physical);
        BigDecimal physicalValue = physical.multiply(defaultPrice);
        BigDecimal usableValue = usable.multiply(defaultPrice);
        return new StockBreakdown(
                physical,
                usable,
                pendingDisposal.min(physical),
                physicalValue,
                usableValue,
                pendingDisposalValue,
                expiredBatchCount
        );
    }

    private static Map<String, String> buildWasteReasons() {
        Map<String, String> reasons = new LinkedHashMap<>();
        reasons.put(WASTE_EXPIRED, "Quá hạn sử dụng");
        reasons.put("HU_HONG", "Hư hỏng");
        reasons.put("BAO_QUAN_KHONG_DAT", "Bảo quản không đạt");
        reasons.put("DO_VO", "Đổ vỡ");
        reasons.put("KIEM_KE_THIEU", "Kiểm kê thiếu");
        reasons.put("CHE_BIEN_LOI", "Chế biến lỗi");
        reasons.put(WASTE_OTHER, "Lý do khác");
        return java.util.Collections.unmodifiableMap(reasons);
    }

    private String normalizeWasteReason(String value) {
        String normalized = normalizeCode(value);
        if (!WASTE_REASONS.containsKey(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lý do tiêu hủy không hợp lệ: " + value);
        }
        return normalized;
    }

    private String normalizeWasteReasonOrOther(String value) {
        try {
            return normalizeWasteReason(value);
        } catch (ResponseStatusException ignored) {
            return WASTE_OTHER;
        }
    }

    private record StockBreakdown(
            BigDecimal physical,
            BigDecimal usable,
            BigDecimal pendingDisposal,
            BigDecimal physicalValue,
            BigDecimal usableValue,
            BigDecimal pendingDisposalValue,
            long expiredBatchCount
    ) {
    }

    private String calculateExpiryStatus(IngredientBatch batch, int warningDays) {
        if (safeQuantity(batch.getSoLuongConLai()).compareTo(BigDecimal.ZERO) <= 0) {
            return USED_UP;
        }
        LocalDate expiryDate = batch.getHanSuDung();
        if (expiryDate == null) {
            return NOT_TRACKED;
        }
        LocalDate today = LocalDate.now();
        if (expiryDate.isBefore(today)) {
            return EXPIRED;
        }
        if (!expiryDate.isAfter(today.plusDays(warningDays))) {
            return EXPIRING_SOON;
        }
        return VALID;
    }

    private boolean isBatchSafe(IngredientBatch batch) {
        return SAFETY_SAFE.equals(safetyStatus(batch));
    }

    private String safetyStatus(IngredientBatch batch) {
        String status = batch.getTrangThaiAnToan();
        return status == null || status.isBlank()
                ? SAFETY_SAFE
                : status.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isExpired(IngredientBatch batch) {
        return batch.getHanSuDung() != null && batch.getHanSuDung().isBefore(LocalDate.now());
    }

    private String normalizeStockStatus(String value) {
        String normalized = normalizeCode(value);
        return switch (normalized) {
            case "CON_HANG", "IN_STOCK" -> "CON_HANG";
            case "SAP_HET", "LOW", "LOW_STOCK" -> "SAP_HET";
            case "HET_HANG", "OUT", "OUT_OF_STOCK" -> "HET_HANG";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái tồn kho không hợp lệ: " + value);
        };
    }

    private String normalizeExpiryStatus(String value) {
        String normalized = normalizeCode(value);
        return switch (normalized) {
            case "CON_HAN", "VALID" -> VALID;
            case "SAP_HET_HAN", "EXPIRING", "EXPIRING_SOON" -> EXPIRING_SOON;
            case "HET_HAN", "EXPIRED" -> EXPIRED;
            case "DA_DUNG_HET", "USED_UP", "EMPTY" -> USED_UP;
            case "KHONG_THEO_DOI", "NOT_TRACKED", "NO_EXPIRY" -> NOT_TRACKED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái hạn sử dụng không hợp lệ: " + value);
        };
    }

    private String normalizeTransactionType(String value) {
        String normalized = normalizeCode(value);
        return switch (normalized) {
            case "NHAP", "NHAP_KHO", "IMPORT", "IN" -> IMPORT;
            case "XUAT", "XUAT_KHO", "EXPORT", "OUT" -> EXPORT;
            case "DIEU_CHINH", "KIEM_KHO", "ADJUST", "ADJUSTMENT" -> ADJUSTMENT;
            case "TIEU_HUY", "HAO_HUT", "WASTE", "DISPOSE", "DISPOSAL" -> WASTE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Loại giao dịch phải là NHAP, XUAT, DIEU_CHINH hoặc TIEU_HUY");
        };
    }

    private String normalizeCode(String value) {
        return cleanRequired(value)
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private boolean hasBatchInformation(InventoryStockRequest request) {
        return request.soLo() != null && !request.soLo().isBlank()
                || request.ngayNhap() != null
                || request.ngaySanXuat() != null
                || request.hanSuDung() != null
                || request.nhaCungCap() != null && !request.nhaCungCap().isBlank();
    }

    private BigDecimal sumBatchRemaining(Integer ingredientId) {
        BigDecimal result = batchRepository.sumRemainingByIngredient(ingredientId);
        return result == null ? BigDecimal.ZERO : result;
    }

    private void recordStockActivity(Ingredient ingredient, String type, BigDecimal quantity) {
        systemActivityService.record(
                "KHO_NGUYEN_LIEU",
                displayTransactionType(type) + " " + strip(quantity) + " "
                        + ingredient.getDonViTinh() + " nguyên liệu " + ingredient.getTenNguyenLieu(),
                ingredient.getMaNguyenLieu()
        );
    }

    private String displayTransactionType(String type) {
        return switch (type) {
            case IMPORT -> "Nhập kho";
            case EXPORT -> "Xuất kho";
            case ADJUSTMENT -> "Điều chỉnh tồn";
            case WASTE -> "Tiêu hủy";
            default -> type;
        };
    }

    private BigDecimal safeQuantity(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal positive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " phải lớn hơn 0");
        }
        return value;
    }

    private BigDecimal nonNegativeOrZero(BigDecimal value, String fieldName) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " không được nhỏ hơn 0");
        }
        return value;
    }

    private BigDecimal nonNegativeOrNull(BigDecimal value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " không được nhỏ hơn 0");
        }
        return value;
    }

    private void requirePositive(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng nhập hoặc xuất phải lớn hơn 0");
        }
    }

    private String cleanRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Dữ liệu không được để trống");
        }
        return value.trim();
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc không được nhỏ hơn ngày bắt đầu");
        }
    }

    private int normalizeWarningDays(int warningDays) {
        return Math.min(Math.max(warningDays, 0), 365);
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
