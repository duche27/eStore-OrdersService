package com.gui.estore.ordersservice.commands;

import com.gui.estore.ordersservice.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Builder
public class CreateOrderCommand {

    @TargetAggregateIdentifier
    public final String orderId;
    private final String userId;
    private final String productId;
    private final int quantity;
    private final String addressId;
    @Enumerated(EnumType.STRING)
    private final OrderStatus orderStatus;
}
