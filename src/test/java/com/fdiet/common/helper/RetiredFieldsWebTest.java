package com.fdiet.common.helper;

import com.fdiet.common.exception.GlobalExceptionHandler;
import com.fdiet.diet.controller.DietController;
import com.fdiet.diet.service.IDietComposeService;
import com.fdiet.diet.service.IDietImportService;
import com.fdiet.diet.service.IDietMeasureService;
import com.fdiet.diet.service.IDietService;
import com.fdiet.journal.controller.JournalController;
import com.fdiet.journal.service.IJournalService;
import com.fdiet.reference.controller.ReferenceController;
import com.fdiet.reference.service.IReferenceImportService;
import com.fdiet.reference.service.IReferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FD-033 phase D: a {@code bedcaFoodId} is a 400 wherever a caller used to send
 * one — as a query parameter of the reference lookups and as a field of the
 * request bodies — and never reaches a service.
 *
 * <p>The controllers are {@code @Validated}, so in the application a query
 * parameter is checked by the method-validation proxy, not by Spring MVC itself;
 * the proxy is built here the same way, or the {@code @Null} would go unchecked.
 */
class RetiredFieldsWebTest {

    private final IReferenceService referenceService = mock(IReferenceService.class);
    private final IDietService dietService = mock(IDietService.class);
    private final IDietComposeService composeService = mock(IDietComposeService.class);
    private final IJournalService journalService = mock(IJournalService.class);

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(
                        validated(new ReferenceController(referenceService,
                                mock(IReferenceImportService.class)), validator),
                        validated(new DietController(dietService, mock(IDietImportService.class),
                                composeService, mock(IDietMeasureService.class)), validator),
                        validated(new JournalController(journalService), validator))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        when(referenceService.rationsForFood(any(), any())).thenReturn(List.of());
        when(referenceService.measuresForFood(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(referenceService.yieldFactorsForFood(anyLong())).thenReturn(List.of());
    }

    private static Object validated(Object controller, LocalValidatorFactoryBean validator) {
        ProxyFactory proxy = new ProxyFactory(controller);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor((jakarta.validation.Validator) validator));
        return proxy.getProxy();
    }

    @Test
    void rationsRefuseABedcaFoodId() throws Exception {
        mvc.perform(get("/api/reference/rations").param("bedcaFoodId", "1065"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("compositionFoodId")));
        verify(referenceService, never()).rationsForFood(any(), any());
    }

    @Test
    void measuresRefuseABedcaFoodIdEvenBesideACompositionOne() throws Exception {
        mvc.perform(get("/api/reference/measures")
                        .param("compositionFoodId", "7").param("bedcaFoodId", "1065"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("retired")));
        verify(referenceService, never()).measuresForFood(anyLong(), any(), any(), any());
    }

    @Test
    void yieldsRefuseABedcaFoodIdEvenBesideACompositionOne() throws Exception {
        mvc.perform(get("/api/reference/yields")
                        .param("compositionFoodId", "7").param("bedcaFoodId", "1065"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("retired")));
        verify(referenceService, never()).yieldFactorsForFood(anyLong());
    }

    @Test
    void measuresAndYieldsWithoutAnyFoodAreA400() throws Exception {
        mvc.perform(get("/api/reference/measures").param("bedcaFoodId", "1065"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reference/yields").param("bedcaFoodId", "1065"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(referenceService);
    }

    @Test
    void aCompositionFoodIdAloneIsAnswered() throws Exception {
        mvc.perform(get("/api/reference/rations").param("compositionFoodId", "7"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reference/measures").param("compositionFoodId", "7"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reference/yields").param("compositionFoodId", "7"))
                .andExpect(status().isOk());
        verify(referenceService).rationsForFood(isNull(), any());
    }

    @Test
    void anIngredientPatchRefusesABedcaFoodId() throws Exception {
        mvc.perform(patch("/api/diets/1/ingredients/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bedcaFoodId\":1065}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("bedcaFoodId")));
        verifyNoInteractions(dietService);
    }

    @Test
    void composeRefusesABedcaFoodId() throws Exception {
        mvc.perform(post("/api/diets/compose").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compositionFoodId\":7,\"grams\":60,\"bedcaFoodId\":1065}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("bedcaFoodId")));
        verifyNoInteractions(composeService);
    }

    /** FD-048: a kept match of a re-parse is a request body like any other. */
    @Test
    void aParseRefusesABedcaFoodIdOnAKeptMatch() throws Exception {
        mvc.perform(post("/api/diets/parse").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"huevo (60 g)\",\"keep\":[{\"name\":\"huevo\","
                                + "\"compositionFoodId\":7,\"bedcaFoodId\":1065}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("bedcaFoodId")));
        verifyNoInteractions(dietService);
    }

    @Test
    void anExtraRefusesABedcaFoodId() throws Exception {
        mvc.perform(post("/api/journal/1/extras").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"day\":\"MONDAY\",\"name\":\"lechuga\",\"quantity\":80,"
                                + "\"unit\":\"g\",\"bedcaFoodId\":1065}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("bedcaFoodId")));
        verifyNoInteractions(journalService);
    }

    @Test
    void aPublishedWeekRefusesABedcaFoodIdOnAnyIngredient() throws Exception {
        mvc.perform(post("/api/diets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":1,\"name\":\"Semana\",\"startedOn\":\"2026-10-05\","
                                + "\"days\":[{\"day\":\"MONDAY\",\"meals\":[{\"type\":\"BREAKFAST\","
                                + "\"name\":\"Desayuno\",\"dishes\":[{\"name\":\"Tostada\","
                                + "\"recipe\":{\"rawText\":\"pan (60 g)\",\"ingredients\":[{\"name\":\"pan\","
                                + "\"quantity\":60,\"unit\":\"g\",\"bedcaFoodId\":1065}]}}]}]}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("bedcaFoodId")));
        verifyNoInteractions(dietService);
    }
}
