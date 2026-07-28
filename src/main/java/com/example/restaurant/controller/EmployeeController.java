package com.example.restaurant.controller;

import com.example.restaurant.dto.ApiResponse;
import com.example.restaurant.dto.EmployeeRequest;
import com.example.restaurant.dto.PageResponse;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeController {
    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Employee>>> allEmployees() {
        List<Employee> employees = employeeService.findAll();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách nhân viên thành công", employees));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponse<Employee>>> pagedEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        PageResponse<Employee> result = PageResponse.from(
                employeeService.findPage(page, size, keyword, role, status)
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách nhân viên phân trang thành công", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Employee>> detail(@PathVariable Integer id) {
        Employee employee = employeeService.findById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết nhân viên thành công", employee));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Employee>> create(@Valid @RequestBody EmployeeRequest request) {
        Employee employee = employeeService.create(request);
        return ResponseEntity.ok(ApiResponse.success("Thêm nhân viên thành công", employee));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Employee>> update(@PathVariable Integer id, @Valid @RequestBody EmployeeRequest request) {
        Employee employee = employeeService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật nhân viên thành công", employee));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        employeeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa nhân viên thành công"));
    }
}
