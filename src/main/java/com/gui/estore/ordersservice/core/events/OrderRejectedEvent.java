package com.gui.estore.ordersservice.core.events;

import com.gui.estore.ordersservice.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value    // inmutable version of @Data - class and field private final
@AllArgsConstructor
public class OrderRejectedEvent {

    String orderId;
    String reason;
    OrderStatus orderStatus = OrderStatus.REJECTED;
}
