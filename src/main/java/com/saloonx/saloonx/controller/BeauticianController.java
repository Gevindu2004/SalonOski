package com.saloonx.saloonx.controller;

import com.saloonx.saloonx.model.Appointment;
import com.saloonx.saloonx.model.Beautician;
import com.saloonx.saloonx.model.User;
import com.saloonx.saloonx.repository.AppointmentRepository;
import com.saloonx.saloonx.service.BeauticianService;
import com.saloonx.saloonx.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/beautician")
public class BeauticianController {

    @Autowired
    private BeauticianService beauticianService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private User getUser(HttpSession session) {
        return (User) session.getAttribute("user");
    }

    private boolean isBeautician(HttpSession session) {
        User user = getUser(session);
        return user != null && "BEAUTICIAN".equals(user.getRole());
    }

    private Beautician getBeauticianFromSession(HttpSession session) {
        User user = getUser(session);
        if (user == null) {
            return null;
        }
        return beauticianService.getBeauticianByUserId(user.getId()).orElse(null);
    }

    private boolean requireApprovedBeautician(HttpSession session, RedirectAttributes redirectAttributes) {
        Beautician beautician = getBeauticianFromSession(session);
        if (beautician == null) {
            redirectAttributes.addFlashAttribute("error", "Complete your beautician profile first.");
            return false;
        }
        if (!beauticianService.isApproved(beautician)) {
            redirectAttributes.addFlashAttribute("error", "Your account must be approved by admin before accessing this area.");
            return false;
        }
        return true;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        if (!isBeautician(session)) {
            return "redirect:/login";
        }
        if (!requireApprovedBeautician(session, redirectAttributes)) {
            return "redirect:/beautician/profile";
        }

        Beautician beautician = getBeauticianFromSession(session);
        List<Appointment> todaysAppointments = beauticianService.getTodaysAppointments(beautician.getId());
        List<Appointment> pendingAppointments = beauticianService.getPendingAppointmentsForBeautician(beautician.getId());
        List<Appointment> allAppointments = beauticianService.getBeauticianAppointments(beautician.getId());

        long completedCount = allAppointments.stream().filter(a -> "COMPLETED".equals(a.getStatus())).count();
        long confirmedCount = allAppointments.stream().filter(a -> "CONFIRMED".equals(a.getStatus())).count();
        long cancelledCount = allAppointments.stream().filter(a -> "CANCELLED".equals(a.getStatus())).count();

        LocalDate today = LocalDate.now();
        Map<LocalDate, List<Appointment>> appointmentsByDate = allAppointments.stream()
                .filter(a -> a.getAppointmentDate().isAfter(today.minusDays(1)) && a.getAppointmentDate().isBefore(today.plusDays(7)))
                .collect(Collectors.groupingBy(Appointment::getAppointmentDate));

        model.addAttribute("beautician", beautician);
        model.addAttribute("todaysAppointments", todaysAppointments != null ? todaysAppointments : Collections.emptyList());
        model.addAttribute("pendingAppointments", pendingAppointments != null ? pendingAppointments : Collections.emptyList());
        model.addAttribute("totalAppointments", allAppointments.size());
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("confirmedCount", confirmedCount);
        model.addAttribute("cancelledCount", cancelledCount);
        model.addAttribute("appointmentsByDate", appointmentsByDate);
        model.addAttribute("today", today);
        model.addAttribute("notifications", notificationService.getNotificationsForUser(beautician.getUser()));
        return "beautician-dashboard";
    }

