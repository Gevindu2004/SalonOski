package com.saloonx.saloonx.repository;

import com.saloonx.saloonx.model.CompanyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyLogRepository extends JpaRepository<CompanyLog, Long> {
    List<CompanyLog> findAllByOrderByCreatedAtDesc();
}
