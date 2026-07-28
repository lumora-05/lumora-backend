package com.example.restaurant.repository;

import com.example.restaurant.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByTenVaiTro(String tenVaiTro);
}
