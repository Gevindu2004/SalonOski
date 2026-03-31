package com.saloonx.saloonx.controller;

import com.saloonx.saloonx.model.Beautician;
import com.saloonx.saloonx.model.CompanyLog;
import com.saloonx.saloonx.model.User;
import com.saloonx.saloonx.service.AppointmentService;
import com.saloonx.saloonx.service.BeauticianService;
import com.saloonx.saloonx.service.CompanyLogService;
import com.saloonx.saloonx.service.FeedbackService;
import com.saloonx.saloonx.service.InventoryService;
import com.saloonx.saloonx.service.NotificationService;
import com.saloonx.saloonx.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private CompanyLogService logService;

    @Autowired
    private UserService userService;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private BeauticianService beauticianService;

    @Autowired
    private InventoryService inventoryService;

    private boolean isAdmin(HttpSession session) {
        User user = (User) session.getAttribute("user");
        return user != null && "ADMIN".equals(user.getRole());
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        List<User> users = userService.getAllUsers();
        List<Beautician> pendingBeauticians = beauticianService.getPendingApprovalBeauticians();
        int totalLoyaltyPoints = users.stream()
                .mapToInt(user -> user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints()).sum();
        long activeCustomers = users.stream()
                .filter(user -> "CUSTOMER".equals(user.getRole()) && "ACTIVE".equals(user.getAccountStatus())).count();

        model.addAttribute("totalUsers", users.size());
        model.addAttribute("totalAppointments", appointmentService.getAllAppointments().size());
        model.addAttribute("totalBeauticians", userService.countUsersByRole("BEAUTICIAN"));
        model.addAttribute("totalFeedback", feedbackService.getAllFeedback().size());
        model.addAttribute("pendingBeauticianApprovals", pendingBeauticians.size());
        model.addAttribute("activeCustomers", activeCustomers);
        model.addAttribute("totalLoyaltyPoints", totalLoyaltyPoints);
        model.addAttribute("pendingBeauticians", pendingBeauticians);
        model.addAttribute("inventoryProductCount", inventoryService.getActiveProducts().size());
        model.addAttribute("inventoryLowStockCount", inventoryService.getDashboardData().get("lowStockCount"));
        return "admin/dashboard";
    }

    @GetMapping("/appointments")
    public String listAppointments(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        model.addAttribute("appointments", appointmentService.getAllAppointments());
        return "admin/appointments";
    }

    @PostMapping("/appointment/status/{id}")
    public String updateAppointmentStatus(@PathVariable Long id, @RequestParam String status, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }

        com.saloonx.saloonx.model.Appointment appointment = appointmentService.getAppointmentById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid appointment Id:" + id));
        
        // If the appointment is already assigned to a beautician, use the centralized service method
        // which handles Rewards, Inventory, Notifications, and Income tracking.
        if (appointment.getBeautician() != null) {
            try {
                beauticianService.updateAppointmentStatus(id, appointment.getBeautician().getId(), status);
            } catch (Exception e) {
                // If it fails (e.g. invalid status transition), do a direct update
                appointment.setStatus(status);
                appointmentService.saveAppointment(appointment);
            }
        } else {
            // Unassigned appointment: just update status and record income if completed
            appointment.setStatus(status);
            appointmentService.saveAppointment(appointment);
            
            if ("COMPLETED".equals(status)) {
                // Still record income even if no beautician was assigned
                com.saloonx.saloonx.service.AccountingService accountingService = 
                    org.springframework.web.context.support.WebApplicationContextUtils
                    .getWebApplicationContext(session.getServletContext())
                    .getBean(com.saloonx.saloonx.service.AccountingService.class);
                if (accountingService != null) {
                    accountingService.recordServiceIncome(appointment);
                }
            }
        }
        
        return "redirect:/admin/appointments";
    }

    @PostMapping("/appointment/delete/{id}")
    public String deleteAppointment(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        appointmentService.deleteAppointment(id);
        return "redirect:/admin/appointments";
    }

    @GetMapping("/logs")
    public String listLogs(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        model.addAttribute("logs", logService.getAllLogs());
        return "admin/logs";
    }

    @GetMapping("/users")
    public String listUsers(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("pendingBeauticians", beauticianService.getPendingApprovalBeauticians());
        model.addAttribute("totalLoyaltyPoints",
                users.stream().mapToInt(user -> user.getLoyaltyPoints() == null ? 0 : user.getLoyaltyPoints()).sum());
        return "admin/users";
    }

    @PostMapping("/beautician/approve/{id}")
    public String approveBeautician(@PathVariable Long id,
            @RequestParam(required = false) String notes,
            HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        User admin = (User) session.getAttribute("user");
        beauticianService.approveBeautician(id, admin, notes);
        return "redirect:/admin/users?approved";
    }

    @PostMapping("/beautician/reject/{id}")
    public String rejectBeautician(@PathVariable Long id,
            @RequestParam(required = false) String notes,
            HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        User admin = (User) session.getAttribute("user");
        beauticianService.rejectBeautician(id, admin, notes);
        return "redirect:/admin/users?rejected";
    }

    @PostMapping("/user/update-role/{id}")
    public String updateRole(@PathVariable Long id, @RequestParam String role, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        User user = userService.getUserById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));

        if ("ADMIN".equals(user.getRole())) {
            return "redirect:/admin/users?error=cannot_modify_admin";
        }

        user.setRole(role);
        user.setAccountStatus("BEAUTICIAN".equals(role) ? "PENDING_APPROVAL" : "ACTIVE");
        userService.updateUser(user, null);
        return "redirect:/admin/users";
    }

    @PostMapping("/user/delete/{id}")
    public String adminDeleteUser(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        User userToDelete = userService.getUserById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));

        if ("ADMIN".equals(userToDelete.getRole())) {
            return "redirect:/admin/users?error=cannot_delete_admin";
        }

        userService.adminDeleteUser(id);
        return "redirect:/admin/users?deleted";
    }

    @PostMapping("/user/approve/{id}")
    public String approveUserDirectly(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        User userToApprove = userService.getUserById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));

        userToApprove.setAccountStatus("ACTIVE");
        userService.updateUser(userToApprove, null);

        // Also approve their beautician profile if it already exists
        User admin = (User) session.getAttribute("user");
        beauticianService.getBeauticianByUserId(id).ifPresent(beautician -> beauticianService
                .approveBeautician(beautician.getId(), admin, "Directly approved from user table."));

        return "redirect:/admin/users?approved";
    }

    @GetMapping("/log/create")
    public String showCreateForm(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        model.addAttribute("log", new CompanyLog());
        model.addAttribute("title", "Create Company Log");
        return "admin/log-form";
    }

    @PostMapping("/log/save")
    public String saveLog(@ModelAttribute CompanyLog log, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        User user = (User) session.getAttribute("user");
        log.setAdminEmail(user.getEmail());
        logService.saveLog(log);
        return "redirect:/admin/logs";
    }

    @GetMapping("/log/edit/{id}")
    public String showEditForm(@PathVariable Long id, HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        CompanyLog log = logService.getLogById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid log Id:" + id));
        model.addAttribute("log", log);
        model.addAttribute("title", "Edit Company Log");
        return "admin/log-form";
    }

    @GetMapping("/log/delete/{id}")
    public String deleteLog(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        logService.deleteLog(id);
        return "redirect:/admin/logs";
    }

    @GetMapping("/feedback")
    public String listFeedbacks(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        model.addAttribute("feedbacks", feedbackService.getAllFeedback());
        return "admin/feedback";
    }

    @PostMapping("/feedback/reply/{id}")
    public String saveReply(@PathVariable Long id, @RequestParam String reply, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        com.saloonx.saloonx.model.Feedback feedback = feedbackService.getFeedbackById(id);
        if (feedback != null) {
            feedback.setAdminReply(reply);
            feedbackService.saveFeedback(feedback);
        }
        return "redirect:/admin/feedback";
    }

    @GetMapping("/feedback/delete/{id}")
    public String deleteFeedback(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        feedbackService.deleteFeedback(id);
        return "redirect:/admin/feedback";
    }

    @GetMapping("/notifications")
    public String listNotifications(HttpSession session, Model model) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        model.addAttribute("notifications", notificationService.getAllNotifications());
        model.addAttribute("users", userService.getAllUsers());
        return "admin/notifications";
    }

    @PostMapping("/notifications/send")
    public String sendNotification(@RequestParam String message,
            @RequestParam(required = false) Long userId,
            HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        if (userId != null) {
            userService.getUserById(userId).ifPresent(user -> notificationService.sendUserNotification(message, user));
        } else {
            notificationService.sendBroadcastNotification(message);
        }
        return "redirect:/admin/notifications?sent";
    }

    @GetMapping("/notifications/delete/{id}")
    public String deleteNotification(@PathVariable Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return "redirect:/login";
        }
        notificationService.deleteNotification(id);
        return "redirect:/admin/notifications?deleted";
    }
}
