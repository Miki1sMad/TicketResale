package com.miki1smad.ticketresale.seasontickets;

import com.miki1smad.ticketresale.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class SeasonTicketSeeder implements CommandLineRunner {

    private final SeasonTicketRepository seasonTicketRepository;
    private final ClubRepository clubRepository;
    private final StadiumRepository stadiumRepository;
    private final SectionRepository sectionRepository;
    private final RowRepository rowRepository;
    private final SeatRepository seatRepository;
    private final MatchRepository matchRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (seasonTicketRepository.count() > 0) {
            return;
        }

        log.info("Seeding 20 initial season tickets with barcodes...");

        // 1. Get or create Home Club & Away Club
        Club homeClub = clubRepository.findByName("FK Crvena Zvezda")
                .orElseGet(() -> clubRepository.save(Club.builder().name("FK Crvena Zvezda").city("Belgrade").build()));

        Club awayClub = clubRepository.findByName("FK Partizan")
                .orElseGet(() -> clubRepository.save(Club.builder().name("FK Partizan").city("Belgrade").build()));

        // 2. Get or create Stadium
        List<Stadium> stadiums = stadiumRepository.findByClubId(homeClub.getId());
        Stadium stadium = stadiums.isEmpty()
                ? stadiumRepository.save(Stadium.builder().club(homeClub).name("Rajko Mitic").city("Belgrade").capacity(53000).build())
                : stadiums.getFirst();

        // 3. Get or create Section & Row
        List<Section> sections = sectionRepository.findByStadiumId(stadium.getId());
        Section section = sections.isEmpty()
                ? sectionRepository.save(Section.builder().stadium(stadium).name("Zapad").category("VIP").build())
                : sections.getFirst();

        List<Row> rows = rowRepository.findBySectionId(section.getId());
        Row row = rows.isEmpty()
                ? rowRepository.save(Row.builder().section(section).rowNumber("1").build())
                : rows.getFirst();

        // 4. Create 20 seats and 20 season tickets
        List<Seat> existingSeats = seatRepository.findByRowId(row.getId());
        List<Seat> seats = new ArrayList<>(existingSeats);
        for (int i = existingSeats.size() + 1; i <= 20; i++) {
            Seat seat = seatRepository.save(Seat.builder().row(row).seatNumber(String.valueOf(i)).build());
            seats.add(seat);
        }

        // 5. Seed 20 season tickets
        for (int i = 1; i <= 20; i++) {
            String barcode = String.format("ST-2025-%03d", i);
            Seat seat = seats.get(i - 1);

            SeasonTicket ticket = SeasonTicket.builder()
                    .barcode(barcode)
                    .club(homeClub)
                    .season("2025/2026")
                    .seat(seat)
                    .status(SeasonTicketStatus.UNCLAIMED)
                    .build();
            seasonTicketRepository.save(ticket);
        }

        // 6. Ensure at least two home matches exist for 2025/2026
        List<Match> existingMatches = matchRepository.findBySeasonAndHomeClubId("2025/2026", homeClub.getId());
        if (existingMatches.isEmpty()) {
            matchRepository.save(Match.builder()
                    .homeClub(homeClub)
                    .awayClub(awayClub)
                    .stadium(stadium)
                    .season("2025/2026")
                    .kickoffTime(Instant.now().plus(7, ChronoUnit.DAYS))
                    .status(MatchStatus.SCHEDULED)
                    .build());

            matchRepository.save(Match.builder()
                    .homeClub(homeClub)
                    .awayClub(awayClub)
                    .stadium(stadium)
                    .season("2025/2026")
                    .kickoffTime(Instant.now().plus(14, ChronoUnit.DAYS))
                    .status(MatchStatus.SCHEDULED)
                    .build());
        }

        log.info("Successfully seeded 20 season tickets (ST-2025-001 to ST-2025-020).");
    }
}
