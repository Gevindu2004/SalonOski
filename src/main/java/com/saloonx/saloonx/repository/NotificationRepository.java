package com.saloonx.saloonx.repository;

import com.saloonx.saloonx.model.Notification;
import com.saloonx.saloonx.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIsNullOrderByCreatedAtDesc();

    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    List<Notification> findByUserOrUserIsNullOrderByCreatedAtDesc(User user);

    void deleteByUserId(Long userId);
}
