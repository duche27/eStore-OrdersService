package com.gui.estore.ordersservice.saga;

import com.gui.estore.core.commands.ReserveProductCommand;
import com.gui.estore.core.events.ProductReservedEvent;
import com.gui.estore.ordersservice.core.events.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.callbacks.LoggingCallback;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Saga
public class OrderSaga {

    // saga es serialized, transient es para que no se serialicen los datos
    @Autowired
    private transient CommandGateway commandGateway;

    // en cuanto un OrderCreatedEvent sea creado
    // associationProperty = "orderId" asocia los eventos a la instancia de SAGA
    @StartSaga
    @SagaEventHandler(associationProperty="orderId")
    public void handle(OrderCreatedEvent orderCreatedEvent) {

        ReserveProductCommand reserveProductCommand = ReserveProductCommand.builder()
                .orderId(orderCreatedEvent.getOrderId())
                .productId(orderCreatedEvent.getProductId())
                .quantity(orderCreatedEvent.getQuantity())
                .userId(orderCreatedEvent.getUserId())
                .build();

        log.info("OrderCreatedEvent handled in SAGA for orderId: " + reserveProductCommand.getOrderId() +
                " and productId: " + reserveProductCommand.getProductId() );

        // CALLBACK nos informará cuando el COMMAND haya sido procesado
        // mandamos COMMAND al ProductAggregate
//        commandGateway.send(reserveProductCommand, LoggingCallback.INSTANCE);
        commandGateway.send(reserveProductCommand, new CommandCallback<ReserveProductCommand, Object>() {

            @Override
            public void onResult(CommandMessage<? extends ReserveProductCommand> commandMessage,
                                 CommandResultMessage<? extends Object> commandResultMessage) {
                if(commandResultMessage.isExceptional()) {
                    // Start a compensating transaction  si hay EXCEPTION
//                    RejectOrderCommand rejectOrderCommand = new RejectOrderCommand(orderCreatedEvent.getOrderId(),
//                            commandResultMessage.exceptionResult().getMessage());
//
//                    commandGateway.send(rejectOrderCommand);
                }
            }
        });
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(ProductReservedEvent productReservedEvent) {

        // process user payment
        log.info("ProductReservedEvent handled in SAGA! OrderId: " + productReservedEvent.getOrderId() + " - productId: "
                + productReservedEvent.getProductId());
    }
}
