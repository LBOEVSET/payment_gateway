package com.consoleshop.payment.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Minimal Event — used only to increment stock on payment failure. */
@Entity
@Table(name = "Event")
@Getter
@Setter
public class Event {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "stock")
    private int stock;
}
