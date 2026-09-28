package com.fdiet.patient.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Somebody a diet is written for.
 *
 * <p>It is <em>not</em> an account. There is no security layer anywhere in this
 * codebase, so this row authenticates nothing and hides nothing: it exists so
 * that a diet, a journal and a week of scores can say whose they are, and so
 * that the editor can hold more than one person at a time. When accounts come
 * back, this is the table they attach to.
 *
 * <p>The name is the whole identity for now, which is why the table holds it
 * under a unique index. Two patients called {@code Victor} would make the
 * selector name nobody.
 *
 * <p>There is deliberately no {@code diets} association here. A diet points at
 * its patient and not the other way round, so this context knows nothing about
 * {@code com.fdiet.diet} and the dependency runs one way only.
 */
@Entity
@Table(name = "patients")
@Getter
@Setter
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    /** The nutritionist's own note about the person. Free text, never parsed. */
    @Column(name = "notes", length = 1000)
    private String notes;

    /**
     * Optional. It is what lets the editor suggest the ration profile for the
     * patient's age; a patient without one is suggested the adult profile, and
     * the nutritionist can always choose another.
     */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** Optional, and never inferred. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sex", length = 8)
    private Sex sex;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Patient() {
    }

    public Patient(String name, String notes) {
        this.name = name;
        this.notes = notes;
    }

    /** Whole months of age on that day, or null without a birth date. */
    public Integer ageInMonths(LocalDate on) {
        if (birthDate == null || on.isBefore(birthDate)) {
            return null;
        }
        return (int) ChronoUnit.MONTHS.between(birthDate, on);
    }

    /** Stamps the creation time when the caller did not set one. */
    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
