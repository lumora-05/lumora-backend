package com.example.restaurant.repository;

import com.example.restaurant.entity.Employee;
import com.example.restaurant.entity.PasswordResetCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findTopByEmployeeOrderBySentAtDesc(Employee employee);

    void deleteByEmployee_MaNhanVien(Integer maNhanVien);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetCode> findTopByEmployee_EmailIgnoreCaseAndUsedAtIsNullOrderByCreatedAtDesc(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from PasswordResetCode c
            join fetch c.employee
            where c.resetTokenHash = :tokenHash
              and c.usedAt is null
            """)
    Optional<PasswordResetCode> findActiveByResetTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update PasswordResetCode c
               set c.usedAt = :usedAt
             where c.employee.maNhanVien = :employeeId
               and c.usedAt is null
            """)
    int invalidateActiveByEmployeeId(@Param("employeeId") Integer employeeId,
                                     @Param("usedAt") LocalDateTime usedAt);

    @Modifying
    @Query("""
            update PasswordResetCode c
               set c.usedAt = :usedAt
             where c.employee.maNhanVien = :employeeId
               and c.usedAt is null
               and c.id <> :currentId
            """)
    int invalidateOtherActiveByEmployeeId(@Param("employeeId") Integer employeeId,
                                          @Param("currentId") Long currentId,
                                          @Param("usedAt") LocalDateTime usedAt);
}
