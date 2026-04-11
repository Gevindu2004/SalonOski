package com.saloonx.saloonx.service;

import com.saloonx.saloonx.model.Appointment;
import com.saloonx.saloonx.model.Beautician;
import com.saloonx.saloonx.model.User;
import com.saloonx.saloonx.repository.AppointmentRepository;
import com.saloonx.saloonx.repository.BeauticianRepository;
import com.saloonx.saloonx.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BeauticianService {

    @Autowired
    private BeauticianRepository beauticianRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserService userService;

    @Transactional
    public Beautician createBeauticianProfile(User user, String phone, String specialization,
                                              String bio, Integer experience) {
        if (beauticianRepository.findByUser(user).isPresent()) {
            throw new RuntimeException("Beautician profile already exists for this user");
        }

        Beautician beautician = new Beautician();
        beautician.setUser(user);
        beautician.setFullName(user.getFullName());
        beautician.setEmail(user.getEmail());
        beautician.setPhone(phone);
        beautician.setSpecialization(specialization);
        beautician.setBio(bio);
        beautician.setExperience(experience);
        beautician.setIsAvailable(false);
        beautician.setApprovalStatus("PENDING");
        beautician.setApprovalNotes("Awaiting admin review.");
        beautician.setApprovalRequestedAt(LocalDateTime.now());

        if (!"BEAUTICIAN".equals(user.getRole())) {
            user.setRole("BEAUTICIAN");
        }
        user.setAccountStatus("PENDING_APPROVAL");
        userRepository.save(user);

        Beautician saved = beauticianRepository.save(beautician);
        submitApprovalRequest(saved, "New beautician onboarding request");
        return saved;
    }

    @Transactional
    public Beautician updateBeauticianProfile(Long beauticianId, String phone, String specialization,
                                              String bio, Integer experience, Boolean isAvailable) {
        Beautician beautician = beauticianRepository.findById(beauticianId)
                .orElseThrow(() -> new RuntimeException("Beautician not found"));

        StringBuilder changes = new StringBuilder();

        if (phone != null && !phone.equals(beautician.getPhone())) {
            beautician.setPhone(phone);
            changes.append("Phone updated, ");
        }
        if (specialization != null && !specialization.equals(beautician.getSpecialization())) {
            beautician.setSpecialization(specialization);
            changes.append("Specialization updated, ");
        }
        if (bio != null && !bio.equals(beautician.getBio())) {
            beautician.setBio(bio);
            changes.append("Bio updated, ");
        }
        if (experience != null && !experience.equals(beautician.getExperience())) {
            beautician.setExperience(experience);
            changes.append("Experience updated, ");
        }
        if (isAvailable != null && isApproved(beautician) && !isAvailable.equals(beautician.getIsAvailable())) {
            beautician.setIsAvailable(isAvailable);
            changes.append("Availability changed, ");
        }

        if ("REJECTED".equals(beautician.getApprovalStatus())) {
            beautician.setApprovalStatus("PENDING");
            beautician.setApprovalNotes("Profile updated and re-submitted for review.");
            beautician.setApprovalRequestedAt(LocalDateTime.now());
            beautician.setReviewedAt(null);
            beautician.setReviewedByAdminEmail(null);
            if (beautician.getUser() != null) {
                beautician.getUser().setAccountStatus("PENDING_APPROVAL");
                userRepository.save(beautician.getUser());
            }
            submitApprovalRequest(beautician, "Beautician re-submitted profile for approval");
        }

        Beautician updated = beauticianRepository.save(beautician);


        return updated;
    }

    public Optional<Beautician> getBeauticianByUserId(Long userId) {
        return beauticianRepository.findByUserId(userId);
    }

    public Optional<Beautician> getBeauticianById(Long id) {
        return beauticianRepository.findById(id);
    }

    public List<Beautician> getAllBeauticians() {
        return beauticianRepository.findAll();
    }

    public List<Beautician> getPendingApprovalBeauticians() {
        return beauticianRepository.findByApprovalStatusOrderByApprovalRequestedAtAsc("PENDING");
    }

    public List<Beautician> getAvailableBeauticians() {
        return beauticianRepository.findByIsAvailableTrue();
    }

    public boolean isApproved(Beautician beautician) {
        return beautician != null && "APPROVED".equals(beautician.getApprovalStatus());
    }

    @Transactional
    public Beautician approveBeautician(Long beauticianId, User adminUser, String notes) {
        Beautician beautician = beauticianRepository.findById(beauticianId)
                .orElseThrow(() -> new RuntimeException("Beautician not found"));

        beautician.setApprovalStatus("APPROVED");
        beautician.setApprovalNotes(notes == null || notes.isBlank() ? "Approved for platform access." : notes.trim());
        beautician.setReviewedByAdminEmail(adminUser.getEmail());
        beautician.setReviewedAt(LocalDateTime.now());
        beautician.setIsAvailable(true);

        User user = beautician.getUser();
        if (user != null) {
            user.setAccountStatus("ACTIVE");
            userRepository.save(user);
            notificationService.sendUserNotification(
                    "Your beautician account has been approved. You can now access the dashboard and manage bookings.",
                    user);
        }

        return beauticianRepository.save(beautician);
    }

    @Transactional
    public Beautician rejectBeautician(Long beauticianId, User adminUser, String notes) {
        Beautician beautician = beauticianRepository.findById(beauticianId)
                .orElseThrow(() -> new RuntimeException("Beautician not found"));

        beautician.setApprovalStatus("REJECTED");
        beautician.setApprovalNotes((notes == null || notes.isBlank())
                ? "Application needs updates before approval."
                : notes.trim());
        beautician.setReviewedByAdminEmail(adminUser.getEmail());
        beautician.setReviewedAt(LocalDateTime.now());
        beautician.setIsAvailable(false);

        User user = beautician.getUser();
        if (user != null) {
            user.setAccountStatus("REJECTED");
            userRepository.save(user);
            notificationService.sendUserNotification(
                    "Your beautician account needs updates before approval. Review the notes in your profile and resubmit.",
                    user);
        }

        return beauticianRepository.save(beautician);
    }

    public List<Appointment> getBeauticianAppointments(Long beauticianId) {
        return appointmentRepository.findByBeauticianId(beauticianId);
    }

    public List<Appointment> getBeauticianAppointmentsByStatus(Long beauticianId, String status) {
        return appointmentRepository.findByBeauticianIdAndStatus(beauticianId, status);
    }

    @Transactional
    public Appointment updateAppointmentStatus(Long appointmentId, Long beauticianId, String status) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        Beautician beautician = beauticianRepository.findById(beauticianId)
                .orElseThrow(() -> new RuntimeException("Beautician not found"));

        if (!isApproved(beautician)) {
            throw new RuntimeException("Your account must be approved before handling appointments.");
        }

        if (appointment.getBeautician() == null) {
            appointment.setBeautician(beautician);
        } else if (!appointment.getBeautician().getId().equals(beauticianId)) {
            throw new RuntimeException("You are not authorized to update this appointment");
        }

        if ("PENDING".equals(appointment.getStatus())) {
            if (!"CONFIRMED".equals(status) && !"CANCELLED".equals(status)) {
                throw new RuntimeException("Pending appointments can only be confirmed or cancelled");
            }
        } else if ("CONFIRMED".equals(appointment.getStatus())) {
            if (!"COMPLETED".equals(status)) {
                throw new RuntimeException("Confirmed appointments can only be marked as completed");
            }
        } else {
            throw new RuntimeException("Can only update pending or confirmed appointments");
        }

        String oldStatus = appointment.getStatus();
        appointment.setStatus(status);
        Appointment updated = appointmentRepository.save(appointment);

        if (appointment.getUser() != null) {
            String message = String.format("Your appointment on %s at %s has been %s by beautician %s",
                    appointment.getAppointmentDate().format(DateTimeFormatter.ISO_DATE),
                    appointment.getAppointmentTime(),
                    status.toLowerCase(),
                    beautician.getFullName());
            notificationService.sendUserNotification(message, appointment.getUser());

            if ("COMPLETED".equals(status) && !Boolean.TRUE.equals(appointment.getRewardProcessed())) {
                userService.awardAppointmentCompletion(appointment.getUser());
                appointment.setRewardProcessed(true);
                updated = appointmentRepository.save(appointment);
            }
        }

        return updated;
    }

    @Transactional
    public Appointment assignAppointmentToBeautician(Long appointmentId, Long beauticianId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        Beautician beautician = beauticianRepository.findById(beauticianId)
                .orElseThrow(() -> new RuntimeException("Beautician not found"));

        if (!isApproved(beautician)) {
            throw new RuntimeException("Beautician is not approved for bookings yet");
        }

        Long appointmentCount = appointmentRepository.countAppointmentsByBeauticianAndDate(
                beauticianId, appointment.getAppointmentDate());

        if (appointmentCount >= 8) {
            throw new RuntimeException("Beautician is fully booked on this date");
        }

        appointment.setBeautician(beautician);
        Appointment updated = appointmentRepository.save(appointment);

        return updated;
    }

    public Long getBeauticianAppointmentCount(Long beauticianId, LocalDate date) {
        return appointmentRepository.countAppointmentsByBeauticianAndDate(beauticianId, date);
    }

    public List<Appointment> getPendingAppointmentsForBeautician(Long beauticianId) {
        List<Appointment> assigned = appointmentRepository.findByBeauticianIdAndStatusOrderByDate(beauticianId, "PENDING");
        List<Appointment> unassigned = appointmentRepository.findByBeauticianIsNullAndStatusOrderByAppointmentDateAscAppointmentTimeAsc("PENDING");
        List<Appointment> combined = new ArrayList<>(assigned);
        combined.addAll(unassigned);
        combined.sort((a1, a2) -> {
            int dc = a1.getAppointmentDate().compareTo(a2.getAppointmentDate());
            return dc != 0 ? dc : a1.getAppointmentTime().compareTo(a2.getAppointmentTime());
        });
        return combined;
    }

    public List<Appointment> getTodaysAppointments(Long beauticianId) {
        return appointmentRepository.findByBeauticianIdAndAppointmentDate(beauticianId, LocalDate.now());
    }

    @Transactional
    public void updateBeauticianRating(Long beauticianId) {
        Beautician beautician = beauticianRepository.findById(beauticianId)
                .orElseThrow(() -> new RuntimeException("Beautician not found"));
        beauticianRepository.save(beautician);
    }

    private void submitApprovalRequest(Beautician beautician, String title) {
        if (beautician.getUser() != null) {
            notificationService.sendUserNotification(
                    "Your beautician profile is waiting for admin approval. You can edit your profile while it is under review.",
                    beautician.getUser());
        }
        notificationService.sendBroadcastNotification(
                "Admin action required: beautician approval request from " + beautician.getFullName() + " (" + beautician.getEmail() + ")");
    }
}
