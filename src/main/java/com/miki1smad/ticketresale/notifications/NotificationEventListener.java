package com.miki1smad.ticketresale.notifications;

import com.miki1smad.ticketresale.auth.UserLoggedInEvent;
import com.miki1smad.ticketresale.auth.UserRegisteredEvent;
import com.miki1smad.ticketresale.listings.TicketListedEvent;
import com.miki1smad.ticketresale.orders.OrderCompletedEvent;
import com.miki1smad.ticketresale.orders.ResaleTicketIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final EmailService emailService;
    private final BarcodeGeneratorService barcodeGeneratorService;

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
        log.info("Handling ResaleTicketIssuedEvent for ticketId={}", event.ticketId());
        sendTicketEmail(event);
    }

    private void sendTicketEmail(ResaleTicketIssuedEvent event) {
        String subject = "Vaša ulaznica i bar-kod za " + event.matchTitle();

        byte[] barcodeImage = barcodeGeneratorService.generateCode128BarcodeImage(event.rawBarcode(), 500, 120);
        byte[] qrCodeImage = barcodeGeneratorService.generateQrCodeImage(event.rawBarcode(), 260, 260);

        String plainText = String.format(
                "Vaša digitalna ulaznica je spremna!\n\n" + "Utakmica: %s\n"
                        + "Sedište: %s\n"
                        + "Broj porudžbine: %d\n\n"
                        + "----------------------------------------\n"
                        + "ULAZNI KOD / TOKEN:\n%s\n"
                        + "----------------------------------------\n\n"
                        + "Molimo prislonite bar-kod ili QR kod na turnstile skener prilikom ulaska na stadion.\n\n"
                        + "TicketResale Tim",
                event.matchTitle(), event.seatDetails(), event.orderId(), event.rawBarcode());

        String htmlContent =
                String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f4f6f8; margin: 0; padding: 20px; }
                        .ticket-card { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1); border: 1px solid #e2e8f0; }
                        .header { background: #0f172a; color: #ffffff; padding: 24px; text-align: center; }
                        .header h1 { margin: 0; font-size: 20px; font-weight: 700; letter-spacing: 0.5px; }
                        .content { padding: 24px; color: #334155; }
                        .match-title { font-size: 18px; font-weight: 600; color: #0f172a; margin-bottom: 16px; text-align: center; }
                        .details-table { width: 100%%; border-collapse: collapse; margin-bottom: 24px; }
                        .details-table td { padding: 8px 12px; border-bottom: 1px solid #f1f5f9; font-size: 14px; }
                        .details-table td.label { font-weight: 600; color: #64748b; width: 35%%; }
                        .details-table td.value { color: #0f172a; font-weight: 500; }
                        .scanner-section { background: #f8fafc; border: 2px dashed #cbd5e1; border-radius: 8px; padding: 20px; text-align: center; margin-bottom: 20px; }
                        .scanner-title { font-size: 14px; font-weight: 700; color: #475569; text-transform: uppercase; margin-bottom: 12px; }
                        .code-image { max-width: 100%%; height: auto; margin: 8px 0; }
                        .token-box { font-family: monospace; font-size: 13px; background: #e2e8f0; padding: 8px 12px; border-radius: 6px; word-break: break-all; margin-top: 12px; color: #1e293b; }
                        .footer { padding: 16px 24px; background: #f8fafc; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #e2e8f0; }
                    </style>
                </head>
                <body>
                    <div class="ticket-card">
                        <div class="header">
                            <h1>TICKETRESALE ULAZNICA</h1>
                        </div>
                        <div class="content">
                            <div class="match-title">%s</div>
                            <table class="details-table">
                                <tr>
                                    <td class="label">Sedište:</td>
                                    <td class="value">%s</td>
                                </tr>
                                <tr>
                                    <td class="label">Porudžbina:</td>
                                    <td class="value">#%d</td>
                                </tr>
                            </table>

                            <div class="scanner-section">
                                <div class="scanner-title">QR Kod za Skener</div>
                                <img src="cid:qrCodeImage" alt="QR Kod Ulaznice" class="code-image" style="width: 200px; height: 200px;" />
                                <div class="scanner-title" style="margin-top: 16px;">1D Bar-kod</div>
                                <img src="cid:barcodeImage" alt="Bar-kod Ulaznice" class="code-image" style="max-width: 90%%;" />
                                <div class="token-box">%s</div>
                            </div>

                            <p style="text-align: center; font-size: 13px; color: #64748b; margin: 0;">
                                Prislonite QR kod ili bar-kod na čitač turnstila prilikom ulaska.
                            </p>
                        </div>
                        <div class="footer">
                            Lep provod na utakmici želi vam TicketResale Tim!
                        </div>
                    </div>
                </body>
                </html>
                """, event.matchTitle(), event.seatDetails(), event.orderId(), event.rawBarcode());

        emailService.sendTicketEmailWithBarcode(
                event.buyerEmail(), subject, htmlContent, plainText, event.rawBarcode(), barcodeImage, qrCodeImage);
    }
}
