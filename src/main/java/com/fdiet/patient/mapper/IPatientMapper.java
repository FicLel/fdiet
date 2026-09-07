package com.fdiet.patient.mapper;

import com.fdiet.patient.dto.PatientDto;
import com.fdiet.patient.model.Patient;

/** Moves a patient between its stored shape and the shape that crosses a boundary. */
public interface IPatientMapper {

    PatientDto toDto(Patient patient);
}