    @GetMapping("/profile/create")
    public String showCreateProfileForm(HttpSession session, Model model) {
        User user = getUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!"BEAUTICIAN".equals(user.getRole())) {
            return "redirect:/home";
        }
        if (beauticianService.getBeauticianByUserId(user.getId()).isPresent()) {
            return "redirect:/beautician/profile";
        }
        model.addAttribute("user", user);
        return "beautician-create-profile";
    }

    @PostMapping("/profile/create")
    public String createProfile(@RequestParam String phone,
                                @RequestParam String specialization,
                                @RequestParam(required = false, defaultValue = "") String bio,
                                @RequestParam Integer experience,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        User user = getUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        try {
            beauticianService.createBeauticianProfile(user, phone, specialization, bio, experience);
            redirectAttributes.addFlashAttribute("success", "Beautician profile created. Admin approval is required before you can manage bookings.");
            return "redirect:/beautician/profile";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create profile: " + e.getMessage());
            return "redirect:/beautician/profile/create";
        }
    }

    @GetMapping("/profile")
    public String viewProfile(HttpSession session, Model model) {
        if (!isBeautician(session)) {
            return "redirect:/login";
        }

        Beautician beautician = getBeauticianFromSession(session);
        if (beautician == null) {
            return "redirect:/beautician/profile/create";
        }

        model.addAttribute("beautician", beautician);
        return "beautician-profile";
    }

    @GetMapping("/profile/edit")
    public String editProfile(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        if (!isBeautician(session)) {
            return "redirect:/login";
        }

        Beautician beautician = getBeauticianFromSession(session);
        if (beautician == null) {
            return "redirect:/beautician/profile/create";
        }

        model.addAttribute("beautician", beautician);
        return "beautician-edit-profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam(required = false) String phone,
                                @RequestParam(required = false) String specialization,
                                @RequestParam(required = false) String bio,
                                @RequestParam(required = false) Integer experience,
                                @RequestParam(required = false) Boolean isAvailable,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        if (!isBeautician(session)) {
            return "redirect:/login";
        }

        Beautician beautician = getBeauticianFromSession(session);
        if (beautician == null) {
            return "redirect:/beautician/profile/create";
        }

        try {
            Beautician updated = beauticianService.updateBeauticianProfile(
                    beautician.getId(), phone, specialization, bio, experience, isAvailable);
            if ("PENDING".equals(updated.getApprovalStatus())) {
                redirectAttributes.addFlashAttribute("success", "Profile updated and queued for admin review.");
            } else {
                redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update profile: " + e.getMessage());
        }

        return "redirect:/beautician/profile";
    }

    @GetMapping("/appointments")
    public String viewAppointments(@RequestParam(required = false) String status,
                                   @RequestParam(required = false) String date,
                                   HttpSession session,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        if (!isBeautician(session)) {
            return "redirect:/login";
        }
        if (!requireApprovedBeautician(session, redirectAttributes)) {
            return "redirect:/beautician/profile";
        }

        Beautician beautician = getBeauticianFromSession(session);
        List<Appointment> appointments;

        if (status != null && !status.isEmpty()) {
            appointments = beauticianService.getBeauticianAppointmentsByStatus(beautician.getId(), status.toUpperCase());
        } else if (date != null && !date.isEmpty()) {
            LocalDate filterDate = LocalDate.parse(date);
            appointments = appointmentRepository.findByBeauticianIdAndAppointmentDate(beautician.getId(), filterDate);
        } else {
            appointments = beauticianService.getBeauticianAppointments(beautician.getId());
        }

        appointments.sort((a1, a2) -> {
            int dateCompare = a1.getAppointmentDate().compareTo(a2.getAppointmentDate());
            return dateCompare != 0 ? dateCompare : a1.getAppointmentTime().compareTo(a2.getAppointmentTime());
        });

        model.addAttribute("beautician", beautician);
        model.addAttribute("appointments", appointments != null ? appointments : Collections.emptyList());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedDate", date);
        return "beautician-appointments";
    }

    @PostMapping("/appointment/update-status/{id}")
    public String updateAppointmentStatus(@PathVariable Long id,
                                          @RequestParam String status,
                                          HttpSession session,
                                          RedirectAttributes redirectAttributes) {
        if (!isBeautician(session)) {
            return "redirect:/login";
        }
        if (!requireApprovedBeautician(session, redirectAttributes)) {
            return "redirect:/beautician/profile";
        }

        Beautician beautician = getBeauticianFromSession(session);
        try {
            beauticianService.updateAppointmentStatus(id, beautician.getId(), status);
            redirectAttributes.addFlashAttribute("success", "Appointment " + status.toLowerCase() + " successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update appointment: " + e.getMessage());
        }
        return "redirect:/beautician/dashboard";
    }

    @GetMapping("/appointment/{id}")
    public String viewAppointmentDetails(@PathVariable Long id,
                                         HttpSession session,
                                         Model model,
                                         RedirectAttributes redirectAttributes) {
        if (!isBeautician(session)) {
            return "redirect:/login";
        }
        if (!requireApprovedBeautician(session, redirectAttributes)) {
            return "redirect:/beautician/profile";
        }

        Beautician beautician = getBeauticianFromSession(session);
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid appointment Id:" + id));

        if (appointment.getBeautician() == null || !appointment.getBeautician().getId().equals(beautician.getId())) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to view this appointment");
            return "redirect:/beautician/appointments";
        }

        model.addAttribute("beautician", beautician);
        model.addAttribute("appointment", appointment);
        return "beautician-appointment-details";
    }

    @GetMapping("/schedule")
    public String viewSchedule(@RequestParam(required = false) String week,
                               HttpSession session,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (!isBeautician(session)) {
            return "redirect:/login";
        }
        if (!requireApprovedBeautician(session, redirectAttributes)) {
            return "redirect:/beautician/profile";
        }

        Beautician beautician = getBeauticianFromSession(session);
        LocalDate startDate = (week != null && !week.isEmpty()) ? LocalDate.parse(week) : LocalDate.now();
        LocalDate endDate = startDate.plusDays(6);

        List<Appointment> weekAppointments = beauticianService.getBeauticianAppointments(beautician.getId()).stream()
                .filter(a -> !a.getAppointmentDate().isBefore(startDate) && !a.getAppointmentDate().isAfter(endDate))
                .collect(Collectors.toList());

        Map<LocalDate, List<Appointment>> appointmentsByDate = weekAppointments.stream()
                .collect(Collectors.groupingBy(Appointment::getAppointmentDate));

        model.addAttribute("beautician", beautician);
        model.addAttribute("appointmentsByDate", appointmentsByDate);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("previousWeek", startDate.minusDays(7));
        model.addAttribute("nextWeek", startDate.plusDays(7));
        return "beautician-schedule";
    }

    @GetMapping("/statistics")
    public String viewStatistics(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        if (!isBeautician(session)) {
            return "redirect:/login";
        }
        if (!requireApprovedBeautician(session, redirectAttributes)) {
            return "redirect:/beautician/profile";
        }

        Beautician beautician = getBeauticianFromSession(session);
        List<Appointment> allAppointments = beauticianService.getBeauticianAppointments(beautician.getId());

        long totalAppointments = allAppointments.size();
        long completedAppointments = allAppointments.stream().filter(a -> "COMPLETED".equals(a.getStatus())).count();
        long confirmedAppointments = allAppointments.stream().filter(a -> "CONFIRMED".equals(a.getStatus())).count();
        long cancelledAppointments = allAppointments.stream().filter(a -> "CANCELLED".equals(a.getStatus())).count();
        long pendingAppointments = allAppointments.stream().filter(a -> "PENDING".equals(a.getStatus())).count();

        Map<String, Long> appointmentsByMonth = allAppointments.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getAppointmentDate().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()));

        Map<String, Long> serviceCounts = allAppointments.stream()
                .collect(Collectors.groupingBy(Appointment::getService, Collectors.counting()));

        model.addAttribute("beautician", beautician);
        model.addAttribute("totalAppointments", totalAppointments);
        model.addAttribute("completedAppointments", completedAppointments);
        model.addAttribute("confirmedAppointments", confirmedAppointments);
        model.addAttribute("cancelledAppointments", cancelledAppointments);
        model.addAttribute("pendingAppointments", pendingAppointments);
        model.addAttribute("completionRate", totalAppointments > 0 ? (completedAppointments * 100.0 / totalAppointments) : 0);
        model.addAttribute("appointmentsByMonth", appointmentsByMonth);
        model.addAttribute("serviceCounts", serviceCounts);
        return "beautician-statistics";
    }
}
