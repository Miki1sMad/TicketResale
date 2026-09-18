package com.miki1smad.ticketresale.events;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final ClubRepository clubRepository;
    private final StadiumRepository stadiumRepository;
    private final MatchRepository matchRepository;

    @Transactional
    @CacheEvict(value = "clubs", allEntries = true)
    public ClubResponse createClub(CreateClubRequest request) {
        if (clubRepository.findByName(request.name()).isPresent()) {
            throw new IllegalArgumentException("Club with name '" + request.name() + "' already exists");
        }
        Club club = Club.builder().name(request.name()).city(request.city()).build();
        return ClubResponse.from(clubRepository.save(club));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "clubs", key = "'all'")
    public List<ClubResponse> listClubs() {
        return clubRepository.findAll().stream().map(ClubResponse::from).toList();
    }

    @Transactional
    @CacheEvict(value = "stadiums", allEntries = true)
    public StadiumResponse createStadium(CreateStadiumRequest request) {
        Club club = null;
        if (request.clubId() != null) {
            club = clubRepository
                    .findById(request.clubId())
                    .orElseThrow(() -> new IllegalArgumentException("Club not found with id: " + request.clubId()));
        }
        Stadium stadium = Stadium.builder()
                .club(club)
                .name(request.name())
                .city(request.city())
                .capacity(request.capacity())
                .build();
        return StadiumResponse.from(stadiumRepository.save(stadium));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "stadiums", key = "'all'")
    public List<StadiumResponse> listStadiums() {
        return stadiumRepository.findAll().stream().map(StadiumResponse::from).toList();
    }

    @Transactional
    @CacheEvict(value = "matches", allEntries = true)
    public MatchResponse createMatch(CreateMatchRequest request) {
        Club homeClub = clubRepository
                .findById(request.homeClubId())
                .orElseThrow(() -> new IllegalArgumentException("Home club not found: " + request.homeClubId()));
        Club awayClub = clubRepository
                .findById(request.awayClubId())
                .orElseThrow(() -> new IllegalArgumentException("Away club not found: " + request.awayClubId()));
        Stadium stadium = stadiumRepository
                .findById(request.stadiumId())
                .orElseThrow(() -> new IllegalArgumentException("Stadium not found: " + request.stadiumId()));

        Match match = Match.builder()
                .homeClub(homeClub)
                .awayClub(awayClub)
                .stadium(stadium)
                .kickoffTime(request.kickoffTime())
                .season(request.season())
                .status(MatchStatus.SCHEDULED)
                .build();
        return MatchResponse.from(matchRepository.save(match));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "matches", key = "'all'")
    public List<MatchResponse> listMatches() {
        return matchRepository.findAllByOrderByKickoffTimeAsc().stream()
                .map(MatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "matches", key = "'upcoming'")
    public List<MatchResponse> listUpcomingMatches() {
        return matchRepository.findByKickoffTimeAfterOrderByKickoffTimeAsc(Instant.now()).stream()
                .map(MatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "matches", key = "#matchId")
    public MatchResponse getMatch(Long matchId) {
        return MatchResponse.from(getMatchEntity(matchId));
    }

    @Transactional(readOnly = true)
    public Match getMatchEntity(Long matchId) {
        return matchRepository
                .findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
    }
}
