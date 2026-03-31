package com.saloonx.saloonx.repository;

import com.saloonx.saloonx.model.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {
    List<StockTransaction> findTop15ByOrderByCreatedAtDesc();
    List<StockTransaction> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
}
