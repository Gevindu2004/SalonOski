package com.saloonx.saloonx.repository;

import com.saloonx.saloonx.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByUser(com.saloonx.saloonx.model.User user);

    List<Appointment> findByUserId(Long userId);

    List<Appointment> findByUserIdAndStatus(Long userId, String status);

    long countByUserIdAndStatus(Long userId, String status);

    List<Appointment> findByAppointmentDate(LocalDate date);

    List<Appointment> findByBeauticianId(Long beauticianId);

    List<Appointment> findByBeauticianIdAndStatus(Long beauticianId, String status);

    List<Appointment> findByBeauticianIdAndAppointmentDate(Long beauticianId, LocalDate date);

    @Query("SELECT a FROM Appointment a WHERE a.beautician.id = :beauticianId AND a.status = :status ORDER BY a.appointmentDate ASC, a.appointmentTime ASC")
    List<Appointment> findByBeauticianIdAndStatusOrderByDate(
            @Param("beauticianId") Long beauticianId,
            @Param("status") String status
    );

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.beautician.id = :beauticianId AND a.appointmentDate = :date AND a.status != 'CANCELLED'")
    Long countAppointmentsByBeauticianAndDate(
            @Param("beauticianId") Long beauticianId,
            @Param("date") LocalDate date
    );

    List<Appointment> findByBeauticianIsNullAndStatusOrderByAppointmentDateAscAppointmentTimeAsc(String status);
}
