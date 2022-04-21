package com.gui.estore.ordersservice.saga;

import com.gui.estore.core.commands.*;
import com.gui.estore.core.events.*;
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
    private final String SHIPMENT_PROCESSING_TIMEOUT_DEADLINE = "shipment-processing-deadline";

    private String paymentScheduleId;
    private String shipmentScheduleId;
    private String userEmail;

    private String productId;
    private int productQuantity;
    private String userId;

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

        log.info("Orden creada OK en SAGA. Order: " + reserveProductCommand.getOrderId() +
                " and productId: " + reserveProductCommand.getProductId());

        // CALLBACK nos informará cuando el COMMAND haya sido procesado
        // mandamos COMMAND al ProductAggregate
        // se puede hacer así o como el resto de compensations con try catch
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

        productId = productReservedEvent.getProductId();
        productQuantity = productReservedEvent.getQuantity();
        userId = productReservedEvent.getUserId();

        // process user payment
        log.info("ProductReservedEvent gestionado OK en SAGA. Orden: " + productReservedEvent.getOrderId() + " - Producto: "
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
            cancelProductReservation(productReservedEvent, e.getMessage());
            return;
        }

        if (Objects.isNull(userPaymentDetails)) {
            cancelProductReservation(productReservedEvent, "Ha habido un error al recuperar los métodos de pago del usuario" + userPaymentDetails.getFirstName());
            log.error("Ha habido un error al recuperar los métodos de pago del usuario" + userPaymentDetails.getFirstName());
            return;
        }

        userEmail = userPaymentDetails.getEmail();

        // userPaymentDetails OK -> mandamos a PaymentService AGGREGATE
        log.info("Información del pago del usuario " + userPaymentDetails.getFirstName() + " recuperada OK");

        // 2 mins, pero normalmente algo como una confirmación de usuario pueden ser varios días
        // productReservedEvent payload opcional
        paymentScheduleId = deadlineManager.schedule(Duration.of(2, ChronoUnit.MINUTES),
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
            cancelProductReservation(productReservedEvent, e.getMessage());
            log.error(e.getMessage() + "Ha habido un error al ejecutar el pago del usuario" + userPaymentDetails.getFirstName());
            return;
        }

        if (Objects.isNull(result)) {
            cancelProductReservation(productReservedEvent, "Ha habido un error al ejecutar el pago del usuario" + userPaymentDetails.getFirstName());
            log.error("Ha habido un error al ejecutar el pago del usuario" + userPaymentDetails.getFirstName());
            return;
        }
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(PaymentProcessedEvent paymentProcessedEvent) {

        log.info("Pago ejecutado OK. Orden: " + paymentProcessedEvent.getOrderId());

        // 2 mins, pero normalmente algo como un envío pueden ser varios días
        shipmentScheduleId = deadlineManager.schedule(Duration.of(2, ChronoUnit.MINUTES),
                SHIPMENT_PROCESSING_TIMEOUT_DEADLINE, paymentProcessedEvent);

        ShipOrderCommand shipOrderCommand = ShipOrderCommand.builder()
                .orderId(paymentProcessedEvent.getOrderId())
                .shipmentId(UUID.randomUUID().toString())
                .build();

        String result = null;

        try {
            // enviamos el COMMAND al COMMAND GATEWAY que llegará al AGGREGATE de SHIPMENT
            result = commandGateway.sendAndWait(shipOrderCommand, 10, TimeUnit.SECONDS);

        } catch (Exception e) {
            // compensation transaction
            log.error("Ha habido un error con el ENVÍO de la orden {}", paymentProcessedEvent.getOrderId());

            CancelPaymentCommand cancelPaymentCommand = CancelPaymentCommand.builder()
                    .paymentId(paymentProcessedEvent.getPaymentId())
                    .orderId(paymentProcessedEvent.getOrderId())
                    .reason(e.getMessage()).build();

            commandGateway.send(cancelPaymentCommand);

            return;
        }
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderShippedEvent orderShippedEvent) {

        // processed user shipment
        log.info("Envío completado OK. Orden " + orderShippedEvent.getOrderId());

        SendNotificationCommand sendNotificationCommand = SendNotificationCommand.builder()
                .orderId(orderShippedEvent.getOrderId())
                .noticeId(UUID.randomUUID().toString())
                .email(userEmail)
                .build();

        String result = null;

        try {

            // enviamos el COMMAND al COMMAND GATEWAY que llegará al AGGREGATE de NOTIFICATION
            result = commandGateway.sendAndWait(sendNotificationCommand, 10, TimeUnit.SECONDS);

        } catch (Exception e) {
            // no es necesaria compensación
            log.error("Ha habido un error al enviar la notificación vía email");
            // OK procesado reserva, pago y envío -> cancelamos el Deadline
            cancelDeadline();
            log.info("Orden {} cerrada correctamente",sendNotificationCommand.getOrderId());
            // creamos nuevo orderAcceptCommand
            ApproveOrderCommand approveOrderCommand = new ApproveOrderCommand(sendNotificationCommand.getOrderId());

            commandGateway.send(approveOrderCommand);
        }
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(NotificationSentEvent notificationSentEvent) {

        // OK procesado reserva, pago, envío y notificación -> cancelamos el Deadline
        cancelDeadline();

        // processed user notification
        log.info("El cliente con email " + notificationSentEvent.getEmail() + " ha sido notificado correctamente. Orden " + notificationSentEvent.getOrderId());

        // mandamos al servicio de notificaciones

        // creamos nuevo orderAcceptCommand
        ApproveOrderCommand approveOrderCommand = new ApproveOrderCommand(notificationSentEvent.getOrderId());

        commandGateway.send(approveOrderCommand);
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderApprovedEvent orderApprovedEvent) {

        log.info("Orden completada OK en SAGA. Orden: " + orderApprovedEvent.getOrderId());

        // happy path cuando tenemos OrderApprovedEvent
        // comunica con SUBSCRIPTION QUERY posibles cambios, errores o ya no hay updates
        // parámetros: query class, predicado sin filtrar, updated object (OrderSummary que devuelve OrdersCommandController)
        queryUpdateEmitter.emit(
                FindOrderQuery.class,
                query -> true,
                new OrderSummary(orderApprovedEvent.getOrderId(), orderApprovedEvent.getOrderStatus(), "ORDER APPROVED"));
    }

    // ---------  COMPENSATIONS  ---------

    // compensation en order por cancelación en product reservation
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(ProductReservationCancelledEvent productReservationCancelledEvent) {

        log.info("Se cancela la orden {} por fallo en el pago", productReservationCancelledEvent.getOrderId());

        RejectOrderCommand rejectOrderCommand = RejectOrderCommand.builder()
                .orderId(productReservationCancelledEvent.getOrderId())
                .reason(productReservationCancelledEvent.getReason())
                .build();

        commandGateway.send(rejectOrderCommand);
    }

    // compensation en product reservation por fallo en payment
    @SagaEventHandler(associationProperty = "orderId")
    public void handle(PaymentCancelledEvent paymentCancelledEvent) {

        log.info("Se cancela la reserva de productos de la orden {} por fallo en el pago", paymentCancelledEvent.getOrderId());

        ProductReservedEvent productReservedEvent = ProductReservedEvent.builder()
                .orderId(paymentCancelledEvent.getOrderId())
                .productId(productId)
                .quantity(productQuantity)
                .userId(userId)
                .build();

        cancelProductReservation(productReservedEvent, "No se ha podido procesar el envío");
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

    // método para hacer COMPENSATION de product reservation en varios puntos de SAGA
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
        if (paymentScheduleId != null) {
            deadlineManager.cancelSchedule(PAYMENT_PROCESSING_TIMEOUT_DEADLINE, paymentScheduleId);
            paymentScheduleId = null;
        }

        // si se ha procesado el envío, se cancela el DeadLineManager porque el proceso ha ido ok
        if (shipmentScheduleId != null) {
            deadlineManager.cancelSchedule(SHIPMENT_PROCESSING_TIMEOUT_DEADLINE, shipmentScheduleId);
            shipmentScheduleId = null;
        }
    }
}
