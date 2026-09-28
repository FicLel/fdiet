package com.fdiet.diet.service;

import com.fdiet.diet.dto.DietImportSummaryDto;

import java.io.InputStream;
import java.time.LocalDate;

/** Turns one sheet of a diet workbook into a patient's stored diet in force. */
public interface IDietImportService {

    /**
     * @param patientId        whose diet this becomes; theirs in force is archived
     * @param sheet            the sheet to read, or null for the first one
     * @param name             the name to store the diet under, or null to use the
     *                         sheet name
     * @param startedOn        the day the diet starts, or null for today
     * @param referenceProfile the ration profile the week is written against, or
     *                         null for the one suggested for the patient's age
     * @param clinical         whether the diet is clinical; null for no
     */
    DietImportSummaryDto importWorkbook(InputStream workbook, Long patientId, String sheet,
                                        String name, LocalDate startedOn, String referenceProfile,
                                        Boolean clinical);
}
