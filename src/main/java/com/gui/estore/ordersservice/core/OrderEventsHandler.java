package com.gui.estore.ordersservice.core;

import com.gui.estore.ordersservice.core.events.OrderApprovedEvent;
import com.gui.estore.ordersservice.core.events.OrderCreatedEvent;
import com.gui.estore.ordersservice.core.events.OrderRejectedEvent;
import com.gui.estore.ordersservice.model.OrderEntity;
import com.gui.estore.ordersservice.model.OrderStatus;
import com.gui.estore.ordersservice.repositories.OrderRepository;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.messaging.interceptors.ExceptionHandler;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("order-group")
// agrupado con OrderLookupEventsHandler para compartir hilo de ejecución (por rollbacks)
public class OrderEventsHandler {

    private final OrderRepository orderRepository;

    @Autowired
    public OrderEventsHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @EventHandler
    public void on(OrderCreatedEvent orderCreatedEvent) {

        OrderEntity orderEntity = new OrderEntity();

        BeanUtils.copyProperties(orderCreatedEvent, orderEntity);

        try {
            orderRepository.save(orderEntity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void on(OrderApprovedEvent orderApprovedEvent) {

        OrderEntity orderEntity = orderRepository.findByOrderId(orderApprovedEvent.getOrderId())
                .orElseThrow(() -> new RuntimeException("No hay orden que aprovar en BD"));

        orderEntity.setOrderStatus(OrderStatus.APPROVED);

        orderRepository.save(orderEntity);
    }

    @EventHandler
    public void on(OrderRejectedEvent orderRejectedEvent) {

        OrderEntity orderEntity = orderRepository.findByOrderId(orderRejectedEvent.getOrderId())
                .orElseThrow(() -> new RuntimeException("No hay orden que rechazar en BD"));

        orderEntity.setOrderStatus(orderRejectedEvent.getOrderStatus());

        try {
            orderRepository.save(orderEntity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // lanza la excepción controlada si no persiste productEntity
    // sin persistir nada, es transaccional
    // de aquí va a OrderServiceEventHandler - después a OrderErrorHandler - excepción controlada
    @ExceptionHandler(resultType = Exception.class)
    private void handle(Exception exception) throws Exception {
        throw exception;
    }

    @ExceptionHandler(resultType = IllegalArgumentException.class)
    private void handle(IllegalArgumentException exception) throws IllegalArgumentException {
//        throw IllegalArgumentException;
    }
}
