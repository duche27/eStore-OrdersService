package com.gui.estore.ordersservice.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Data
@Builder
@AllArgsConstructor
public class ApproveOrderCommand {

    @TargetAggregateIdentifier
    private final String orderId;
}
