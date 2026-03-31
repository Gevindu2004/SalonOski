package com.saloonx.saloonx.repository;

import com.saloonx.saloonx.model.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findTop10ByOrderByCreatedAtDesc();
    boolean existsByPoNumber(String poNumber);
}
