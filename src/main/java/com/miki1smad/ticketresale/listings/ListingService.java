package com.miki1smad.ticketresale.listings;

import com.miki1smad.ticketresale.seasontickets.EntitlementStatus;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlement;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlementRepository;
import com.miki1smad.ticketresale.users.User;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final MatchEntitlementRepository matchEntitlementRepository;

    @Transactional
    @CacheEvict(value = "listings", allEntries = true)
    public ListingResponse createListing(CreateListingRequest request, User seller) {
        MatchEntitlement entitlement = matchEntitlementRepository
                .findById(request.matchEntitlementId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Match entitlement not found with ID: " + request.matchEntitlementId()));

        User owner = entitlement.getSeasonTicket().getOwner();
        if (owner == null || !owner.getId().equals(seller.getId())) {
            throw new IllegalArgumentException("You do not own this match entitlement");
        }

        if (entitlement.getStatus() != EntitlementStatus.OWNER_HELD) {
            throw new IllegalStateException(
                    "Match entitlement is not available for listing (status: " + entitlement.getStatus() + ")");
        }

        if (entitlement.getMatch().getKickoffTime().isBefore(Instant.now())) {
            throw new IllegalStateException("Cannot list tickets for a match that has already started");
        }

        entitlement.setStatus(EntitlementStatus.LISTED);
        matchEntitlementRepository.save(entitlement);

        Listing listing = Listing.builder()
                .seller(seller)
                .matchEntitlement(entitlement)
                .price(request.price())
                .status(ListingStatus.ACTIVE)
                .build();

        listing = listingRepository.save(listing);
        return ListingResponse.from(listing);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "listings", key = "#matchId != null ? #matchId : 'all'")
    public List<ListingResponse> getActiveListings(Long matchId) {
        List<Listing> listings;
        if (matchId != null) {
            listings = listingRepository.findByMatchEntitlementMatchIdAndStatus(matchId, ListingStatus.ACTIVE);
        } else {
            listings = listingRepository.findByStatus(ListingStatus.ACTIVE);
        }
        return listings.stream().map(ListingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ListingResponse getListingById(Long id) {
        return listingRepository
                .findById(id)
                .map(ListingResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Listing not found with ID: " + id));
    }

    @Transactional
    @CacheEvict(value = "listings", allEntries = true)
    public ListingResponse cancelListing(Long id, User seller) {
        Listing listing = listingRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Listing not found with ID: " + id));

        if (!listing.getSeller().getId().equals(seller.getId())) {
            throw new IllegalArgumentException("You do not own this listing");
        }

        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new IllegalStateException("Only active listings can be cancelled");
        }

        listing.setStatus(ListingStatus.CANCELLED);
        listing.getMatchEntitlement().setStatus(EntitlementStatus.OWNER_HELD);
        return ListingResponse.from(listingRepository.save(listing));
    }
}
