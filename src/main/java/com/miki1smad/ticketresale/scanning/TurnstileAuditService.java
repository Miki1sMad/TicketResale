package com.miki1smad.ticketresale.scanning;

import com.miki1smad.ticketresale.events.Match;
import com.miki1smad.ticketresale.orders.ResaleTicket;
import com.miki1smad.ticketresale.seasontickets.SeasonTicket;
import com.miki1smad.ticketresale.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TurnstileAuditService {

    private final TurnstileScanLogRepository scanLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TurnstileScanLog recordLog(
            String turnstileId,
            Match match,
            User operator,
            String barcodeIdentifier,
            String barcodeHash,
            String ticketType,
            ResaleTicket resaleTicket,
            SeasonTicket seasonTicket,
            String scanResult,
            String failureReason) {

        TurnstileScanLog log = TurnstileScanLog.builder()
                .turnstileId(turnstileId)
                .match(match)
                .operator(operator)
                .barcodeIdentifier(barcodeIdentifier)
                .barcodeHash(barcodeHash)
                .ticketType(ticketType)
                .resaleTicket(resaleTicket)
                .seasonTicket(seasonTicket)
                .scanResult(scanResult)
                .failureReason(failureReason)
                .build();

        return scanLogRepository.save(log);
    }
}
