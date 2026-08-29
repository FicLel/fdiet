package com.fdiet.common.exception;

import com.fdiet.common.dto.ApiErrorDto;
import com.fdiet.diet.exception.DietNotFoundException;
import com.fdiet.diet.exception.InvalidDietException;
import com.fdiet.food.exception.BedcaFoodNotFoundException;
import com.fdiet.food.exception.FoodItemNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.io.UncheckedIOException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({FoodItemNotFoundException.class, BedcaFoodNotFoundException.class,
            DietNotFoundException.class})
    public ResponseEntity<ApiErrorDto> handleNotFound(RuntimeException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorDto> handleInvalidParameters(ConstraintViolationException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorDto> handleInvalidBody(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message);
    }

    /** A request body whose fields broke their constraints. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleInvalidFields(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(InvalidDietException.class)
    public ResponseEntity<ApiErrorDto> handleInvalidDiet(InvalidDietException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ApiErrorDto> handleUnreadableSource(UncheckedIOException e) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    private ResponseEntity<ApiErrorDto> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiErrorDto.of(status.value(), status.getReasonPhrase(), message));
    }
}
