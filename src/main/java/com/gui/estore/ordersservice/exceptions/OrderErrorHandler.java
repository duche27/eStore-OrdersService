package com.gui.estore.ordersservice.exceptions;

import org.axonframework.commandhandling.CommandExecutionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class OrderErrorHandler {

    @ExceptionHandler(value = {OrderNotFoundException.class})
    public ResponseEntity<ErrorMessage> noDataErrorHandler(OrderNotFoundException e) {

        ErrorMessage errorMessage = new ErrorMessage(e.getMessage());

        return new ResponseEntity<>(errorMessage, HttpStatus.NO_CONTENT);
    }

    @ExceptionHandler(value = {RuntimeException.class})
    public ResponseEntity<ErrorMessage> handleRuntimeException(RuntimeException e) {

        ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
        return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // excepciones del AGGREGATE
    @ExceptionHandler(value = {CommandExecutionException.class})
    public ResponseEntity<ErrorMessage> handleCommandExecutionException(CommandExecutionException e, WebRequest request) {

        ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
        return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(value = {IllegalStateException.class})
    public ResponseEntity<ErrorMessage> handleIllegalStateException(IllegalStateException e) {

        ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
        return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(value = {Exception.class})
    public ResponseEntity<ErrorMessage> handleOtherException(Exception e, WebRequest request) {

        ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
        return new ResponseEntity<>(errorMessage, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
