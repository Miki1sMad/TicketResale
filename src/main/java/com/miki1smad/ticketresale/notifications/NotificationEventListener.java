package com.miki1smad.ticketresale.notifications;

import com.miki1smad.ticketresale.auth.UserLoggedInEvent;
import com.miki1smad.ticketresale.auth.UserRegisteredEvent;
import com.miki1smad.ticketresale.listings.TicketListedEvent;
import com.miki1smad.ticketresale.orders.OrderCompletedEvent;
import com.miki1smad.ticketresale.orders.ResaleTicketIssuedEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final EmailService emailService;

    @Value("${ticketresale.mail.ticket-delay-seconds:30}")
    private int ticketDelaySeconds;

    @ApplicationModuleListener
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Handling UserRegisteredEvent for email={}", event.email());
        String subject = "Dobrodošli na TicketResale platformu!";
        String body = String.format(
                "Zdravo %s,\n\nVaš nalog sa email adresom %s je uspešno kreiran na TicketResale berzi.\n"
                        + "Možete pregledati dostupne utakmice, claim-ovati vašu sezonsku kartu ili kupiti ulaznice.\n\n"
                        + "Sportski pozdrav,\nTicketResale Tim",
                event.firstName() != null ? event.firstName() : "korisniče", event.email());
        emailService.sendEmail(event.email(), subject, body);
    }

    @ApplicationModuleListener
    public void onUserLoggedIn(UserLoggedInEvent event) {
        log.info("Handling UserLoggedInEvent for email={}", event.email());
        String subject = "Nova prijava na vaš TicketResale nalog";
        String body = String.format(
                "Obaveštenje o bezbednosti:\n\nZabeležena je nova uspešna prijava na nalog %s u %s.\n"
                        + "Ukoliko niste vi inicirali ovu prijavu, molimo vas da odmah promenite lozinku.\n\n"
                        + "TicketResale Bezbednosni Tim",
                event.email(), event.loggedInAt());
        emailService.sendEmail(event.email(), subject, body);
    }

    @ApplicationModuleListener
    public void onTicketListed(TicketListedEvent event) {
        log.info("Handling TicketListedEvent for listingId={}", event.listingId());
        String subject = "Potvrda: Vaša karta je oglašena na berzi";
        String body = String.format(
                "Vaša karta za utakmicu '%s' je uspešno stavljena na berzu po ceni od %s RSD.\n"
                        + "Broj oglasa (Listing ID): %d\n\n"
                        + "Kada neko kupi vašu kartu, bićete odmah obavešteni putem emaila.\n\n"
                        + "TicketResale Tim",
                event.matchTitle(), event.price(), event.listingId());
        emailService.sendEmail(event.sellerEmail(), subject, body);
    }

    @ApplicationModuleListener
    public void onOrderCompleted(OrderCompletedEvent event) {
        log.info("Handling OrderCompletedEvent for orderId={}", event.orderId());

        // 1. Mejl prodavcu da je karta prodata
        String sellerSubject = "Vaša karta je uspešno prodata!";
        String sellerBody = String.format(
                "Obaveštavamo vas da je vaša karta za utakmicu '%s' uspešno prodata na berzi.\n"
                        + "Iznos: %s RSD\n"
                        + "Broj porudžbine: %d\n\n"
                        + "Vaše pravo ulaska za ovu utakmicu je preneto na kupca.\n\n"
                        + "TicketResale Tim",
                event.matchTitle(), event.price(), event.orderId());
        emailService.sendEmail(event.sellerEmail(), sellerSubject, sellerBody);

        // 2. Mejl kupcu sa potvrdom porudžbine
        String buyerSubject = "Potvrda kupovine - Porudžbina #" + event.orderId();
        String buyerBody = String.format(
                "Hvala vam na kupovini!\n\n" + "Uspešno ste kupili kartu za utakmicu '%s' u iznosu od %s RSD.\n"
                        + "Broj vaše porudžbine: %d\n\n"
                        + "Uskoro će vam u posebnom mejlu stići vaša digitalna ulaznica i bar-kod.\n\n"
                        + "TicketResale Tim",
                event.matchTitle(), event.price(), event.orderId());
        emailService.sendEmail(event.buyerEmail(), buyerSubject, buyerBody);
    }

    @ApplicationModuleListener
    public void onResaleTicketIssued(ResaleTicketIssuedEvent event) {
        log.info("Handling ResaleTicketIssuedEvent for ticketId={}, delay={}s", event.ticketId(), ticketDelaySeconds);
        if (ticketDelaySeconds > 0) {
            CompletableFuture.runAsync(
                    () -> sendTicketEmail(event),
                    CompletableFuture.delayedExecutor(ticketDelaySeconds, TimeUnit.SECONDS));
        } else {
            sendTicketEmail(event);
        }
    }

    private void sendTicketEmail(ResaleTicketIssuedEvent event) {
        String subject = "Vaša ulaznica i bar-kod za " + event.matchTitle();
        String body = String.format(
                "Vaša digitalna ulaznica je spremna!\n\n" + "Utakmica: %s\n"
                        + "Sedište: %s\n"
                        + "Broj porudžbine: %d\n\n"
                        + "----------------------------------------\n"
                        + "ULAZNI KOD / TOKEN:\n%s\n"
                        + "----------------------------------------\n\n"
                        + "Molimo pokažite ovaj bar-kod redarima na turnstilu prilikom ulaska na stadion.\n\n"
                        + "Lep provod na utakmici želi vam TicketResale Tim!",
                event.matchTitle(), event.seatDetails(), event.orderId(), event.rawBarcode());
        emailService.sendEmail(event.buyerEmail(), subject, body);
    }
}
