package com.example.restaurant.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CustomerGoogleJwtTest {
    @Test
    void tokenWithoutPhoneStillIdentifiesCustomerAndExistingPhoneSubjectIsUnchanged() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(service, "expirationMs", 60000L);
        String googleToken = service.generateCustomerToken(42, null);
        assertEquals("CUSTOMER", service.extractTokenType(googleToken));
        assertEquals(42, service.extractCustomerId(googleToken));
        assertEquals("customer:42", service.extractUsername(googleToken));
        String phoneToken = service.generateCustomerToken(7, "0901234567");
        assertEquals("0901234567", service.extractUsername(phoneToken));
        assertEquals(7, service.extractCustomerId(phoneToken));
    }
}
