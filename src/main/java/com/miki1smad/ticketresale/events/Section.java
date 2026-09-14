package com.miki1smad.ticketresale.events;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "sections",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_stadium_section",
                    columnNames = {"stadium_id", "name"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stadium_id", nullable = false)
    private Stadium stadium;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private String category = "STANDARD";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
