package com.saloonx.saloonx.controller;

import com.saloonx.saloonx.model.Notification;
import com.saloonx.saloonx.model.User;
import com.saloonx.saloonx.service.ConfiguredOAuthProviderService;
import com.saloonx.saloonx.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.util.List;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ConfiguredOAuthProviderService configuredOAuthProviderService;

    @ModelAttribute
    public void addAttributes(HttpSession session, Model model) {
        model.addAttribute("availableOAuthProviders", configuredOAuthProviderService.getAvailableProviders());
        model.addAttribute("oauthEnabled", !configuredOAuthProviderService.getAvailableProviders().isEmpty());
        try {
            User user = (User) session.getAttribute("user");
            if (user != null) {
                List<Notification> notes = notificationService.getNotificationsForUser(user);
                model.addAttribute("notificationCount", notes != null ? notes.size() : 0);
            }
        } catch (Exception e) {
            model.addAttribute("notificationCount", 0);
        }
    }
}
