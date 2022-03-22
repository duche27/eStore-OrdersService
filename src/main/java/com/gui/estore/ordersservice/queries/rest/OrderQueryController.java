package com.gui.estore.ordersservice.queries.rest;

import com.gui.estore.ordersservice.queries.FindOrderQuery;
import com.gui.estore.ordersservice.queries.FindOrdersQuery;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/orders")
public class OrderQueryController {

    private final QueryGateway queryGateway;
    private final Environment env;

    public OrderQueryController(QueryGateway queryGateway, Environment env) {
        this.queryGateway = queryGateway;
        this.env = env;
    }

    @GetMapping("getOrder/{id}")
    public ResponseEntity<OrderRestModel> getOrder(@PathVariable(value = "id") String orderId) {

        FindOrderQuery findOrderQuery = FindOrderQuery.builder()
                .orderId(orderId)
                .build();

        OrderRestModel order = queryGateway.query(findOrderQuery, OrderRestModel.class).join();

        return ResponseEntity.ok(order);
    }

    @GetMapping({"getOrders", "getOrders/{userId}"})
    public List<OrderRestModel> getOrders(@PathVariable(value = "userId", required = false) String userId) {

        FindOrdersQuery findOrdersQuery = new FindOrdersQuery();

        if (!Objects.isNull(userId)) findOrdersQuery = FindOrdersQuery.builder()
                .userId(userId)
                .build();

        // join() porque devuelve un CompletableFuture
        return queryGateway.query(findOrdersQuery, ResponseTypes.multipleInstancesOf(OrderRestModel.class)).join();
    }
}
