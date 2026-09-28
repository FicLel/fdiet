package com.fdiet.reference.model;

import com.fdiet.reference.domain.ExchangeNutrient;
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

/**
 * The definition of an exchange unit — "1 ración de HC = 10 g" — and nothing
 * more. The count is worked out from the composition figures when it is read;
 * no published exchange list is copied.
 */
@Entity
@Table(name = "ref_exchange_systems")
@Getter
@Setter
public class ReferenceExchangeSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 80, nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private ReferenceSource source;

    @Column(name = "name", length = 160, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "nutrient", length = 16, nullable = false)
    private ExchangeNutrient nutrient;

    @Column(name = "grams_per_unit", precision = 6, scale = 2, nullable = false)
    private BigDecimal gramsPerUnit;

    @Column(name = "clinical", nullable = false)
    private boolean clinical;

    @Column(name = "note", length = 500)
    private String note;
}
