package com.gui.estore.ordersservice.commands.rest;

import com.gui.estore.ordersservice.commands.CreateOrderCommand;
import com.gui.estore.ordersservice.model.OrderStatus;
import com.gui.estore.ordersservice.model.OrderSummary;
import com.gui.estore.ordersservice.queries.FindOrderQuery;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("orders")
public class OrdersCommandController {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public OrdersCommandController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    @PostMapping("newOrder")
    public OrderSummary createOrder(@Valid @RequestBody OrderCreateRest orderCreateRest) {

        String userId = "27b95829-4f3f-4ddf-8983-151ba010e35b";
        String orderId = UUID.randomUUID().toString();

        CreateOrderCommand createOrderCommand = CreateOrderCommand.builder()
                .orderId(orderId)
                .userId(userId)
                .productId(orderCreateRest.getProductId())
                .quantity(orderCreateRest.getQuantity())
                .addressId(orderCreateRest.getAddressId())
                .orderStatus(OrderStatus.CREATED)
                .build();

        // parámetros: query a lanzar, tipo de respuesta inicial, tipo de respuesta final con el incremental update
        SubscriptionQueryResult<OrderSummary, OrderSummary> subscriptionQueryResult = queryGateway.subscriptionQuery(new FindOrderQuery(orderId),
                ResponseTypes.instanceOf(OrderSummary.class),
                ResponseTypes.instanceOf(OrderSummary.class));

        try {
            commandGateway.sendAndWait(createOrderCommand);
            // updates() se suscribe al FLUX (incremental update) para cualquier cambio
            // blockFirst() bloquea el FLUX indefinidamente hasta que se reciba una respuesta
            return subscriptionQueryResult.updates().blockFirst();
        } finally {
            subscriptionQueryResult.close();
        }
    }
}
