package com.gui.estore.ordersservice.exceptions;

import org.axonframework.commandhandling.CommandExecutionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class OrderErrorHandler {

    @ExceptionHandler(value = {OrderNotFoundException.class})
    public ResponseEntity<Error> noDataErrorHandler(OrderNotFoundException e) {

        Error error = new Error(e.getMessage());

        return new ResponseEntity<>(error, HttpStatus.NO_CONTENT);
    }

    // para manejar excepciones de validaciones de constraints de hibernate
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Error> dataIntegrityViolationHandler(DataIntegrityViolationException e) {

        Error error = new Error(e.getMostSpecificCause().getMessage());

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // para manejar excepciones de validaciones de hibernate con @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Error> fieldsValidationExceptions(MethodArgumentNotValidException e) {

        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(Error.builder().message(errors.toString()).build(), HttpStatus.BAD_REQUEST);
    }

    // excepciones del AGGREGATE
    @ExceptionHandler(value = {CommandExecutionException.class})
    public ResponseEntity<Error> handleCommandExecutionException(CommandExecutionException e, WebRequest request) {

        Error error = new Error(e.getMessage());
        return new ResponseEntity<>(error, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(value = {IllegalStateException.class})
    public ResponseEntity<Error> handleIllegalStateException(IllegalStateException e) {

        Error error = new Error(e.getMessage());
        return new ResponseEntity<>(error, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(value = {RuntimeException.class})
    public ResponseEntity<Error> handleRuntimeException(RuntimeException e) {

        Error error = new Error(e.getMessage());
        return new ResponseEntity<>(error, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(value = {Exception.class})
    public ResponseEntity<Error> handleOtherException(Exception e, WebRequest request) {

        Error error = new Error(e.getMessage());
        return new ResponseEntity<>(error, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
