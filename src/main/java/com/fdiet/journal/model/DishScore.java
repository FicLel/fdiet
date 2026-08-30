package com.fdiet.journal.model;

import com.fdiet.diet.dto.MealType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * What the patient thought of one plate, from 1 to 5.
 *
 * <p><b>It points at a slot, not at a dish row.</b> {@code PUT /api/diets/{id}}
 * replaces a diet's whole week, so every {@code diet_dishes} id is new after any
 * publish; a score holding one of those ids would be deleted by the cascade
 * each time the nutritionist edited a single cell. The place in the week —
 * diet, day, meal slot, position within the meal — is what survives, and it is
 * already how {@code uk_diet_meals_slot} identifies a place.
 *
 * <p>The diet is held as a plain id rather than an association. The week is
 * another context's aggregate, and the journal only ever needs to say which
 * week it is talking about.
 */
@Entity
@Table(name = "dish_scores")
@Getter
@Setter
public class DishScore {

    /** The range the design offers; the schema restates it as a check constraint. */
    public static final int MIN = 1;
    public static final int MAX = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "diet_id", nullable = false)
    private Long dietId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 16, nullable = false)
    private DayOfWeek dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", length = 24, nullable = false)
    private MealType mealType;

    /** The dish's place in its meal, from 0 — {@code diet_dishes.position}. */
    @Column(name = "dish_index", nullable = false)
    private int dishIndex;

    @Column(name = "score", nullable = false)
    private int score;

    /**
     * When it was scored. A republish can leave a score describing a dish that
     * has since been rewritten, and this is what says whether it predates the
     * change.
     */
    @Column(name = "scored_at", nullable = false)
    private LocalDateTime scoredAt;

    protected DishScore() {
    }

    public DishScore(Long dietId, DayOfWeek dayOfWeek, MealType mealType, int dishIndex, int score) {
        this.dietId = dietId;
        this.dayOfWeek = dayOfWeek;
        this.mealType = mealType;
        this.dishIndex = dishIndex;
        this.score = score;
        this.scoredAt = LocalDateTime.now();
    }

    /** Re-scoring writes over the opinion rather than adding a second one. */
    public void rescore(int value) {
        this.score = value;
        this.scoredAt = LocalDateTime.now();
    }
}
