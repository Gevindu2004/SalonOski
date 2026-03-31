package com.saloonx.saloonx.repository;

import com.saloonx.saloonx.model.ServiceProductRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceProductRequirementRepository extends JpaRepository<ServiceProductRequirement, Long> {
    List<ServiceProductRequirement> findByServiceNameIgnoreCaseOrderByProductNameAsc(String serviceName);
    List<ServiceProductRequirement> findAllByOrderByServiceNameAsc();
}
