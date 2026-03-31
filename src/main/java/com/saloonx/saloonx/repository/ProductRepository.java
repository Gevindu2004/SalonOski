package com.saloonx.saloonx.repository;

import com.saloonx.saloonx.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true AND (
                LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(p.category, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(p.searchIndex, '')) LIKE LOWER(CONCAT('%', :query, '%'))
            )
            ORDER BY p.name ASC
            """)
    List<Product> searchActiveProducts(String query);

    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true AND COALESCE(p.currentStock, 0) <= COALESCE(p.reorderLevel, 0)
            ORDER BY p.currentStock ASC, p.name ASC
            """)
    List<Product> findLowStockProducts();

    List<Product> findByActiveTrueOrderByNameAsc();
}
