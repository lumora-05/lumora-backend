package com.example.restaurant.service;

import com.example.restaurant.dto.BatchImpactItemResponse;
import com.example.restaurant.dto.BatchImpactResponse;
import com.example.restaurant.dto.BatchIncidentRequest;
import com.example.restaurant.dto.BatchIncidentResolveRequest;
import com.example.restaurant.dto.BatchIncidentResponse;
import com.example.restaurant.dto.OrderItemBatchUsageResponse;
import com.example.restaurant.dto.OrderItemTraceResponse;
import com.example.restaurant.entity.FoodRecipeIngredient;
import com.example.restaurant.entity.Ingredient;
import com.example.restaurant.entity.IngredientBatch;
import com.example.restaurant.entity.IngredientBatchIncident;
import com.example.restaurant.entity.InventoryTransaction;
import com.example.restaurant.entity.Order;
import com.example.restaurant.entity.OrderItem;
import com.example.restaurant.entity.OrderItemBatchUsage;
import com.example.restaurant.repository.FoodRecipeIngredientRepository;
import com.example.restaurant.repository.IngredientBatchIncidentRepository;
import com.example.restaurant.repository.IngredientBatchRepository;
import com.example.restaurant.repository.IngredientRepository;
import com.example.restaurant.repository.InventoryTransactionRepository;
import com.example.restaurant.repository.OrderItemBatchUsageRepository;
import com.example.restaurant.repository.OrderItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FoodTraceabilityService {
    public static final String SAFETY_SAFE = "AN_TOAN";
    public static final String SAFETY_TEMP_LOCKED = "KHOA_TAM_THOI";
    public static final String SAFETY_INCIDENT = "CO_SU_CO";
    public static final String SAFETY_RECALLED = "THU_HOI";
    public static final String SAFETY_DISPOSED = "DA_TIEU_HUY";

    private static final Set<String> VALID_SAFETY_STATUSES = Set.of(
            SAFETY_SAFE,
            SAFETY_TEMP_LOCKED,
            SAFETY_INCIDENT,
            SAFETY_RECALLED,
            SAFETY_DISPOSED
    );
    private static final Set<String> VALID_SEVERITIES = Set.of(
            "THAP", "TRUNG_BINH", "CAO", "KHAN_CAP"
    );
    private static final Set<String> VALID_INCIDENT_RESOLUTION_STATUSES = Set.of(
            "DANG_XU_LY", "DA_DONG", "DA_THU_HOI", "DA_TIEU_HUY"
    );

    private final FoodRecipeIngredientRepository recipeRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemBatchUsageRepository usageRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final IngredientBatchIncidentRepository incidentRepository;
    private final SystemActivityService systemActivityService;

    public FoodTraceabilityService(FoodRecipeIngredientRepository recipeRepository,
                                   OrderItemRepository orderItemRepository,
                                   OrderItemBatchUsageRepository usageRepository,
                                   IngredientRepository ingredientRepository,
                                   IngredientBatchRepository batchRepository,
                                   InventoryTransactionRepository transactionRepository,
                                   IngredientBatchIncidentRepository incidentRepository,
                                   SystemActivityService systemActivityService) {
        this.recipeRepository = recipeRepository;
        this.orderItemRepository = orderItemRepository;
        this.usageRepository = usageRepository;
        this.ingredientRepository = ingredientRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.incidentRepository = incidentRepository;
        this.systemActivityService = systemActivityService;
    }

    /**
     * Cấp phát nguyên liệu theo FEFO khi món bắt đầu chế biến.
     *
     * Để không làm gián đoạn dữ liệu cũ, món chưa được thiết lập công thức vẫn đi
     * theo quy trình bếp cũ. Khi món đã có công thức, toàn bộ nguyên liệu bắt buộc
     * phải lấy từ lô còn hạn và an toàn; không dùng phần tồn cũ không có mã lô.
     */
    @Transactional
    public List<OrderItemBatchUsage> allocateForCooking(OrderItem item, String username) {
        if (item == null || item.getMaChiTiet() == null || item.getMonAn() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chi tiết món không hợp lệ để cấp phát nguyên liệu");
        }
        if (usageRepository.existsByChiTietDonHang_MaChiTiet(item.getMaChiTiet())) {
            return usageRepository.findTraceByOrderItemId(item.getMaChiTiet());
        }

        List<FoodRecipeIngredient> recipe = new ArrayList<>(
                recipeRepository.findActiveByFoodId(item.getMonAn().getMaMonAn())
        );
        if (recipe.isEmpty()) {
            return List.of();
        }
        recipe.sort(Comparator.comparing(r -> r.getNguyenLieu().getMaNguyenLieu()));

        int portions = item.getSoLuong() == null ? 0 : item.getSoLuong();
        if (portions <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Số lượng món phải lớn hơn 0 trước khi cấp phát nguyên liệu");
        }

        String actor = displayActor(username);
        List<OrderItemBatchUsage> allocations = new ArrayList<>();
        for (FoodRecipeIngredient recipeItem : recipe) {
            BigDecimal required = safeQuantity(recipeItem.getDinhLuong())
                    .multiply(BigDecimal.valueOf(portions));
            if (required.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Integer ingredientId = recipeItem.getNguyenLieu().getMaNguyenLieu();
            Ingredient ingredient = ingredientRepository.findByIdForUpdate(ingredientId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy nguyên liệu trong công thức: " + ingredientId
                    ));
            if (!Boolean.TRUE.equals(ingredient.getTrangThai())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Không thể bắt đầu chế biến vì nguyên liệu đã ngừng sử dụng: "
                                + ingredient.getTenNguyenLieu());
            }

            List<IngredientBatch> availableBatches = batchRepository
                    .findAvailableByIngredientForUpdate(ingredientId)
                    .stream()
                    .filter(this::isSafeForCooking)
                    .toList();
            BigDecimal available = availableBatches.stream()
                    .map(IngredientBatch::getSoLuongConLai)
                    .map(this::safeQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (available.compareTo(required) < 0) {
                BigDecimal shortage = required.subtract(available);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Không thể bắt đầu chế biến món " + item.getMonAn().getTenMonAn()
                                + ". Thiếu " + strip(shortage) + " " + ingredient.getDonViTinh()
                                + " nguyên liệu " + ingredient.getTenNguyenLieu()
                                + ". Chỉ các lô còn hạn và an toàn mới được sử dụng");
            }

            BigDecimal totalBefore = safeQuantity(ingredient.getSoLuongTon());
            if (totalBefore.compareTo(required) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Tồn kho tổng của " + ingredient.getTenNguyenLieu()
                                + " không đủ để chế biến món");
            }

            BigDecimal remaining = required;
            BigDecimal runningBefore = totalBefore;
            for (IngredientBatch batch : availableBatches) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BigDecimal batchStock = safeQuantity(batch.getSoLuongConLai());
                BigDecimal used = batchStock.min(remaining);
                if (used.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                batch.setSoLuongConLai(batchStock.subtract(used));
                batchRepository.save(batch);
                BigDecimal runningAfter = runningBefore.subtract(used);
                InventoryTransaction transaction = createCookingExportTransaction(
                        ingredient,
                        batch,
                        item,
                        used,
                        runningBefore,
                        runningAfter,
                        actor
                );

                OrderItemBatchUsage usage = new OrderItemBatchUsage();
                usage.setChiTietDonHang(item);
                usage.setNguyenLieu(ingredient);
                usage.setLoNguyenLieu(batch);
                usage.setGiaoDichKho(transaction);
                usage.setSoLuongSuDung(used);
                usage.setTrangThai("DA_CAP_PHAT");
                usage.setNguoiCapPhat(actor);
                usage.setThoiGianCapPhat(LocalDateTime.now());
                allocations.add(usageRepository.save(usage));

                runningBefore = runningAfter;
                remaining = remaining.subtract(used);
            }

            ingredient.setSoLuongTon(totalBefore.subtract(required));
            ingredientRepository.saveAndFlush(ingredient);
        }

        usageRepository.flush();
        Order order = item.getDonHang();
        systemActivityService.record(
                "FOOD_TRACEABILITY_ALLOCATED",
                "Đã cấp phát " + allocations.size() + " lô nguyên liệu theo FEFO cho món "
                        + item.getMonAn().getTenMonAn() + " trong đơn #DH"
                        + (order == null ? "?" : order.getMaDonHang()),
                order == null ? item.getMaChiTiet() : order.getMaDonHang()
        );
        return allocations;
    }

    /**
     * Món đã được cấp phát nguyên liệu nhưng sau đó bị hủy vẫn giữ nguyên lượng đã
     * xuất; chỉ đánh dấu bản ghi truy xuất để phản ánh đúng hao hụt thực tế.
     */
    @Transactional
    public void markAllocatedUsageCancelled(Integer itemId) {
        List<OrderItemBatchUsage> usages = usageRepository.findTraceByOrderItemId(itemId);
        if (usages.isEmpty()) {
            return;
        }
        usages.forEach(usage -> usage.setTrangThai("DA_HUY_MON"));
        usageRepository.saveAll(usages);
        usageRepository.flush();
    }

    @Transactional(readOnly = true)
    public OrderItemTraceResponse traceOrderItem(Integer itemId) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy chi tiết đơn hàng: " + itemId
                ));
        List<OrderItemBatchUsage> usages = usageRepository.findTraceByOrderItemId(itemId);
        Order order = item.getDonHang();
        Integer tableId = order.getBanAn() == null ? null : order.getBanAn().getMaBan();
        String tableName = order.getBanAn() == null ? null : order.getBanAn().getTenBan();
        return new OrderItemTraceResponse(
                item.getMaChiTiet(),
                order.getMaDonHang(),
                tableId,
                tableName,
                item.getMonAn().getMaMonAn(),
                item.getMonAn().getTenMonAn(),
                item.getSoLuong(),
                item.getTrangThaiMon(),
                order.getThoiGianDat(),
                recipeRepository.existsByMonAn_MaMonAnAndTrangThaiTrue(item.getMonAn().getMaMonAn()),
                !usages.isEmpty(),
                usages.stream().map(this::toUsageResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public BatchImpactResponse traceBatchImpact(Long batchId) {
        IngredientBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lô nguyên liệu: " + batchId
                ));
        List<OrderItemBatchUsage> usages = usageRepository.findImpactByBatchId(batchId);
        Set<Integer> orderIds = new LinkedHashSet<>();
        Set<Integer> tableIds = new LinkedHashSet<>();
        Set<Integer> itemIds = new LinkedHashSet<>();
        BigDecimal totalUsed = BigDecimal.ZERO;
        long cooking = 0;
        long served = 0;
        List<BatchImpactItemResponse> details = new ArrayList<>();

        for (OrderItemBatchUsage usage : usages) {
            OrderItem item = usage.getChiTietDonHang();
            Order order = item.getDonHang();
            orderIds.add(order.getMaDonHang());
            if (order.getBanAn() != null && order.getBanAn().getMaBan() != null) {
                tableIds.add(order.getBanAn().getMaBan());
            }
            itemIds.add(item.getMaChiTiet());
            totalUsed = totalUsed.add(safeQuantity(usage.getSoLuongSuDung()));
            String itemStatus = normalize(item.getTrangThaiMon());
            if (Set.of("DANG_NAU", "DANG_CHE_BIEN").contains(itemStatus)) {
                cooking++;
            }
            if ("DA_PHUC_VU".equals(itemStatus)) {
                served++;
            }
            details.add(new BatchImpactItemResponse(
                    usage.getMaSuDung(),
                    item.getMaChiTiet(),
                    order.getMaDonHang(),
                    order.getBanAn() == null ? null : order.getBanAn().getMaBan(),
                    order.getBanAn() == null ? null : order.getBanAn().getTenBan(),
                    item.getMonAn().getMaMonAn(),
                    item.getMonAn().getTenMonAn(),
                    item.getSoLuong(),
                    usage.getSoLuongSuDung(),
                    usage.getNguyenLieu().getDonViTinh(),
                    item.getTrangThaiMon(),
                    order.getTrangThai(),
                    order.getThoiGianDat(),
                    usage.getThoiGianCapPhat()
            ));
        }

        Ingredient ingredient = batch.getNguyenLieu();
        return new BatchImpactResponse(
                batch.getMaLo(),
                ingredient.getMaNguyenLieu(),
                ingredient.getTenNguyenLieu(),
                ingredient.getDonViTinh(),
                batch.getSoLo(),
                batch.getNhaCungCap(),
                batch.getNgayNhap(),
                batch.getHanSuDung(),
                safetyStatus(batch),
                safeQuantity(batch.getSoLuongConLai()),
                totalUsed,
                itemIds.size(),
                orderIds.size(),
                tableIds.size(),
                cooking,
                served,
                details
        );
    }

    @Transactional
    public BatchIncidentResponse reportIncident(Long batchId,
                                                BatchIncidentRequest request,
                                                String username) {
        IngredientBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lô nguyên liệu: " + batchId
                ));
        if (incidentRepository.existsOpenIncidentByBatchId(batchId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lô nguyên liệu đang có một sự cố chưa được xử lý");
        }

        String severity = normalize(request.mucDo());
        if (!VALID_SEVERITIES.contains(severity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mức độ sự cố không hợp lệ. Dùng THAP, TRUNG_BINH, CAO hoặc KHAN_CAP");
        }

        batch.setTrangThaiAnToan(SAFETY_INCIDENT);
        batchRepository.save(batch);

        IngredientBatchIncident incident = new IngredientBatchIncident();
        incident.setLoNguyenLieu(batch);
        incident.setLoaiSuCo(normalize(request.loaiSuCo()));
        incident.setMucDo(severity);
        incident.setLyDo(request.lyDo().trim());
        incident.setGhiChu(clean(request.ghiChu()));
        incident.setTrangThai("MOI");
        incident.setNguoiPhatHien(displayActor(username));
        incident.setThoiGianPhatHien(LocalDateTime.now());
        IngredientBatchIncident saved = incidentRepository.saveAndFlush(incident);

        systemActivityService.record(
                "FOOD_SAFETY_INCIDENT_REPORTED",
                "Lô " + batch.getSoLo() + " của " + batch.getNguyenLieu().getTenNguyenLieu()
                        + " đã bị khóa do sự cố an toàn thực phẩm mức " + severity,
                batch.getNguyenLieu().getMaNguyenLieu()
        );
        return toIncidentResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BatchIncidentResponse> findIncidents(Long batchId) {
        List<IngredientBatchIncident> incidents = batchId == null
                ? incidentRepository.findAllByOrderByThoiGianPhatHienDescMaSuCoDesc()
                : incidentRepository.findByLoNguyenLieu_MaLoOrderByThoiGianPhatHienDescMaSuCoDesc(batchId);
        return incidents.stream().map(this::toIncidentResponse).toList();
    }

    @Transactional
    public BatchIncidentResponse resolveIncident(Long incidentId,
                                                 BatchIncidentResolveRequest request,
                                                 String username) {
        IngredientBatchIncident incident = incidentRepository.findByIdForUpdate(incidentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy sự cố lô nguyên liệu: " + incidentId
                ));
        String currentIncidentStatus = normalize(incident.getTrangThai());
        if (!Set.of("MOI", "DANG_XU_LY").contains(currentIncidentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Sự cố đã kết thúc, không thể cập nhật lại");
        }
        String incidentStatus = normalize(request.trangThaiSuCo());
        String safetyStatus = normalize(request.trangThaiAnToanLo());
        if (!VALID_INCIDENT_RESOLUTION_STATUSES.contains(incidentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái xử lý sự cố không hợp lệ");
        }
        if (!VALID_SAFETY_STATUSES.contains(safetyStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái an toàn của lô không hợp lệ");
        }

        IngredientBatch batch = batchRepository.findByIdForUpdate(incident.getLoNguyenLieu().getMaLo())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy lô nguyên liệu của sự cố"
                ));
        if ("DA_TIEU_HUY".equals(incidentStatus)
                && safeQuantity(batch.getSoLuongConLai()).compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Lô vẫn còn tồn. Hãy ghi nhận tiêu hủy toàn bộ lượng còn lại trước khi đóng sự cố là đã tiêu hủy");
        }
        if ("DA_THU_HOI".equals(incidentStatus)) {
            safetyStatus = SAFETY_RECALLED;
        }
        if ("DA_TIEU_HUY".equals(incidentStatus)) {
            safetyStatus = SAFETY_DISPOSED;
        }

        batch.setTrangThaiAnToan(safetyStatus);
        batchRepository.save(batch);
        incident.setTrangThai(incidentStatus);
        incident.setNguoiXuLy(displayActor(username));
        incident.setKetQuaXuLy(request.ketQuaXuLy().trim());
        if (!"DANG_XU_LY".equals(incidentStatus)) {
            incident.setThoiGianXuLy(LocalDateTime.now());
        }
        IngredientBatchIncident saved = incidentRepository.saveAndFlush(incident);

        systemActivityService.record(
                "FOOD_SAFETY_INCIDENT_RESOLVED",
                "Sự cố lô " + batch.getSoLo() + " đã chuyển sang " + incidentStatus
                        + ", trạng thái an toàn lô: " + safetyStatus,
                batch.getNguyenLieu().getMaNguyenLieu()
        );
        return toIncidentResponse(saved);
    }

    private InventoryTransaction createCookingExportTransaction(Ingredient ingredient,
                                                                IngredientBatch batch,
                                                                OrderItem item,
                                                                BigDecimal quantity,
                                                                BigDecimal before,
                                                                BigDecimal after,
                                                                String actor) {
        Order order = item.getDonHang();
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setNguyenLieu(ingredient);
        transaction.setLoNguyenLieu(batch);
        transaction.setChiTietDonHang(item);
        transaction.setLoaiGiaoDich(InventoryService.EXPORT);
        transaction.setSoLuong(quantity);
        transaction.setSoLuongTruoc(before);
        transaction.setSoLuongSau(after);
        transaction.setDonGiaNhap(batch.getDonGiaNhap());
        transaction.setLyDo("Cấp phát nguyên liệu để chế biến món " + item.getMonAn().getTenMonAn());
        transaction.setMaLyDo("CHE_BIEN_MON");
        transaction.setGhiChu("Đơn #DH" + (order == null ? "?" : order.getMaDonHang())
                + ", chi tiết món #" + item.getMaChiTiet());
        transaction.setNguoiThucHien(actor);
        transaction.setThoiGian(LocalDateTime.now());
        return transactionRepository.saveAndFlush(transaction);
    }

    private OrderItemBatchUsageResponse toUsageResponse(OrderItemBatchUsage usage) {
        IngredientBatch batch = usage.getLoNguyenLieu();
        Ingredient ingredient = usage.getNguyenLieu();
        return new OrderItemBatchUsageResponse(
                usage.getMaSuDung(),
                ingredient.getMaNguyenLieu(),
                ingredient.getTenNguyenLieu(),
                ingredient.getDonViTinh(),
                batch.getMaLo(),
                batch.getSoLo(),
                batch.getNgayNhap(),
                batch.getNgaySanXuat(),
                batch.getHanSuDung(),
                batch.getNhaCungCap(),
                safetyStatus(batch),
                usage.getSoLuongSuDung(),
                usage.getTrangThai(),
                usage.getNguoiCapPhat(),
                usage.getThoiGianCapPhat()
        );
    }

    private BatchIncidentResponse toIncidentResponse(IngredientBatchIncident incident) {
        IngredientBatch batch = incident.getLoNguyenLieu();
        return new BatchIncidentResponse(
                incident.getMaSuCo(),
                batch.getMaLo(),
                batch.getNguyenLieu().getMaNguyenLieu(),
                batch.getNguyenLieu().getTenNguyenLieu(),
                batch.getSoLo(),
                incident.getLoaiSuCo(),
                incident.getMucDo(),
                incident.getLyDo(),
                incident.getGhiChu(),
                incident.getTrangThai(),
                safetyStatus(batch),
                incident.getNguoiPhatHien(),
                incident.getThoiGianPhatHien(),
                incident.getNguoiXuLy(),
                incident.getThoiGianXuLy(),
                incident.getKetQuaXuLy()
        );
    }

    private boolean isSafeForCooking(IngredientBatch batch) {
        return Boolean.TRUE.equals(batch.getTrangThai())
                && safeQuantity(batch.getSoLuongConLai()).compareTo(BigDecimal.ZERO) > 0
                && (batch.getHanSuDung() == null || !batch.getHanSuDung().isBefore(LocalDate.now()))
                && SAFETY_SAFE.equals(safetyStatus(batch));
    }

    private String safetyStatus(IngredientBatch batch) {
        return StringUtils.hasText(batch.getTrangThaiAnToan())
                ? normalize(batch.getTrangThaiAnToan())
                : SAFETY_SAFE;
    }

    private BigDecimal safeQuantity(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String strip(BigDecimal value) {
        return safeQuantity(value).stripTrailingZeros().toPlainString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String displayActor(String username) {
        return StringUtils.hasText(username) ? username.trim() : "Hệ thống";
    }
}
