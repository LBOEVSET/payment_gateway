package com.consoleshop.payment.repository;

import com.consoleshop.payment.domain.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, String> {

    @Modifying
    @Query("UPDATE Event e SET e.stock = e.stock + :qty WHERE e.id = :id")
    void incrementStock(@Param("id") String id, @Param("qty") int qty);
}
