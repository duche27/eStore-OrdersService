package com.gui.estore.ordersservice.core.events;

import com.gui.estore.ordersservice.model.OrderStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Data
@NoArgsConstructor
public class OrderCreatedEvent {

    public String orderId;
    private String userId;
    private String productId;
    private int quantity;
    private String addressId;
    private OrderStatus orderStatus;

}
