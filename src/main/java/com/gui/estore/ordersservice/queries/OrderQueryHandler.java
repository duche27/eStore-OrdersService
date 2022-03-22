package com.gui.estore.ordersservice.queries;

import com.gui.estore.ordersservice.exceptions.OrderNotFoundException;
import com.gui.estore.ordersservice.model.OrderEntity;
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
    public OrderRestModel findOrder(FindOrderQuery findOrderQuery) {

        return orderRepository.findByOrderId(findOrderQuery.getOrderId())
                .map(orderEntity -> {
                    OrderRestModel orderRestModel = new OrderRestModel();
                    BeanUtils.copyProperties(orderEntity, orderRestModel);
                    return orderRestModel;
                })
                .orElseThrow(
                        () -> new OrderNotFoundException("No existe ninguna orden con ese id " + findOrderQuery.orderId));
    }

    @QueryHandler
    public List<OrderRestModel> findOrders(FindOrdersQuery findOrdersQuery) {

        boolean filtered = false;
        if (!Objects.isNull(findOrdersQuery.getUserId())) filtered = true;

        List<OrderEntity> storedOrders = orderRepository.findAll();

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
