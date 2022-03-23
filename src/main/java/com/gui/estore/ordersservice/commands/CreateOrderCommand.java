package com.gui.estore.ordersservice.commands;

import com.gui.estore.ordersservice.model.OrderStatus;
import lombok.Builder;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Builder
@Data
public class CreateOrderCommand {

    @TargetAggregateIdentifier
    public final String orderId;

    private final String userId;
    private final String productId;
    private final int quantity;
    private final String addressId;
    private final OrderStatus orderStatus;
}
