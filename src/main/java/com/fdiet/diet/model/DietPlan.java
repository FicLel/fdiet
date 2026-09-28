package com.fdiet.diet.model;

import com.fdiet.patient.model.Patient;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A stored diet. Every diet is written for a {@link Patient}, and each patient
 * has one {@link DietStatus#ACTIVE} diet at a time with a record of the
 * archived ones behind it — so this is what a lookup asks for first: the diet
 * in force today, for one person.
 *
 * <p>The table also carries an {@code active_flag} generated column — 1 while
 * the row is active, NULL once it is archived — and {@code uk_diets_active}
 * spans {@code (patient_id, active_flag)}. MySQL allows a unique index to hold
 * many rows with a NULL in them, so every patient keeps all of their archived
 * diets and no patient can ever hold a second active one. The generated column
 * is deliberately not mapped here: Hibernate validates only the columns it
 * knows about, so leaving it out means no insert can ever try to write it.
 *
 * <p>Day and meal-slot ordering is not a concern of this class. The meals come
 * back in whatever order the database hands them over and are ordered by
 * feeding them through {@link com.fdiet.diet.domain.Diet}, whose {@code
 * EnumMap}s exist for exactly that.
 */
@Entity
@Table(name = "diets")
@Getter
@Setter
public class DietPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Who the week is written for.
     *
     * <p>The association is a mapping and not a layer crossing, the same way an
     * ingredient maps the food it was matched to. It is what lets a diet be read
     * back saying whose it is without a second lookup for the name — and it is
     * the only direction the two contexts know each other in: a patient row
     * holds no diets.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    /**
     * The reference profile the week is written against — a source's population
     * band, {@code AESAN-2022:ADULTOS}. A choice the nutritionist makes, stored
     * like a food match and never changed on its own. Null is a week read against
     * no profile: no ration counts, and household measures only attach when the
     * sources agree.
     */
    @Column(name = "reference_profile_code", length = 64)
    private String referenceProfileCode;

    /** A diet written for a clinical situation; clinical exchange systems are offered only here. */
    @Column(name = "clinical", nullable = false)
    private boolean clinical;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private DietStatus status;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    /** Null while the diet is active; the day it was archived once it is not. */
    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "diet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlannedMeal> meals = new ArrayList<>();

    public void addMeal(PlannedMeal meal) {
        meals.add(meal);
        meal.setDiet(this);
    }

    public void removeMeal(PlannedMeal meal) {
        meals.remove(meal);
        meal.setDiet(null);
    }

    /** Stamps the creation time when the caller did not set one. */
    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
