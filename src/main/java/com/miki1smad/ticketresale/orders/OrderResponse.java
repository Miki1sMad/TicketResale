package com.miki1smad.ticketresale.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long buyerId,
        String buyerEmail,
        Long reservationId,
        Long listingId,
        BigDecimal totalAmount,
        OrderStatus status,
        String idempotencyKey,
        String ticketToken,
        List<TicketResponse> tickets,
        Instant createdAt) {

    public static OrderResponse from(Order order, String rawToken) {
        List<TicketResponse> ticketResponses = order.getTickets() != null
                ? order.getTickets().stream().map(TicketResponse::from).toList()
                : List.of();

        return new OrderResponse(
                order.getId(),
                order.getBuyer().getId(),
                order.getBuyer().getEmail(),
                order.getReservation().getId(),
                order.getReservation().getListing().getId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getIdempotencyKey(),
                rawToken,
                ticketResponses,
                order.getCreatedAt());
    }
}
