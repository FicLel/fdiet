package com.fdiet.reference.model;

import com.fdiet.alternative.domain.FoodCategory;
import com.fdiet.reference.domain.FoodState;
import com.fdiet.reference.domain.HouseholdMeasure;
import com.fdiet.reference.domain.PortionSize;
import com.fdiet.reference.domain.WeightBasis;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * What a household measure of a food weighs, according to one source — or
 * according to the nutritionist, for one diet or for every diet.
 *
 * <p>A row is one of three kinds. A published row has a {@link #source} and a
 * code: it is what a document says. A diet's row has a {@link #dietId}: the
 * professional's criterion for that diet ("en esta dieta, 1 cucharadita de AOVE
 * son 5 ml"). A {@linkplain #globalCriterion global criterion} has neither: her
 * criterion for every diet ("1 huevo mediano son 58 g"). Both of hers are always
 * shown as such, never as a published figure, and the reference sync — which
 * addresses rows by code — never reaches them.
 *
 * <p>The published figure is kept as it was printed: "3 Uds. medianas, 180 g"
 * is a count of 3 and 180 g. One unit is divided out when it is read.
 */
@Entity
@Table(name = "ref_food_measures")
@Getter
@Setter
public class ReferenceFoodMeasure {

    private static final int SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The CSV key of a published row; null for a diet's own. */
    @Column(name = "code", length = 80)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private ReferenceSource source;

    /** The diet this row belongs to, when it is a nutritionist's own. A plain id: see the class note. */
    @Column(name = "diet_id")
    private Long dietId;

    /**
     * The nutritionist's criterion for every diet, belonging to no diet and no
     * source. The generated {@code criterion_key} column beside it, not mapped
     * here, keeps one per food, measure and size.
     */
    @Column(name = "global_criterion", nullable = false)
    private boolean globalCriterion;

    @Enumerated(EnumType.STRING)
    @Column(name = "measure", length = 32, nullable = false)
    private HouseholdMeasure measure;

    @Enumerated(EnumType.STRING)
    @Column(name = "size", length = 8)
    private PortionSize size;

    @Column(name = "measure_count", precision = 6, scale = 2, nullable = false)
    private BigDecimal count;

    /** The composition food (CIQUAL 2025 or BLS 4.0) the row names, a plain id; null for a family row. */
    @Column(name = "composition_food_id")
    private Long compositionFoodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "food_category", length = 24)
    private FoodCategory foodCategory;

    @Column(name = "keywords", length = 500)
    private String keywords;

    @Column(name = "food_label", length = 160, nullable = false)
    private String foodLabel;

    @Column(name = "grams_min", precision = 8, scale = 2)
    private BigDecimal gramsMin;

    @Column(name = "grams_max", precision = 8, scale = 2)
    private BigDecimal gramsMax;

    @Column(name = "ml_min", precision = 8, scale = 2)
    private BigDecimal mlMin;

    @Column(name = "ml_max", precision = 8, scale = 2)
    private BigDecimal mlMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16, nullable = false)
    private FoodState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "weight_basis", length = 16, nullable = false)
    private WeightBasis weightBasis;

    @Column(name = "gross_grams", precision = 8, scale = 2)
    private BigDecimal grossGrams;

    @Column(name = "household_text", length = 255)
    private String householdText;

    @Column(name = "page_ref", length = 160)
    private String pageRef;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** True for a nutritionist's own row, which belongs to one diet. */
    public boolean isDietOwn() {
        return dietId != null;
    }

    /**
     * What one measure weighs, in grams, or null when the source gave a range
     * ("1 huevo mediano, 53-63 g"): a range cannot weigh anything until somebody
     * picks a value in it, and the midpoint would be a figure nobody published.
     *
     * <p>Millilitres are read as grams — the one assumption
     * {@code PortionScaler} already makes for every liquid a diet writes, so a
     * spoonful of oil and "10 ml de aceite" are weighed the same way.
     */
    public BigDecimal gramsPerMeasure() {
        BigDecimal point = point(gramsMin, gramsMax);
        if (point == null) {
            point = point(mlMin, mlMax);
        }
        if (point == null || count == null || count.signum() <= 0) {
            return null;
        }
        return point.divide(count, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal point(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        if (min == null || max == null) {
            return min == null ? max : min;
        }
        return min.compareTo(max) == 0 ? min : null;
    }

    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
