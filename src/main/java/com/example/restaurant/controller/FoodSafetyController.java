package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.BatchImpactResponse;
import com.example.restaurant.dto.BatchIncidentRequest;
import com.example.restaurant.dto.BatchIncidentResolveRequest;
import com.example.restaurant.dto.BatchIncidentResponse;
import com.example.restaurant.dto.OrderItemTraceResponse;
import com.example.restaurant.service.FoodTraceabilityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/food-safety")
@PreAuthorize("hasRole('ADMIN')")
public class FoodSafetyController {
    private final FoodTraceabilityService traceabilityService;

    public FoodSafetyController(FoodTraceabilityService traceabilityService) {
        this.traceabilityService = traceabilityService;
    }

    @GetMapping("/order-items/{itemId}/trace")
    public ResponseEntity<ApiResponse<OrderItemTraceResponse>> traceOrderItem(
            @PathVariable Integer itemId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Truy xuất lô nguyên liệu của món trong đơn thành công",
                traceabilityService.traceOrderItem(itemId)
        ));
    }

    @GetMapping("/batches/{batchId}/impact")
    public ResponseEntity<ApiResponse<BatchImpactResponse>> traceBatchImpact(
            @PathVariable Long batchId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Truy xuất ảnh hưởng của lô nguyên liệu thành công",
                traceabilityService.traceBatchImpact(batchId)
        ));
    }

    @PostMapping("/batches/{batchId}/incidents")
    public ResponseEntity<ApiResponse<BatchIncidentResponse>> reportIncident(
            @PathVariable Long batchId,
            @Valid @RequestBody BatchIncidentRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Báo cáo sự cố và khóa lô nguyên liệu thành công",
                traceabilityService.reportIncident(batchId, request, authentication.getName())
        ));
    }

    @GetMapping("/incidents")
    public ResponseEntity<ApiResponse<List<BatchIncidentResponse>>> findIncidents(
            @RequestParam(required = false) Long batchId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách sự cố lô nguyên liệu thành công",
                traceabilityService.findIncidents(batchId)
        ));
    }

    @PutMapping("/incidents/{incidentId}/resolve")
    public ResponseEntity<ApiResponse<BatchIncidentResponse>> resolveIncident(
            @PathVariable Long incidentId,
            @Valid @RequestBody BatchIncidentResolveRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật xử lý sự cố lô nguyên liệu thành công",
                traceabilityService.resolveIncident(incidentId, request, authentication.getName())
        ));
    }
}
