package com.saloonx.saloonx.controller;

import com.saloonx.saloonx.model.Appointment;
import com.saloonx.saloonx.model.Beautician;
import com.saloonx.saloonx.model.User;
import com.saloonx.saloonx.service.AppointmentService;
import com.saloonx.saloonx.service.BeauticianService;
import com.saloonx.saloonx.service.NotificationService;
import com.saloonx.saloonx.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class PageController {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserService userService;

    @Autowired
    private BeauticianService beauticianService;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/ai-hairstyle")
    public String aiHairstyle() {
        return "hairstyle-ai";
    }

    @GetMapping("/services")
    public String services() {
        return "services";
    }

    @GetMapping("/appointment")
    public String appointment(@RequestParam(required = false) String service, HttpSession session, Model model) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }
        if (service != null) {
            model.addAttribute("selectedService", service);
        }
        return "appointment";
    }

    @PostMapping("/appointment")
    public String bookAppointment(@RequestParam String name,
                                  @RequestParam String phone,
                                  @RequestParam String date,
                                  @RequestParam String time,
                                  @RequestParam String service,
                                  HttpSession session,
                                  Model model) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        try {
            Appointment appointment = new Appointment();
            appointment.setCustomerName(name);
            appointment.setCustomerPhone(phone);
            LocalDate appointmentDate = LocalDate.parse(date);
            LocalTime appointmentTime = LocalTime.parse(time);
            if (appointmentDate.isBefore(LocalDate.now())) {
                model.addAttribute("error", "You cannot book an appointment for a past date.");
                return "appointment";
            }
            if (appointmentDate.isEqual(LocalDate.now()) && appointmentTime.isBefore(LocalTime.now())) {
                model.addAttribute("error", "You cannot book an appointment for a past time.");
                return "appointment";
            }
            appointment.setAppointmentDate(appointmentDate);
            appointment.setAppointmentTime(appointmentTime);
            appointment.setService(service);
            appointment.setUser(user);

            appointmentService.saveAppointment(appointment);
            model.addAttribute("success", "Appointment booked successfully!");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to book appointment: " + e.getMessage());
        }
        return "appointment";
    }

    @GetMapping("/home")
    public String homePage() {
        return "home";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/oauth2/authorization/{provider}")
    public String deprecatedOAuthEntry(@PathVariable String provider) {
        return "redirect:/login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@RequestParam String name,
                         @RequestParam String email,
                         @RequestParam String password,
                         @RequestParam String confirmPassword,
                         @RequestParam(required = false) String referralCode,
                         Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match");
            model.addAttribute("submittedReferralCode", referralCode);
            return "signup";
        }
        try {
            User user = new User();
            user.setFullName(name);
            user.setEmail(email);
            user.setPassword(password);
            userService.registerUser(user, referralCode);
            return "redirect:/login?registered";
        } catch (Exception e) {
            model.addAttribute("error", "Registration failed: " + e.getMessage());
            model.addAttribute("submittedReferralCode", referralCode);
            return "signup";
        }
    }

    @GetMapping("/beautician-signup")
    public String beauticianSignup() {
        return "beautician-signup";
    }

    @PostMapping("/beautician-signup")
    public String beauticianSignup(@RequestParam String name,
                                   @RequestParam String email,
                                   @RequestParam String password,
                                   @RequestParam String confirmPassword,
                                   Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match");
            return "beautician-signup";
        }
        try {
            User user = new User();
            user.setFullName(name);
            user.setEmail(email);
            user.setPassword(password);
            userService.registerUserWithRole(user, "BEAUTICIAN");
            return "redirect:/login?beauticianRegistered";
        } catch (Exception e) {
            model.addAttribute("error", "Registration failed: " + e.getMessage());
            return "beautician-signup";
        }
    }

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        User refreshedUser = userService.getUserById(user.getId()).orElse(user);
        session.setAttribute("user", refreshedUser);
        Map<String, Object> profileSummary = userService.buildProfileSummary(refreshedUser);
        model.addAttribute("profileSummary", profileSummary);

        if ("BEAUTICIAN".equals(refreshedUser.getRole())) {
            beauticianService.getBeauticianByUserId(refreshedUser.getId())
                    .ifPresent(beautician -> model.addAttribute("beautician", beautician));
            return "beautician-profile";
        }
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String fullName,
                                @RequestParam String email,
                                @RequestParam(required = false) String password,
                                @RequestParam(required = false) String confirmPassword,
                                HttpSession session,
                                Model model) {

        User currentUser = (User) session.getAttribute("user");
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            currentUser.setFullName(fullName);
            currentUser.setEmail(email);

            String newPassword = null;
            if (password != null && !password.isEmpty()) {
                if (!password.equals(confirmPassword)) {
                    model.addAttribute("error", "New passwords do not match.");
                    attachProfileModel(currentUser, session, model);
                    return "BEAUTICIAN".equals(currentUser.getRole()) ? "beautician-profile" : "profile";
                }
                newPassword = password;
            }

            User updatedUser = userService.updateUser(currentUser, newPassword);
            session.setAttribute("user", updatedUser);
            model.addAttribute("success", "Profile updated successfully!");
            attachProfileModel(updatedUser, session, model);
        } catch (Exception e) {
            model.addAttribute("error", "Update failed: " + e.getMessage());
            attachProfileModel(currentUser, session, model);
        }

        return "BEAUTICIAN".equals(currentUser.getRole()) ? "beautician-profile" : "profile";
    }

    @PostMapping("/profile/delete")
    public String deleteProfile(@RequestParam String password, HttpSession session, Model model) {
        User currentUser = (User) session.getAttribute("user");
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            userService.deleteUser(currentUser, password);
            session.invalidate();
            return "redirect:/login?deleted";
        } catch (Exception e) {
            model.addAttribute("error", "Deletion failed: " + e.getMessage());
            attachProfileModel(currentUser, session, model);
            return "BEAUTICIAN".equals(currentUser.getRole()) ? "beautician-profile" : "profile";
        }
    }

    @GetMapping("/my-appointments")
    public String myAppointments(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        List<Appointment> appointments = appointmentService.getAppointmentsByUserId(user.getId());
        model.addAttribute("appointments", appointments);
        return "my-appointments";
    }

    @GetMapping("/notifications")
    public String notifications(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        List<com.saloonx.saloonx.model.Notification> notifications = notificationService.getNotificationsForUser(user);
        model.addAttribute("notifications", notifications);
        model.addAttribute("notificationCount", notifications.size());
        return "notifications";
    }

    @GetMapping("/beautician-bookings")
    public String beauticianBookings(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"BEAUTICIAN".equals(user.getRole())) {
            return "redirect:/login";
        }

        Optional<Beautician> beauticianOpt = beauticianService.getBeauticianByUserId(user.getId());
        if (beauticianOpt.isEmpty()) {
            return "redirect:/beautician/profile/create";
        }
        if (!beauticianService.isApproved(beauticianOpt.get())) {
            return "redirect:/beautician/profile?approvalPending";
        }

        List<Appointment> bookings = appointmentService.getAllAppointments();
        model.addAttribute("bookings", bookings != null ? bookings : Collections.emptyList());
        return "beautician-bookings";
    }

    @PostMapping("/my-appointments/delete/{id}")
    public String deleteMyAppointment(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        appointmentService.getAppointmentById(id).ifPresent(appt -> {
            if (appt.getUser() != null && appt.getUser().getId().equals(user.getId())) {
                appointmentService.deleteAppointment(id);
            }
        });
        return "redirect:/my-appointments";
    }

    @PostMapping("/my-appointments/update/{id}")
    public String updateMyAppointment(@PathVariable Long id,
                                      @RequestParam String date,
                                      @RequestParam String time,
                                      @RequestParam String service,
                                      HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }

        appointmentService.getAppointmentById(id).ifPresent(appt -> {
            if (appt.getUser() != null && appt.getUser().getId().equals(user.getId())) {
                LocalDate appointmentDate = LocalDate.parse(date);
                LocalTime appointmentTime = LocalTime.parse(time);
                boolean isPast = appointmentDate.isBefore(LocalDate.now()) ||
                        (appointmentDate.isEqual(LocalDate.now()) && appointmentTime.isBefore(LocalTime.now()));
                if (!isPast) {
                    appt.setAppointmentDate(appointmentDate);
                    appt.setAppointmentTime(appointmentTime);
                    appt.setService(service);
                    appointmentService.saveAppointment(appt);
                }
            }
        });
        return "redirect:/my-appointments";
    }

    @GetMapping("/api/booked-slots")
    @ResponseBody
    public List<String> getBookedSlots(@RequestParam String date) {
        LocalDate localDate = LocalDate.parse(date);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        return appointmentService.getBookedTimesByDate(localDate).stream()
                .map(time -> time.format(formatter))
                .toList();
    }

    private void attachProfileModel(User user, HttpSession session, Model model) {
        User refreshedUser = userService.getUserById(user.getId()).orElse(user);
        session.setAttribute("user", refreshedUser);
        model.addAttribute("profileSummary", userService.buildProfileSummary(refreshedUser));
        if ("BEAUTICIAN".equals(refreshedUser.getRole())) {
            beauticianService.getBeauticianByUserId(refreshedUser.getId())
                    .ifPresent(beautician -> model.addAttribute("beautician", beautician));
        }
    }
}
