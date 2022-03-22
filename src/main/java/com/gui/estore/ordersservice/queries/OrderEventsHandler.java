package com.gui.estore.ordersservice.queries;

import com.gui.estore.ordersservice.core.events.OrderCreatedEvent;
import com.gui.estore.ordersservice.model.OrderEntity;
import com.gui.estore.ordersservice.repositories.OrderRepository;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.messaging.interceptors.ExceptionHandler;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("order-group")   // agrupado con OrderLookupEventsHandler para compartir hilo de ejecución (por rollbacks)
public class OrderEventsHandler {

    private final OrderRepository orderRepository;

    @Autowired
    public OrderEventsHandler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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

    public void on(OrderCreatedEvent orderCreatedEvent) {

        OrderEntity orderEntity = new OrderEntity();

        BeanUtils.copyProperties(orderCreatedEvent, orderEntity);

        try {
            orderRepository.save(orderEntity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}