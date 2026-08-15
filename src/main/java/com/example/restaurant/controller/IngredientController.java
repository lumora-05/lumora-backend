package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
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
import com.example.restaurant.dto.InventoryWasteRequest;
import com.example.restaurant.dto.InventoryWasteResponse;
import com.example.restaurant.dto.InventoryWasteStatisticsResponse;
import com.example.restaurant.dto.PageResponse;
import com.example.restaurant.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/ingredients")
@PreAuthorize("hasRole('ADMIN')")
public class IngredientController {
    private final InventoryService inventoryService;

    public IngredientController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<IngredientResponse>>> findAll() {
        List<IngredientResponse> result = inventoryService.findAll(null);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách nguyên liệu thành công", result));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<IngredientResponse>>> findActive() {
        List<IngredientResponse> result = inventoryService.findAll(true);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách nguyên liệu đang sử dụng thành công", result));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponse<IngredientResponse>>> findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String stockStatus) {
        PageResponse<IngredientResponse> result = PageResponse.from(
                inventoryService.findPage(page, size, keyword, active, stockStatus)
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách kho nguyên liệu thành công", result));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<IngredientResponse>>> findLowStock() {
        List<IngredientResponse> result = inventoryService.findLowStock();
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách nguyên liệu sắp hết thành công", result));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<InventoryStatisticsResponse>> statistics() {
        InventoryStatisticsResponse result = inventoryService.statistics();
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thống kê kho nguyên liệu thành công", result));
    }

    @GetMapping("/waste/reasons")
    public ResponseEntity<ApiResponse<List<InventoryWasteReasonResponse>>> wasteReasons() {
        List<InventoryWasteReasonResponse> result = inventoryService.findWasteReasons();
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách lý do tiêu hủy thành công", result));
    }

    @GetMapping("/waste/statistics")
    public ResponseEntity<ApiResponse<InventoryWasteStatisticsResponse>> wasteStatistics(
            @RequestParam(required = false) Integer ingredientId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        InventoryWasteStatisticsResponse result = inventoryService.wasteStatistics(ingredientId, from, to);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thống kê tiêu hủy và hao hụt thành công", result));
    }

    @PostMapping("/{id}/waste")
    public ResponseEntity<ApiResponse<InventoryWasteResponse>> recordWaste(
            @PathVariable Integer id,
            @Valid @RequestBody InventoryWasteRequest request,
            @RequestParam(defaultValue = "3") int warningDays,
            Authentication authentication) {
        InventoryWasteResponse result = inventoryService.recordWaste(
                id, request, authentication.getName(), warningDays);
        return ResponseEntity.ok(ApiResponse.success(
                "Ghi nhận tiêu hủy nguyên liệu thành công", result));
    }

    @PostMapping("/batches/{batchId}/dispose")
    public ResponseEntity<ApiResponse<InventoryWasteResponse>> disposeBatch(
            @PathVariable Long batchId,
            @Valid @RequestBody InventoryWasteRequest request,
            @RequestParam(defaultValue = "3") int warningDays,
            Authentication authentication) {
        InventoryWasteResponse result = inventoryService.disposeBatch(
                batchId, request, authentication.getName(), warningDays);
        return ResponseEntity.ok(ApiResponse.success(
                "Tiêu hủy lô nguyên liệu thành công", result));
    }

    @GetMapping("/transactions/page")
    public ResponseEntity<ApiResponse<PageResponse<InventoryTransactionResponse>>> transactionPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer ingredientId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        PageResponse<InventoryTransactionResponse> result = PageResponse.from(
                inventoryService.findTransactionPage(page, size, ingredientId, batchId, type, from, to)
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy lịch sử nhập xuất kho thành công", result));
    }

    @GetMapping("/batches/page")
    public ResponseEntity<ApiResponse<PageResponse<IngredientBatchResponse>>> batchPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer ingredientId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String expiryStatus,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "3") int warningDays) {
        PageResponse<IngredientBatchResponse> result = PageResponse.from(
                inventoryService.findBatchPage(
                        page, size, keyword, ingredientId, active,
                        expiryStatus, from, to, warningDays)
        );
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách lô và hạn sử dụng thành công", result));
    }

    @GetMapping("/batches/statistics")
    public ResponseEntity<ApiResponse<IngredientBatchStatisticsResponse>> batchStatistics(
            @RequestParam(defaultValue = "3") int warningDays) {
        IngredientBatchStatisticsResponse result = inventoryService.batchStatistics(warningDays);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thống kê hạn sử dụng thành công", result));
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<ApiResponse<IngredientBatchResponse>> findBatchById(
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "3") int warningDays) {
        IngredientBatchResponse result = inventoryService.findBatchById(batchId, warningDays);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thông tin lô nguyên liệu thành công", result));
    }

    @GetMapping("/{id}/batches")
    public ResponseEntity<ApiResponse<List<IngredientBatchResponse>>> findBatchesByIngredient(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "3") int warningDays) {
        List<IngredientBatchResponse> result = inventoryService.findBatchesByIngredient(id, warningDays);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách lô của nguyên liệu thành công", result));
    }

    @PostMapping("/{id}/batches")
    public ResponseEntity<ApiResponse<IngredientBatchResponse>> createBatch(
            @PathVariable Integer id,
            @Valid @RequestBody IngredientBatchRequest request,
            @RequestParam(defaultValue = "3") int warningDays,
            Authentication authentication) {
        IngredientBatchResponse result = inventoryService.createBatch(
                id, request, authentication.getName(), warningDays);
        return ResponseEntity.ok(ApiResponse.success(
                "Nhập lô nguyên liệu thành công", result));
    }

    @PutMapping("/batches/{batchId}")
    public ResponseEntity<ApiResponse<IngredientBatchResponse>> updateBatch(
            @PathVariable Long batchId,
            @Valid @RequestBody IngredientBatchUpdateRequest request,
            @RequestParam(defaultValue = "3") int warningDays,
            Authentication authentication) {
        IngredientBatchResponse result = inventoryService.updateBatch(
                batchId, request, authentication.getName(), warningDays);
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật lô nguyên liệu thành công", result));
    }

    @DeleteMapping("/batches/{batchId}")
    public ResponseEntity<ApiResponse<IngredientBatchResponse>> deactivateBatch(
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "3") int warningDays) {
        IngredientBatchResponse result = inventoryService.deactivateBatch(batchId, warningDays);
        return ResponseEntity.ok(ApiResponse.success(
                "Ngừng sử dụng lô nguyên liệu thành công", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IngredientResponse>> findById(@PathVariable Integer id) {
        IngredientResponse result = inventoryService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy thông tin nguyên liệu thành công", result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IngredientResponse>> create(
            @Valid @RequestBody IngredientRequest request,
            Authentication authentication) {
        IngredientResponse result = inventoryService.create(request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Thêm nguyên liệu thành công", result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<IngredientResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody IngredientRequest request,
            Authentication authentication) {
        IngredientResponse result = inventoryService.update(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật nguyên liệu thành công", result));
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<ApiResponse<IngredientResponse>> updateStock(
            @PathVariable Integer id,
            @Valid @RequestBody InventoryStockRequest request,
            Authentication authentication) {
        IngredientResponse result = inventoryService.updateStock(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật tồn kho thành công", result));
    }

    // Giữ tương thích với client cũ từng dùng POST cho thao tác tồn kho.
    @PostMapping("/{id}/stock")
    public ResponseEntity<ApiResponse<IngredientResponse>> updateStockByPost(
            @PathVariable Integer id,
            @Valid @RequestBody InventoryStockRequest request,
            Authentication authentication) {
        return updateStock(id, request, authentication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<IngredientResponse>> deactivate(@PathVariable Integer id) {
        IngredientResponse result = inventoryService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success(
                "Ngừng sử dụng nguyên liệu thành công", result));
    }
}
