package com.gui.estore.ordersservice.commands.rest;

import com.gui.estore.ordersservice.commands.CreateOrderCommand;
import com.gui.estore.ordersservice.model.OrderStatus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("orders")
public class OrdersCommandController {

    private final CommandGateway commandGateway;

    public OrdersCommandController(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @PostMapping("newOrder")
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderRestModel createOrderRestModel) {

        CreateOrderCommand createOrderCommand = CreateOrderCommand.builder()
                .orderId(UUID.randomUUID().toString())
                .userId("27b95829-4f3f-4ddf-8983-151ba010e35b")
                .productId(createOrderRestModel.getProductId())
                .quantity(createOrderRestModel.getQuantity())
                .addressId(createOrderRestModel.getAddressId())
                .orderStatus(OrderStatus.CREATED)
                .build();

        commandGateway.sendAndWait(createOrderCommand);

        return new ResponseEntity<>("lalala ORDER POST con addressID " + createOrderRestModel.getAddressId() + " - id " + createOrderRestModel.getProductId(), HttpStatus.CREATED);
    }
}
