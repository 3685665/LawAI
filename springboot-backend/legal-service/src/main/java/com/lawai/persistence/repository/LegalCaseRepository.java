package com.lawai.persistence.repository;

import com.lawai.persistence.entity.LegalCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalCaseRepository extends JpaRepository<LegalCaseEntity, String> {
}
