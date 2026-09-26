package com.example.restaurant.service;

import com.example.restaurant.dto.CustomerProfileUpdateRequest;
import com.example.restaurant.dto.CustomerRegisterRequest;
import com.example.restaurant.entity.Customer;
import com.example.restaurant.repository.CustomerRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerGoogleLoginTest {
    private final CustomerRepository repository = mock(CustomerRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final CustomerAccountService service = new CustomerAccountService(
            repository, encoder, jwt, mock(LoginAttemptService.class));

    @BeforeEach
    void setup() {
        when(repository.saveAndFlush(any(Customer.class))).thenAnswer(call -> {
            Customer customer = call.getArgument(0);
            if (customer.getMaKhachHang() == null) customer.setMaKhachHang(42);
            return customer;
        });
    }

    private GoogleIdToken.Payload payload(String subject, String email) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(subject);
        payload.setEmail(email);
        payload.setEmailVerified(true);
        payload.set("name", "Nguyễn Văn An");
        return payload;
    }

    @Test
    void firstLoginCreatesCustomerWithoutInventingPhoneOrPassword() {
        when(jwt.generateCustomerToken(42, null)).thenReturn("customer-jwt");
        var response = service.loginWithGoogle(payload("google-123", "AN@gmail.com"));
        assertEquals(42, response.maKhachHang());
        assertNull(response.soDienThoai());
        assertEquals("customer-jwt", response.token());
        verify(repository).saveAndFlush(argThat(customer ->
                "google-123".equals(customer.getGoogleSubject())
                        && "an@gmail.com".equals(customer.getGoogleEmail())
                        && "Nguyễn Văn An".equals(customer.getHoTen())
                        && "HOAT_DONG".equals(customer.getTrangThai())
                        && customer.getMatKhauHash() == null
                        && customer.getDiemTichLuy() == 0));
        verifyNoInteractions(encoder);
    }

    @Test
    void repeatLoginUsesSubjectAndPreservesProfileAndPointsWhenEmailChanges() {
        Customer customer = new Customer();
        customer.setMaKhachHang(7);
        customer.setGoogleSubject("stable-subject");
        customer.setHoTen("Tên khách đã sửa");
        customer.setSoDienThoai("0901234567");
        customer.setDiemTichLuy(125);
        when(repository.findByGoogleSubjectForUpdate("stable-subject")).thenReturn(Optional.of(customer));
        var response = service.loginWithGoogle(payload("stable-subject", "new@gmail.com"));
        assertEquals(7, response.maKhachHang());
        assertEquals("Tên khách đã sửa", response.hoTen());
        assertEquals("0901234567", response.soDienThoai());
        assertEquals(125, response.diemTichLuy());
        assertEquals("new@gmail.com", customer.getGoogleEmail());
    }

    @Test
    void disabledGoogleCustomerCannotLogin() {
        Customer customer = new Customer();
        customer.setTrangThai("NGUNG_HOAT_DONG");
        when(repository.findByGoogleSubjectForUpdate("disabled")).thenReturn(Optional.of(customer));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.loginWithGoogle(payload("disabled", "a@gmail.com")));
        assertEquals(403, error.getStatusCode().value());
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(jwt);
    }

    @Test
    void missingSubjectOrUnverifiedEmailCannotCreateCustomer() {
        assertThrows(ResponseStatusException.class,
                () -> service.loginWithGoogle(payload(null, "a@gmail.com")));
        var unverified = payload("subject", "a@gmail.com");
        unverified.setEmailVerified(false);
        assertThrows(ResponseStatusException.class, () -> service.loginWithGoogle(unverified));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void missingNameHasFallbackAndLongNameFitsColumn() {
        var missing = payload("one", "a@gmail.com");
        missing.set("name", null);
        assertEquals("Khách hàng Google", service.loginWithGoogle(missing).hoTen());
        var longName = payload("two", "b@gmail.com");
        longName.set("name", "a".repeat(150));
        assertEquals(100, service.loginWithGoogle(longName).hoTen().length());
    }

    @Test
    void publicRegistrationCannotSetPasswordOnExistingGoogleAccount() {
        Customer customer = new Customer();
        customer.setGoogleSubject("google-123");
        when(repository.findBySoDienThoaiForUpdate("0901234567")).thenReturn(Optional.of(customer));
        var error = assertThrows(ResponseStatusException.class, () -> service.register(
                new CustomerRegisterRequest("Other person", "0901234567", "password")));
        assertEquals(409, error.getStatusCode().value());
        verifyNoInteractions(encoder);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void authenticatedGoogleCustomerCanAddPhoneWithoutLosingGoogleIdentity() {
        Customer customer = new Customer();
        customer.setMaKhachHang(42);
        customer.setGoogleSubject("google-123");
        when(jwt.extractTokenType("valid")).thenReturn("CUSTOMER");
        when(jwt.extractCustomerId("valid")).thenReturn(42);
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(customer));
        var response = service.updateProfile("Bearer valid",
                new CustomerProfileUpdateRequest("Nguyễn Văn An", "0901234567"));
        assertEquals("0901234567", response.soDienThoai());
        assertEquals("google-123", customer.getGoogleSubject());
        verify(jwt).generateCustomerToken(42, "0901234567");
    }

    @Test
    void addingExistingPhoneDoesNotMergeAccounts() {
        Customer customer = new Customer();
        customer.setMaKhachHang(42);
        Customer other = new Customer();
        other.setMaKhachHang(9);
        when(jwt.extractTokenType("valid")).thenReturn("CUSTOMER");
        when(jwt.extractCustomerId("valid")).thenReturn(42);
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(customer));
        when(repository.findBySoDienThoaiForUpdate("0901234567")).thenReturn(Optional.of(other));
        var error = assertThrows(ResponseStatusException.class, () -> service.updateProfile("Bearer valid",
                new CustomerProfileUpdateRequest("Nguyễn Văn An", "0901234567")));
        assertEquals(409, error.getStatusCode().value());
        assertNull(customer.getSoDienThoai());
    }
}
