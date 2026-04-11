package com.saloonx.saloonx.repository;

import com.saloonx.saloonx.model.Beautician;
import com.saloonx.saloonx.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeauticianRepository extends JpaRepository<Beautician, Long> {
    Optional<Beautician> findByUser(User user);

    Optional<Beautician> findByUserId(Long userId);

    Optional<Beautician> findByEmail(String email);

    List<Beautician> findByIsAvailableTrue();

    List<Beautician> findBySpecializationContainingIgnoreCase(String specialization);

    List<Beautician> findByApprovalStatusOrderByApprovalRequestedAtAsc(String approvalStatus);

    @Query("SELECT b FROM Beautician b WHERE b.rating >= :minRating")
    List<Beautician> findByMinRating(@Param("minRating") Double minRating);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.beautician.id = :beauticianId AND a.status = 'COMPLETED'")
    Long countCompletedAppointments(@Param("beauticianId") Long beauticianId);
}
