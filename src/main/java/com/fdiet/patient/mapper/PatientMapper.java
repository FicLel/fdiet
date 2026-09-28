package com.fdiet.patient.mapper;

import com.fdiet.patient.dto.PatientDto;
import com.fdiet.patient.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class PatientMapper implements IPatientMapper {

    @Override
    public PatientDto toDto(Patient patient) {
        return new PatientDto(
                patient.getId(),
                patient.getName(),
                patient.getNotes(),
                patient.getBirthDate(),
                patient.getSex(),
                patient.getCreatedAt());
    }
}
