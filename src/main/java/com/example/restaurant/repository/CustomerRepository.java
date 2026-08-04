package com.example.restaurant.repository;

import com.example.restaurant.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Integer>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findBySoDienThoai(String soDienThoai);

    boolean existsBySoDienThoai(String soDienThoai);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.soDienThoai = :phone")
    Optional<Customer> findBySoDienThoaiForUpdate(@Param("phone") String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.maKhachHang = :customerId")
    Optional<Customer> findByIdForUpdate(@Param("customerId") Integer customerId);

    Page<Customer> findAllByOrderByThoiGianCapNhatDesc(Pageable pageable);
}
