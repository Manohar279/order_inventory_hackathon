package com.hackathon.orders.repository;

import com.hackathon.orders.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * SELECT ... FOR UPDATE on every inventory row in the order.
     * A second concurrent order touching the same products waits here until the
     * first transaction commits, so two orders can never both "see" the last unit.
     * Rows are locked in ascending product-id order to avoid deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId IN :ids ORDER BY i.productId")
    List<Inventory> lockByProductIds(@Param("ids") Collection<Long> ids);
}
