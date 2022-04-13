package com.gui.estore.ordersservice.queries;

import com.gui.estore.ordersservice.exceptions.OrderNotFoundException;
import com.gui.estore.ordersservice.model.OrderEntity;
import com.gui.estore.ordersservice.model.OrderSummary;
import com.gui.estore.ordersservice.queries.rest.OrderRestModel;
import com.gui.estore.ordersservice.repositories.OrderRepository;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class OrderQueryHandler {

    OrderRepository orderRepository;

    public OrderQueryHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @QueryHandler
    public OrderSummary findOrder(FindOrderQuery findOrderQuery) {

        OrderEntity orderEntity = orderRepository.findByOrderId(findOrderQuery.getOrderId()).orElseThrow(
                        () -> new OrderNotFoundException("No existe ninguna orden con ese id " + findOrderQuery.orderId));

//        OrderRestModel orderRestModel = new OrderRestModel();
//        BeanUtils.copyProperties(orderEntity, orderRestModel);
//        return orderRestModel;

        return new OrderSummary(orderEntity.getOrderId(), orderEntity.getOrderStatus(), "");
    }

    @QueryHandler
    public List<OrderRestModel> findOrders(FindOrdersQuery findOrdersQuery) {

        boolean filtered = !Objects.isNull(findOrdersQuery.getUserId());

        List<OrderEntity> storedOrders = orderRepository.findAll();

        // si filtered = false, devuelvo todas las ordenes porque filtra por un orderId inexistente
        return storedOrders.stream()
                .filter(filtered ? orderEntity -> orderEntity.getUserId().equals(findOrdersQuery.getUserId())
                        : orderEntity -> !Objects.isNull(orderEntity.getOrderId()))
                .map(orderEntity -> {
                    OrderRestModel orderRestModel = new OrderRestModel();
                    BeanUtils.copyProperties(orderEntity, orderRestModel);
                    return orderRestModel;
                }).collect(Collectors.toList());
    }
}
