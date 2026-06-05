package com.consoleshop.payment.repository;

import com.consoleshop.payment.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id")
    void incrementStock(@Param("id") String id, @Param("qty") int qty);
}
