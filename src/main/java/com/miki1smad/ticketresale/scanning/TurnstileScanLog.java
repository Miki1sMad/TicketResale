package com.miki1smad.ticketresale.scanning;

import com.miki1smad.ticketresale.events.Match;
import com.miki1smad.ticketresale.orders.ResaleTicket;
import com.miki1smad.ticketresale.seasontickets.SeasonTicket;
import com.miki1smad.ticketresale.users.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "turnstile_scan_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnstileScanLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "turnstile_id", nullable = false, length = 50)
    private String turnstileId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(name = "barcode_identifier", length = 120)
    private String barcodeIdentifier;

    @Column(name = "barcode_hash", length = 64)
    private String barcodeHash;

    @Column(name = "ticket_type", nullable = false, length = 50)
    private String ticketType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resale_ticket_id")
    private ResaleTicket resaleTicket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_ticket_id")
    private SeasonTicket seasonTicket;

    @Column(name = "scan_result", nullable = false, length = 50)
    private String scanResult;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "scanned_at", nullable = false, updatable = false)
    private Instant scannedAt;
}
