package com.fdiet.reference.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One population band of one source, labelled the way the source labelled it.
 * A selectable band is a <em>profile</em>: what a diet is written against.
 *
 * <p>The ages are filled only where the source states them. "Etapa juvenil" is
 * not turned into a number nobody published.
 */
@Entity
@Table(name = "ref_populations")
@Getter
@Setter
public class ReferencePopulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private ReferenceSource source;

    @Column(name = "label", length = 160, nullable = false)
    private String label;

    @Column(name = "age_min_months")
    private Integer ageMinMonths;

    @Column(name = "age_max_months")
    private Integer ageMaxMonths;

    @Column(name = "context", length = 255)
    private String context;

    /** The band whose meal energy shares this one borrows, when its own source has none. */
    @Column(name = "meal_shares_from", length = 64)
    private String mealSharesFrom;

    /** Why, in words — shown wherever the borrowed shares are drawn. */
    @Column(name = "meal_shares_note", length = 500)
    private String mealSharesNote;

    @Column(name = "selectable", nullable = false)
    private boolean selectable;
}
