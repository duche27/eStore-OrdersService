package com.gui.estore.ordersservice.commands;

import com.gui.estore.ordersservice.core.events.OrderApprovedEvent;
import com.gui.estore.ordersservice.core.events.OrderCreatedEvent;
import com.gui.estore.ordersservice.core.events.OrderRejectedEvent;
import com.gui.estore.ordersservice.model.OrderStatus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.springframework.beans.BeanUtils;

@Aggregate
public class OrderAggregate {

    @AggregateIdentifier
    public String orderId;
    private String userId;
    private String productId;
    private int quantity;
    private String addressId;
    private OrderStatus orderStatus;

    public OrderAggregate() {
    }

    @CommandHandler
    public OrderAggregate(CreateOrderCommand createOrderCommand) {

        // validaciones


        // creamos evento si pasan validaciones
        OrderCreatedEvent orderCreatedEvent = new OrderCreatedEvent();

        BeanUtils.copyProperties(createOrderCommand, orderCreatedEvent);

        // publicamos evento y mandamos al eventHandler
        AggregateLifecycle.apply(orderCreatedEvent);
    }

    @EventSourcingHandler
    public void on(OrderCreatedEvent orderCreatedEvent) throws Exception {
        this.orderId = orderCreatedEvent.getOrderId();
        this.userId = orderCreatedEvent.getUserId();
        this.productId = orderCreatedEvent.getProductId();
        this.quantity = orderCreatedEvent.getQuantity();
        this.addressId = orderCreatedEvent.getAddressId();
        this.orderStatus = OrderStatus.ON_VALIDATION;
    }

    @CommandHandler
    public void handle(ApproveOrderCommand approveOrderCommand) {

        // creamos evento
        OrderApprovedEvent orderApprovedEvent =  OrderApprovedEvent.builder()
                .orderId(approveOrderCommand.getOrderId())
                .orderStatus(OrderStatus.APPROVED)
                .build();

        // publicamos evento
        AggregateLifecycle.apply(orderApprovedEvent);
    }

    @EventSourcingHandler
    public void on(OrderApprovedEvent orderApprovedEvent) throws Exception {
        this.orderStatus = orderApprovedEvent.getOrderStatus();
    }

    @CommandHandler
    public void handle(RejectOrderCommand rejectOrderCommand) {

        OrderRejectedEvent orderRejectedEvent = OrderRejectedEvent.builder()
                .orderId(rejectOrderCommand.getOrderId())
                .orderStatus(OrderStatus.REJECTED)
                .reason(rejectOrderCommand.getReason())
                .build();

        AggregateLifecycle.apply(orderRejectedEvent);
    }

    @EventSourcingHandler
    public void on(OrderRejectedEvent orderRejectedEvent) {
        this.orderStatus = orderRejectedEvent.getOrderStatus();
    }
}
