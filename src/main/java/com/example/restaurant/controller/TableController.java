package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.QrStatusUpdateRequest;
import com.example.restaurant.dto.TableArrangementResponse;
import com.example.restaurant.dto.TableMergeRequest;
import com.example.restaurant.dto.TableRequest;
import com.example.restaurant.dto.TableTransferRequest;
import com.example.restaurant.entity.DiningTable;
import com.example.restaurant.service.TableArrangementService;
import com.example.restaurant.service.TableService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tables")
@PreAuthorize("hasAnyRole('ADMIN','WAITER','CASHIER')")
public class TableController {
    private final TableService tableService;
    private final TableArrangementService tableArrangementService;

    public TableController(TableService tableService,
                           TableArrangementService tableArrangementService) {
        this.tableService = tableService;
        this.tableArrangementService = tableArrangementService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DiningTable>>> allTables(Authentication authentication) {
        List<DiningTable> tables = isWaiterOnly(authentication)
                ? tableService.findAllForWaiter(authentication.getName())
                : tableService.findAll();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bàn ăn thành công", tables));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DiningTable>> detail(@PathVariable Integer id,
                                                            Authentication authentication) {
        DiningTable diningTable = isWaiterOnly(authentication)
                ? tableService.findByIdForWaiter(id, authentication.getName())
                : tableService.findById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết bàn ăn thành công", diningTable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DiningTable>> create(@Valid @RequestBody TableRequest request) {
        DiningTable diningTable = tableService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Thêm bàn ăn thành công", diningTable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DiningTable>> update(@PathVariable Integer id,
                                                            @Valid @RequestBody TableRequest request) {
        DiningTable diningTable = tableService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật bàn ăn thành công", diningTable));
    }

    @PostMapping("/{id}/qr")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DiningTable>> generateQr(@PathVariable Integer id) {
        DiningTable diningTable = tableService.generateQr(id);
        return ResponseEntity.ok(ApiResponse.success("Tạo mã QR cho bàn ăn thành công", diningTable));
    }

    @PatchMapping("/{id}/qr/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DiningTable>> updateQrStatus(@PathVariable Integer id,
                                                                    @Valid @RequestBody QrStatusUpdateRequest request) {
        DiningTable diningTable = tableService.updateQrStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái QR thành công", diningTable));
    }

    /** Chuyển toàn bộ đơn đang mở của bàn nguồn sang một bàn trống khác. */
    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','WAITER')")
    public ResponseEntity<ApiResponse<TableArrangementResponse>> transfer(
            @PathVariable Integer id,
            @Valid @RequestBody TableTransferRequest request,
            Authentication authentication) {
        TableArrangementResponse result = tableArrangementService.transfer(
                id,
                request.maBanDich(),
                authentication.getName(),
                hasRole(authentication, "ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Chuyển bàn thành công", result));
    }

    /** Ghép nhiều bàn cùng khu vực thành một nhóm dùng chung một đơn. */
    @PostMapping("/merge")
    @PreAuthorize("hasAnyRole('ADMIN','WAITER')")
    public ResponseEntity<ApiResponse<TableArrangementResponse>> merge(
            @Valid @RequestBody TableMergeRequest request,
            Authentication authentication) {
        TableArrangementResponse result = tableArrangementService.merge(
                request,
                authentication.getName(),
                hasRole(authentication, "ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Ghép bàn thành công", result));
    }

    /** Tách nhóm bàn khi nhóm chưa có hoặc không còn đơn đang mở. */
    @DeleteMapping("/groups/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN','WAITER')")
    public ResponseEntity<ApiResponse<TableArrangementResponse>> unmerge(
            @PathVariable String groupId,
            Authentication authentication) {
        TableArrangementResponse result = tableArrangementService.unmerge(
                groupId,
                authentication.getName(),
                hasRole(authentication, "ROLE_ADMIN")
        );
        return ResponseEntity.ok(ApiResponse.success("Tách bàn thành công", result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        tableService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa bàn ăn thành công"));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private boolean isWaiterOnly(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        boolean waiter = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_WAITER".equals(authority.getAuthority()));
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return waiter && !admin;
    }
}
