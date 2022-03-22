package com.gui.estore.ordersservice.repositories;


import com.gui.estore.ordersservice.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    OrderEntity findByProductId(String productId);
    OrderEntity findByProductIdOrTitle(String productId, String title);
}
