package com.gui.estore.ordersservice.exceptions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Error {

    private String message;
    private ZonedDateTime timestamp;

    public Error(String message) {
        this.message = message;
        this.timestamp = ZonedDateTime.now();
    }
}