package com.example.restaurant.service;

import com.example.restaurant.dto.CustomerAuthResponse;
import com.example.restaurant.dto.GoogleLoginRequest;
import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.Role;
import com.example.restaurant.repository.EmployeeRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthGoogleRoutingTest {
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final EmployeeDetailsService details = mock(EmployeeDetailsService.class);
    private final CustomerAccountService customers = mock(CustomerAccountService.class);
    private final JwtService jwt = mock(JwtService.class);
    private final GoogleTokenService google = mock(GoogleTokenService.class);
    private final AuthService service = new AuthService(mock(AuthenticationManager.class), employees,
            details, customers, jwt, google, mock(LoginAttemptService.class));

    private GoogleIdToken.Payload verified() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("subject");
        payload.setEmail("user@gmail.com");
        payload.setEmailVerified(true);
        when(google.verify("valid")).thenReturn(payload);
        return payload;
    }

    @Test
    void nonEmployeeReceivesOnlyCustomerRoleAndCustomerToken() {
        var payload = verified();
        when(customers.loginWithGoogle(payload)).thenReturn(
                new CustomerAuthResponse("customer-jwt", 42, "Khách", null, 0));
        var response = service.loginWithGoogle(new GoogleLoginRequest("valid"));
        assertEquals("CUSTOMER", response.role());
        assertEquals("customer-jwt", response.token());
        assertEquals("customer:42", response.username());
        assertEquals(42, response.maKhachHang());
        assertNull(response.maNhanVien());
        verifyNoInteractions(details, jwt);
    }

    @Test
    void employeeKeepsExistingRoleAndDoesNotCreateCustomer() {
        verified();
        Employee employee = new Employee();
        employee.setMaNhanVien(10);
        employee.setTenDangNhap("waiter01");
        employee.setVaiTro(new Role("WAITER"));
        when(employees.findByEmailIgnoreCase("user@gmail.com")).thenReturn(Optional.of(employee));
        var principal = User.withUsername("waiter01").password("unused").roles("WAITER").build();
        when(details.loadUserByUsername("waiter01")).thenReturn(principal);
        when(jwt.generateToken(principal)).thenReturn("employee-jwt");
        var response = service.loginWithGoogle(new GoogleLoginRequest("valid"));
        assertEquals("WAITER", response.role());
        assertEquals("employee-jwt", response.token());
        assertNull(response.maKhachHang());
        verifyNoInteractions(customers);
    }

    @Test
    void disabledEmployeeCannotFallBackToCustomer() {
        verified();
        Employee employee = new Employee();
        employee.setTrangThai("NGHI_VIEC");
        when(employees.findByEmailIgnoreCase("user@gmail.com")).thenReturn(Optional.of(employee));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.loginWithGoogle(new GoogleLoginRequest("valid")));
        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(customers, jwt);
    }

    @Test
    void invalidGoogleTokenNeverReachesAccountLookupOrCreation() {
        when(google.verify("invalid")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertThrows(ResponseStatusException.class,
                () -> service.loginWithGoogle(new GoogleLoginRequest("invalid")));
        verifyNoInteractions(employees, customers, jwt);
    }
}
