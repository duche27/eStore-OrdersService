package com.gui.estore.ordersservice.saga;

import com.gui.estore.core.commands.ProcessPaymentCommand;
import com.gui.estore.core.commands.ReserveProductCommand;
import com.gui.estore.core.events.PaymentProcessedEvent;
import com.gui.estore.core.events.ProductReservedEvent;
import com.gui.estore.core.model.User;
import com.gui.estore.core.queries.FetchUserPaymentDetailsQuery;
import com.gui.estore.ordersservice.commands.ApproveOrderCommand;
import com.gui.estore.ordersservice.core.events.OrderApprovedEvent;
import com.gui.estore.ordersservice.core.events.OrderCreatedEvent;
import com.gui.estore.ordersservice.exceptions.PaymentException;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.callbacks.LoggingCallback;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Saga
public class OrderSaga {

    // saga es serialized, transient es para que no se serialicen los datos
    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient QueryGateway queryGateway;

    // abrimos método HANDLE pro cada EVENT recibido
    // en cuanto un OrderCreatedEvent sea creado
    // associationProperty = "orderId" asocia los eventos a la instancia de SAGA
    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderCreatedEvent orderCreatedEvent) {

        ReserveProductCommand reserveProductCommand = ReserveProductCommand.builder()
                .orderId(orderCreatedEvent.getOrderId())
                .productId(orderCreatedEvent.getProductId())
                .quantity(orderCreatedEvent.getQuantity())
                .userId(orderCreatedEvent.getUserId())
                .build();

        log.info("OrderCreatedEvent handled in SAGA for orderId: " + reserveProductCommand.getOrderId() +
                " and productId: " + reserveProductCommand.getProductId());

        // CALLBACK nos informará cuando el COMMAND haya sido procesado
        // mandamos COMMAND al ProductAggregate
        commandGateway.send(reserveProductCommand, new CommandCallback<ReserveProductCommand, Object>() {

            @Override
            public void onResult(CommandMessage<? extends ReserveProductCommand> commandMessage,
                                 CommandResultMessage<? extends Object> commandResultMessage) {
                if (commandResultMessage.isExceptional()) {
                    // Start a compensating transaction si hay EXCEPTION
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

        FetchUserPaymentDetailsQuery fetchUserPaymentDetailsQuery =
                FetchUserPaymentDetailsQuery.builder()
                        .userId(productReservedEvent.getUserId())
                        .build();

        User userPaymentDetails = null;

        try {
            // QUERY a GATEWAY y llega a UserEventsHandler
            userPaymentDetails = queryGateway.query(fetchUserPaymentDetailsQuery, ResponseTypes.instanceOf(User.class)).join();
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        if (Objects.isNull(userPaymentDetails)) {
            throw new PaymentException("Ha habido un error al recuperar los métodos de pago del usuario" + userPaymentDetails.getFirstName());
        }

        // userPaymentDetails OK -> mandamos a PaymentService AGGREGATE
        log.info("Información del pago del usuario " + userPaymentDetails.getFirstName() + " recuperada OK");

        ProcessPaymentCommand processPaymentCommand = ProcessPaymentCommand.builder()
                .orderId(productReservedEvent.getOrderId())
                .paymentId(UUID.randomUUID().toString())
                .paymentDetails(userPaymentDetails.getPaymentDetails())
                .build();

        String result = null;

        try {
            // acepta time unit de espera de respuesta
            // bloquea ejecución ese tiempo, si no llega devuelve null
            result = commandGateway.sendAndWait(processPaymentCommand, 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        if (Objects.isNull(result)) {
            throw new PaymentException("Ha habido un error al ejecutar el pago del usuario" + userPaymentDetails.getFirstName());
        }
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(PaymentProcessedEvent paymentProcessedEvent) {

        // processed user payment
        log.info("PaymentProcessedEvent handled in SAGA! OrderId: " + paymentProcessedEvent.getOrderId());

        // creamos nuevo orderAcceptCommand
        ApproveOrderCommand approveOrderCommand = new ApproveOrderCommand(paymentProcessedEvent.getOrderId());

        commandGateway.send(approveOrderCommand);
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderApprovedEvent orderApprovedEvent) {

        log.info("OrderApprovedEvent completely handled in SAGA! OrderId: " + orderApprovedEvent.getOrderId());

//        SagaLifecycle.end();

    }
}
