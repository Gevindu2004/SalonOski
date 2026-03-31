package com.saloonx.saloonx.service;

import com.saloonx.saloonx.model.Notification;
import com.saloonx.saloonx.model.User;
import com.saloonx.saloonx.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    public void sendBroadcastNotification(String message) {
        Notification notification = new Notification();
        notification.setMessage(message);
        notification.setUser(null); // Broadcast
        notificationRepository.save(notification);
    }

    public void sendUserNotification(String message, User user) {
        Notification notification = new Notification();
        notification.setMessage(message);
        notification.setUser(user);
        notificationRepository.save(notification);
    }

    public List<Notification> getNotificationsForUser(User user) {
        // Broadcast notifications plus user-specific ones
        return notificationRepository.findByUserOrUserIsNullOrderByCreatedAtDesc(user);
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll(
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt")));
    }

    public void deleteNotification(Long id) {
        notificationRepository.deleteById(id);
    }
}
