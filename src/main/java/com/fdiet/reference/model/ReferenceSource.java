package com.fdiet.reference.model;

import com.fdiet.reference.domain.LicenceClass;
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

import java.time.LocalDate;

/**
 * A document reference figures were read from, with the terms they may be used
 * under and the line that has to be shown beside them.
 */
@Entity
@Table(name = "ref_sources")
@Getter
@Setter
public class ReferenceSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "short_name", length = 80, nullable = false)
    private String shortName;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "institution", length = 255, nullable = false)
    private String institution;

    @Column(name = "country", length = 8, nullable = false)
    private String country;

    @Column(name = "tier", nullable = false)
    private int tier;

    @Column(name = "year")
    private Integer year;

    @Column(name = "url", length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "licence_class", length = 1, nullable = false)
    private LicenceClass licenceClass;

    @Column(name = "licence", length = 1000, nullable = false)
    private String licence;

    @Column(name = "attribution", length = 1000, nullable = false)
    private String attribution;

    @Column(name = "clinical", nullable = false)
    private boolean clinical;

    @Column(name = "retrieved_on", nullable = false)
    private LocalDate retrievedOn;

    @Column(name = "notes", length = 1000)
    private String notes;
}
