package com.fdiet.reference.model;

import com.fdiet.reference.domain.RecommendationPeriod;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * How many rations of which groups, how often, for one population. Only a
 * maximum is a ceiling; only a minimum is a floor.
 */
@Entity
@Table(name = "ref_recommendations")
@Getter
@Setter
public class ReferenceRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 80, nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "population_id", nullable = false)
    private ReferencePopulation population;

    @Column(name = "label", length = 160, nullable = false)
    private String label;

    /** Semicolon-separated ration group codes counted together. */
    @Column(name = "group_codes", length = 160, nullable = false)
    private String groupCodes;

    @Column(name = "rations_min", precision = 6, scale = 2)
    private BigDecimal rationsMin;

    @Column(name = "rations_max", precision = 6, scale = 2)
    private BigDecimal rationsMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", length = 16, nullable = false)
    private RecommendationPeriod period;

    @Column(name = "page_ref", length = 160, nullable = false)
    private String pageRef;

    @Column(name = "note", length = 500)
    private String note;

    public List<String> groups() {
        return Arrays.stream(groupCodes.split(";")).map(String::trim).filter(g -> !g.isEmpty()).toList();
    }
}
