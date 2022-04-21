package com.gui.estore.ordersservice.core.events;

import com.gui.estore.ordersservice.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value    // inmutable version of @Data - class and field private final
@AllArgsConstructor
@Builder
public class OrderApprovedEvent {

    String orderId;
    OrderStatus orderStatus;
}
