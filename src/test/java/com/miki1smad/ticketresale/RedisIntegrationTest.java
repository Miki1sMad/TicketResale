package com.miki1smad.ticketresale;

import static org.assertj.core.api.Assertions.assertThat;

import com.miki1smad.ticketresale.events.ClubResponse;
import com.miki1smad.ticketresale.events.CreateClubRequest;
import com.miki1smad.ticketresale.events.CreateMatchRequest;
import com.miki1smad.ticketresale.events.CreateStadiumRequest;
import com.miki1smad.ticketresale.events.EventService;
import com.miki1smad.ticketresale.events.MatchResponse;
import com.miki1smad.ticketresale.events.StadiumResponse;
import com.miki1smad.ticketresale.listings.CreateListingRequest;
import com.miki1smad.ticketresale.listings.CreateReservationRequest;
import com.miki1smad.ticketresale.listings.ListingResponse;
import com.miki1smad.ticketresale.listings.ListingService;
import com.miki1smad.ticketresale.listings.ReservationResponse;
import com.miki1smad.ticketresale.listings.ReservationService;
import com.miki1smad.ticketresale.seasontickets.EntitlementStatus;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlement;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlementRepository;
import com.miki1smad.ticketresale.seasontickets.SeasonTicket;
import com.miki1smad.ticketresale.seasontickets.SeasonTicketRepository;
import com.miki1smad.ticketresale.seasontickets.SeasonTicketService;
import com.miki1smad.ticketresale.users.Role;
import com.miki1smad.ticketresale.users.User;
import com.miki1smad.ticketresale.users.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;

class RedisIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private ListingService listingService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SeasonTicketService seasonTicketService;

    @Autowired
    private SeasonTicketRepository seasonTicketRepository;

    @Autowired
    private MatchEntitlementRepository matchEntitlementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedissonClient redissonClient;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("Should cache stadiums and evict cache when new stadium is created")
    void testStadiumsCacheAndEviction() {
        Cache stadiumsCache = cacheManager.getCache("stadiums");
        assertThat(stadiumsCache).isNotNull();
        stadiumsCache.clear();

        // Initial read -> populates cache
        List<StadiumResponse> initialStadiums = eventService.listStadiums();
        assertThat(initialStadiums).isNotEmpty();

        Object cachedValue = stadiumsCache.get("all");
        assertThat(cachedValue).isNotNull();

        // Create new club and stadium -> evicts "stadiums" cache
        ClubResponse club =
                eventService.createClub(new CreateClubRequest("Redis Club " + System.currentTimeMillis(), "Belgrade"));
        eventService.createStadium(new CreateStadiumRequest(club.id(), "Redis Arena", "Belgrade", 25000));

        // After createStadium, cache must be evicted (null or empty)
        assertThat(stadiumsCache.get("all")).isNull();

        // Next read repopulates cache
        List<StadiumResponse> updatedStadiums = eventService.listStadiums();
        assertThat(updatedStadiums.size()).isEqualTo(initialStadiums.size() + 1);
        assertThat(stadiumsCache.get("all")).isNotNull();
    }

    @Test
    @DisplayName("Should cache matches and evict cache when new match is created")
    void testMatchesCacheAndEviction() {
        Cache matchesCache = cacheManager.getCache("matches");
        assertThat(matchesCache).isNotNull();
        matchesCache.clear();

        List<MatchResponse> initialMatches = eventService.listMatches();
        assertThat(matchesCache.get("all")).isNotNull();

        // Create a new match -> should evict "matches" cache
        ClubResponse home =
                eventService.createClub(new CreateClubRequest("Home Club " + System.currentTimeMillis(), "Belgrade"));
        ClubResponse away =
                eventService.createClub(new CreateClubRequest("Away Club " + System.currentTimeMillis(), "Novi Sad"));
        StadiumResponse stadium = eventService.listStadiums().getFirst();

        eventService.createMatch(new CreateMatchRequest(
                home.id(), away.id(), stadium.id(), Instant.now().plusSeconds(86400 * 3), "2025/2026"));

        // Cache must be evicted
        assertThat(matchesCache.get("all")).isNull();
        assertThat(matchesCache.get("upcoming")).isNull();

        List<MatchResponse> updatedMatches = eventService.listMatches();
        assertThat(updatedMatches.size()).isEqualTo(initialMatches.size() + 1);
        assertThat(matchesCache.get("all")).isNotNull();
    }

    @Test
    @DisplayName("Should cache active listings and evict on reservation")
    void testListingsCacheAndEvictionOnReservation() {
        Cache listingsCache = cacheManager.getCache("listings");
        assertThat(listingsCache).isNotNull();
        listingsCache.clear();

        // 1. Setup seller and season ticket
        User seller = userRepository.save(User.builder()
                .email("redis_seller_" + System.currentTimeMillis() + "@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Redis")
                .lastName("Seller")
                .role(Role.USER)
                .build());

        seasonTicketService.claimSeasonTicket(seller.getId(), "ST-2025-006");
        SeasonTicket ticket =
                seasonTicketRepository.findByBarcode("ST-2025-006").orElseThrow();
        MatchEntitlement entitlement =
                matchEntitlementRepository.findBySeasonTicketId(ticket.getId()).getFirst();
        entitlement.setStatus(EntitlementStatus.OWNER_HELD);
        matchEntitlementRepository.save(entitlement);

        // 2. Create listing -> evicts listings cache
        ListingResponse listing = listingService.createListing(
                new CreateListingRequest(entitlement.getId(), new BigDecimal("3500.00")), seller);

        // Query active listings -> populates cache
        List<ListingResponse> activeListings = listingService.getActiveListings(listing.matchId());
        assertThat(activeListings).isNotEmpty();
        assertThat(listingsCache.get(listing.matchId())).isNotNull();

        // 3. Create buyer and reserve -> evicts listings cache
        User buyer = userRepository.save(User.builder()
                .email("redis_buyer_" + System.currentTimeMillis() + "@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Redis")
                .lastName("Buyer")
                .role(Role.USER)
                .build());

        ReservationResponse reservation =
                reservationService.createReservation(new CreateReservationRequest(listing.id()), buyer);
        assertThat(reservation).isNotNull();

        // Verify listings cache was evicted upon reservation
        assertThat(listingsCache.get(listing.matchId())).isNull();
    }

    @Test
    @DisplayName("Should acquire and release Redisson distributed lock correctly")
    void testRedissonDistributedLock() throws InterruptedException {
        String lockKey = "lock:test:" + System.currentTimeMillis();
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = lock.tryLock(2, 5, TimeUnit.SECONDS);
        assertThat(acquired).isTrue();
        assertThat(lock.isHeldByCurrentThread()).isTrue();

        lock.unlock();
        assertThat(lock.isHeldByCurrentThread()).isFalse();
    }
}
