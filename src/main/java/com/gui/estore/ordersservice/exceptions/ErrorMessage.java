package com.gui.estore.ordersservice.exceptions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorMessage {

    private String message;
    private ZonedDateTime timestamp;

    public ErrorMessage(String message) {
        this.message = message;
        this.timestamp = ZonedDateTime.now();
    }
}