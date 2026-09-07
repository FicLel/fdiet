package com.fdiet.patient.controller;

import com.fdiet.patient.dto.PatientDto;
import com.fdiet.patient.dto.PatientRequestDto;
import com.fdiet.patient.service.IPatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Web layer. It forwards to the service and returns what it hands back.
 *
 * <p>Every route here is open, as every route in this codebase is. A patient is
 * a name a diet is written for and not an account, so one patient's week being
 * readable from another's screen is the design and not an oversight.
 */
@RestController
@RequestMapping("/api/patients")
@Validated
@Tag(name = "Patient controller", description = "The people diets are written for")
public class PatientController {

    private final IPatientService patientService;

    public PatientController(IPatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    @Operation(summary = "Everybody, by name. Ask GET /api/diets/current for the diet each of "
            + "them is on")
    public List<PatientDto> findAll() {
        return patientService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One patient")
    public PatientDto byId(@PathVariable Long id) {
        return patientService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a patient. The name is their whole identity while there are no "
            + "accounts, so no two may share one")
    public PatientDto create(@RequestBody @Valid PatientRequestDto request) {
        return patientService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a patient, or rewrite the note kept about them")
    public PatientDto update(@PathVariable Long id,
                             @RequestBody @Valid PatientRequestDto request) {
        return patientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a patient who has no diets. One who still has diets is refused: "
            + "their weeks are the record of them and are not deleted as a side effect")
    public void delete(@PathVariable Long id) {
        patientService.delete(id);
    }
}
