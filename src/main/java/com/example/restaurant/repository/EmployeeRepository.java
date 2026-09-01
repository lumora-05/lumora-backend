package com.example.restaurant.repository;

import com.example.restaurant.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Integer>, JpaSpecificationExecutor<Employee> {
    Optional<Employee> findByTenDangNhap(String tenDangNhap);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.tenDangNhap = :username")
    Optional<Employee> findByTenDangNhapForUpdate(@Param("username") String username);
    Optional<Employee> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where lower(e.email) = lower(:email)")
    Optional<Employee> findByEmailIgnoreCaseForUpdate(@Param("email") String email);
    boolean existsByTenDangNhap(String tenDangNhap);
    boolean existsByTenDangNhapIgnoreCase(String tenDangNhap);
    boolean existsByTenDangNhapIgnoreCaseAndMaNhanVienNot(String tenDangNhap, Integer maNhanVien);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndMaNhanVienNot(String email, Integer maNhanVien);
}
