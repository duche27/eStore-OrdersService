package com.gui.estore.ordersservice.commands.rest;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Data
public class OrderCreateRest {

    @NotBlank(message = "ProductID cannot be empty")
    private String productId;

    @Min(value = 1, message = "Quantity cannot be lower than 1")
    @Max(value = 5, message = "Quantity cannot be higher than 5")
    private Integer quantity;

    //    @NotBlank(message = "AddressID cannot be empty")
    private String addressId;
}
