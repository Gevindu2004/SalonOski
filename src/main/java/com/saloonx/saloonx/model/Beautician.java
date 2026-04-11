package com.saloonx.saloonx.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "beauticians")
public class Beautician {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    private String specialization;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private String profileImage;

    private Integer experience;

    @Column(nullable = false)
    private Boolean isAvailable = true;

    @OneToMany(mappedBy = "beautician", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Appointment> appointments;

    @Column(nullable = false)
    private Double rating = 0.0;

    private Integer totalReviews = 0;

    @Column(nullable = false)
    private String approvalStatus = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String approvalNotes;

    private String reviewedByAdminEmail;

    private LocalDateTime approvalRequestedAt;

    private LocalDateTime reviewedAt;
}
