package com.miki1smad.ticketresale.events;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_row_seat",
                    columnNames = {"row_id", "seat_number"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "row_id", nullable = false)
    private Row row;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
