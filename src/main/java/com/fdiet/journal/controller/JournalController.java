package com.fdiet.journal.controller;

import com.fdiet.diet.dto.MealType;
import com.fdiet.journal.dto.DietJournalDto;
import com.fdiet.journal.dto.DishScoreDto;
import com.fdiet.journal.dto.ExtraFoodDto;
import com.fdiet.journal.dto.JournalCountsDto;
import com.fdiet.journal.dto.LogExtraFoodRequestDto;
import com.fdiet.journal.dto.ScoreDishRequestDto;
import com.fdiet.journal.service.IJournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

import java.time.DayOfWeek;

/**
 * Web layer. It forwards to the service and returns what it hands back.
 *
 * <p>A score's URL is the place in the week it belongs to, because that is what
 * a score is kept against — the dish rows are replaced wholesale on every
 * publish, and an id from before one addresses nothing.
 */
@RestController
@RequestMapping("/api/journal")
@Validated
@Tag(name = "Journal controller",
        description = "The patient's side of the plan: what was thought of a plate, "
                + "and what was eaten beside it")
public class JournalController {

    private final IJournalService journalService;

    public JournalController(IJournalService journalService) {
        this.journalService = journalService;
    }

    @GetMapping("/{dietId}")
    @Operation(summary = "A whole week's scores and off-plan entries. The average travels with "
            + "the number of plates it was worked out over, and is absent while none have "
            + "been scored")
    public DietJournalDto find(@PathVariable Long dietId) {
        return journalService.find(dietId);
    }

    @GetMapping("/{dietId}/counts")
    @Operation(summary = "How many plates the patient has scored and how many extras they "
            + "logged on this diet — what deleting the diet would take with it")
    public JournalCountsDto counts(@PathVariable Long dietId) {
        return journalService.counts(dietId);
    }

    @PutMapping("/{dietId}/scores/{day}/{mealType}/{dishIndex}")
    @Operation(summary = "Score one plate from 1 to 5, writing over any earlier opinion of the "
            + "same slot. 400 when the diet has no dish there")
    public DishScoreDto score(@PathVariable Long dietId,
                              @PathVariable DayOfWeek day,
                              @PathVariable MealType mealType,
                              @PathVariable @Min(0) int dishIndex,
                              @RequestBody @Valid ScoreDishRequestDto request) {
        return journalService.score(dietId, day, mealType, dishIndex, request);
    }

    @DeleteMapping("/{dietId}/scores/{day}/{mealType}/{dishIndex}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Take a score back. This is how a rating is removed — there is no score "
            + "of zero, since having no opinion must stay out of the average")
    public void clearScore(@PathVariable Long dietId,
                           @PathVariable DayOfWeek day,
                           @PathVariable MealType mealType,
                           @PathVariable @Min(0) int dishIndex) {
        journalService.clearScore(dietId, day, mealType, dishIndex);
    }

    @PostMapping("/{dietId}/extras")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record something eaten that the plan did not prescribe. Give "
            + "compositionFoodId (CIQUAL / BLS) or foodItemId to match it to a food, or "
            + "neither — an entry nothing matched is kept as written and simply counts towards "
            + "nothing. bedcaFoodId is retired: a value there is a 400")
    public ExtraFoodDto logExtra(@PathVariable Long dietId,
                                 @RequestBody @Valid LogExtraFoodRequestDto request) {
        return journalService.logExtra(dietId, request);
    }

    @DeleteMapping("/{dietId}/extras/{extraId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove one off-plan entry")
    public void removeExtra(@PathVariable Long dietId, @PathVariable Long extraId) {
        journalService.removeExtra(dietId, extraId);
    }
}
