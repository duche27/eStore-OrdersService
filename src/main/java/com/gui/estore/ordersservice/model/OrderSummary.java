package com.gui.estore.ordersservice.model;

import lombok.*;

import javax.persistence.*;

@Value
public class OrderSummary {

    String orderId;
    @Enumerated(EnumType.STRING)
    OrderStatus orderStatus;
    String reason;
}
