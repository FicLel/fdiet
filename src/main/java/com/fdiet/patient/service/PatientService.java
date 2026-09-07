package com.fdiet.patient.service;

import com.fdiet.common.helper.Texts;
import com.fdiet.patient.dto.PatientDto;
import com.fdiet.patient.dto.PatientRequestDto;
import com.fdiet.patient.exception.InvalidPatientException;
import com.fdiet.patient.exception.PatientNotFoundException;
import com.fdiet.patient.mapper.IPatientMapper;
import com.fdiet.patient.model.Patient;
import com.fdiet.patient.repository.PatientRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PatientService implements IPatientService {

    private static final int NAME_MAX = 255;
    private static final int NOTES_MAX = 1000;

    private final PatientRepository patientRepository;
    private final IPatientMapper patientMapper;

    public PatientService(PatientRepository patientRepository, IPatientMapper patientMapper) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientDto> findAll() {
        return patientRepository.findAllByOrderByNameAsc().stream()
                .map(patientMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PatientDto findById(Long id) {
        return patientMapper.toDto(entityById(id));
    }

    @Override
    @Transactional
    public PatientDto create(PatientRequestDto request) {
        String name = nameOf(request);
        requireNameFree(name, null);
        return patientMapper.toDto(
                patientRepository.save(new Patient(name, notesOf(request))));
    }

    @Override
    @Transactional
    public PatientDto update(Long id, PatientRequestDto request) {
        Patient patient = entityById(id);
        String name = nameOf(request);
        requireNameFree(name, id);
        patient.setName(name);
        patient.setNotes(notesOf(request));
        return patientMapper.toDto(patientRepository.save(patient));
    }

    /**
     * A patient still holding diets is refused rather than deleted.
     * {@code fk_diets_patient} does not cascade on purpose — the weeks written
     * for somebody are the record of them, and dropping a name should not drop
     * the record. The check is the foreign key itself, asked by flushing: the
     * diets are another context's table and this service does not read it.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        Patient patient = entityById(id);
        try {
            patientRepository.delete(patient);
            patientRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new InvalidPatientException("Patient " + id + " still has diets, and a "
                    + "patient's diets are the record of them. Delete the diets first, or keep "
                    + "the patient.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Patient entityById(Long id) {
        if (id == null) {
            throw PatientNotFoundException.patient(null);
        }
        return patientRepository.findById(id)
                .orElseThrow(() -> PatientNotFoundException.patient(id));
    }

    /**
     * The name is the whole identity while there are no accounts, so it is
     * checked here for a message a person can act on; {@code uk_patients_name}
     * is still there underneath for the write this check races with.
     */
    private void requireNameFree(String name, Long self) {
        Optional<Patient> holder = patientRepository.findByName(name);
        if (holder.isPresent() && !holder.get().getId().equals(self)) {
            throw new InvalidPatientException("There is already a patient called " + name);
        }
    }

    private static String nameOf(PatientRequestDto request) {
        return Texts.clean(request.name(), NAME_MAX);
    }

    private static String notesOf(PatientRequestDto request) {
        return Texts.clean(request.notes(), NOTES_MAX);
    }
}
