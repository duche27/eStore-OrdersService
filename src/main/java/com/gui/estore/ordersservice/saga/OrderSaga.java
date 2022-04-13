package com.gui.estore.ordersservice.saga;

import com.gui.estore.core.commands.CancelProductReservationCommand;
import com.gui.estore.core.commands.ProcessPaymentCommand;
import com.gui.estore.core.commands.ReserveProductCommand;
import com.gui.estore.core.events.PaymentProcessedEvent;
import com.gui.estore.core.events.ProductReservationCancelledEvent;
import com.gui.estore.core.events.ProductReservedEvent;
import com.gui.estore.core.model.User;
import com.gui.estore.core.queries.FetchUserPaymentDetailsQuery;
import com.gui.estore.ordersservice.commands.ApproveOrderCommand;
import com.gui.estore.ordersservice.commands.RejectOrderCommand;
import com.gui.estore.ordersservice.core.events.OrderApprovedEvent;
import com.gui.estore.ordersservice.core.events.OrderCreatedEvent;
import com.gui.estore.ordersservice.core.events.OrderRejectedEvent;
import com.gui.estore.ordersservice.model.OrderSummary;
import com.gui.estore.ordersservice.queries.FindOrderQuery;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
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

    @Autowired
    private transient DeadlineManager deadlineManager;

    @Autowired
    private transient QueryUpdateEmitter queryUpdateEmitter;

    private final String PAYMENT_PROCESSING_TIMEOUT_DEADLINE = "payment-processing-deadline";

    private String scheduleId;

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
                    RejectOrderCommand rejectOrderCommand = RejectOrderCommand.builder()
                            .orderId(orderCreatedEvent.getOrderId())
                            .reason(commandResultMessage.exceptionResult().getMessage()).build();

                    commandGateway.send(rejectOrderCommand);
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
            // compensation
            cancelProductReservation(productReservedEvent,e.getMessage());
            return;
        }

        if (Objects.isNull(userPaymentDetails)) {
            cancelProductReservation(productReservedEvent,"Ha habido un error al recuperar los métodos de pago del usuario" + userPaymentDetails.getFirstName());
            log.error("Ha habido un error al recuperar los métodos de pago del usuario" + userPaymentDetails.getFirstName());
            return;
        }

        // userPaymentDetails OK -> mandamos a PaymentService AGGREGATE
        log.info("Información del pago del usuario " + userPaymentDetails.getFirstName() + " recuperada OK");

        // 2 mins, pero normalmente algo como una confirmación de usuario pueden ser varios días
        // productReservedEvent payload opcional
        scheduleId = deadlineManager.schedule(Duration.of(2, ChronoUnit.MINUTES),
                PAYMENT_PROCESSING_TIMEOUT_DEADLINE, productReservedEvent);

        // para pruebas: ejecutaba siempre deadlineManager
//        if (true) return;

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
            cancelProductReservation(productReservedEvent,e.getMessage());
            log.error(e.getMessage() + "Ha habido un error al ejecutar el pago del usuario" + userPaymentDetails.getFirstName());
            return;
        }

        if (Objects.isNull(result)) {
            cancelProductReservation(productReservedEvent,"Ha habido un error al ejecutar el pago del usuario" + userPaymentDetails.getFirstName());
            log.error("Ha habido un error al ejecutar el pago del usuario" + userPaymentDetails.getFirstName());
            return;
        }
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(PaymentProcessedEvent paymentProcessedEvent) {

        // si se ha procesado el pago cancelamos el Deadline
        cancelDeadline();

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

        // happy path cuando tenemos OrderApprovedEvent
        // comunica con SUBSCRIPTION QUERY posibles cambios, errores o ya no hay updates
        // parámetros: query class, predicado sin filtrar, updated object (OrderSummary que devuelve OrdersCommandController)
        queryUpdateEmitter.emit(
                FindOrderQuery.class,
                query -> true,
                new OrderSummary(orderApprovedEvent.getOrderId(), orderApprovedEvent.getOrderStatus(), ""));

        // método alternativo para indicar el fin de saga aparte de la anotación @EndSaga
//        SagaLifecycle.end();

    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(ProductReservationCancelledEvent productReservationCancelledEvent) {

        log.info("PRODUCT RESERVATION EVENT handled in SAGA: OrderId " + productReservationCancelledEvent.getOrderId()
        + " - productId " + productReservationCancelledEvent.getProductId() + " - REASON: " + productReservationCancelledEvent.getReason());

        RejectOrderCommand rejectOrderCommand = RejectOrderCommand.builder()
                .orderId(productReservationCancelledEvent.getOrderId())
                .reason(productReservationCancelledEvent.getReason())
                .build();

        commandGateway.send(rejectOrderCommand);
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderRejectedEvent orderRejectedEvent) {

        log.info("ORDER REJECTED EVENT handled in SAGA: OrderId " + orderRejectedEvent.getOrderId()
                + " - REASON: " + orderRejectedEvent.getReason());

        // cuando tenemos OrderRejectedEvent también actualizamos la suscripción
        queryUpdateEmitter.emit(
                FindOrderQuery.class,
                query -> true,
                new OrderSummary(orderRejectedEvent.getOrderId(), orderRejectedEvent.getOrderStatus(), orderRejectedEvent.getReason()));
    }

    // método con el mismo deadlineName que el nuestro para que AXON lo ejecute en el caso de que haga falta
    // el argumento productReservedEvent es el payload opcional que habíamos mandado en la creación del Deadline
    @DeadlineHandler(deadlineName = PAYMENT_PROCESSING_TIMEOUT_DEADLINE)
    public void handlePaymentDeadline(ProductReservedEvent productReservedEvent) {

        log.info("Payment processing deadline took place. Sending a compensation command to cancel de product reservation.");

        // nuestro método general de rollback en la SAGA y el payload opcional que habíamos mandado
        cancelProductReservation(productReservedEvent, "Payment processing timeout");
    }

    // método para hacer ROLLBACK/COMPENSATION en varios puntos de SAGA
    private void cancelProductReservation(ProductReservedEvent productReservedEvent, String reason) {

        // si se cancela la reserva cancelamos el Deadline
        cancelDeadline();

        CancelProductReservationCommand cancelProductReservationCommand = CancelProductReservationCommand.builder()
                .orderId(productReservedEvent.getOrderId())
                .productId(productReservedEvent.getProductId())
                .quantity(productReservedEvent.getQuantity())
                .userId(productReservedEvent.getUserId())
                .reason(reason)
                .build();

        commandGateway.send(cancelProductReservationCommand);
    }

    // método para cancelar el Deadline usándolo en varios puntos de SAGA
    private void cancelDeadline() {

        // si se ha procesado el pago, se cancela el DeadLineManager porque el proceso ha ido ok
        if (scheduleId != null) {
            deadlineManager.cancelSchedule(PAYMENT_PROCESSING_TIMEOUT_DEADLINE, scheduleId);
            scheduleId = null;
        }
    }
}
