package com.lawai.persistence.repository;

import com.lawai.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {

  Optional<UserEntity> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);
}
