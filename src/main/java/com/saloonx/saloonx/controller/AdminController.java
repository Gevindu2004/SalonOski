package com.saloonx.saloonx.controller;

import com.saloonx.saloonx.model.Beautician;
import com.saloonx.saloonx.model.User;
import com.saloonx.saloonx.service.AppointmentService;
import com.saloonx.saloonx.service.BeauticianService;
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
    private UserService userService;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private BeauticianService beauticianService;

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
        model.addAttribute("pendingBeauticianApprovals", pendingBeauticians.size());
        model.addAttribute("activeCustomers", activeCustomers);
        model.addAttribute("totalLoyaltyPoints", totalLoyaltyPoints);
        model.addAttribute("pendingBeauticians", pendingBeauticians);
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
            // Unassigned appointment: just update status
            appointment.setStatus(status);
            appointmentService.saveAppointment(appointment);
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

}
