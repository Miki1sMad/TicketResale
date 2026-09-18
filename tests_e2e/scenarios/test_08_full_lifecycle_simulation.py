import asyncio
import httpx
import pytest
from helpers.api_client import ApiClient
from helpers.data_factory import CARD_VALID, random_idempotency_key, random_user_payload
from helpers.mailpit_client import MailpitClient


@pytest.mark.concurrency
async def test_full_e2e_lifecycle_simulation(
    api_client: ApiClient,
    admin_token: str,
    get_available_barcode,
    mailpit: MailpitClient,
):
    # -------------------------------------------------------------
    # STEP 1: Admin Setup (Clubs, Stadium, Match)
    # -------------------------------------------------------------
    # Create Home & Away Clubs
    home_club_res = api_client.create_club(
        name="FK Simulation Home", city="Belgrade", token=admin_token
    )
    home_club_id = home_club_res.json()["id"] if home_club_res.status_code == 201 else 1

    away_club_res = api_client.create_club(
        name="FK Simulation Away", city="Novi Sad", token=admin_token
    )
    away_club_id = away_club_res.json()["id"] if away_club_res.status_code == 201 else 2

    # Create Stadium & Match
    stadium_res = api_client.create_stadium(
        club_id=home_club_id,
        name="Simulation Arena",
        city="Belgrade",
        capacity=45000,
        token=admin_token,
    )
    stadium_id = stadium_res.json()["id"] if stadium_res.status_code == 201 else 1

    # -------------------------------------------------------------
    # STEP 2: Season Ticket Claim by Seller "Marko"
    # -------------------------------------------------------------
    barcode = get_available_barcode()
    seller_payload = random_user_payload(season_ticket_barcode=barcode)
    seller_reg = api_client.register(seller_payload)
    assert seller_reg.status_code == 201
    seller_token = seller_reg.json()["accessToken"]
    seller_email = seller_payload["email"]

    # Verify Season Ticket claimed
    my_tickets = api_client.get_my_season_tickets(seller_token).json()
    assert len(my_tickets) >= 1
    entitlement = my_tickets[0]["entitlements"][0]
    match_id = entitlement["matchId"]
    entitlement_id = entitlement["id"]
    assert entitlement["status"] == "OWNER_HELD"

    # -------------------------------------------------------------
    # STEP 3: List Ticket on Resale Market
    # -------------------------------------------------------------
    listing_price = 3500.0
    listing_res = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=listing_price, token=seller_token
    )
    assert listing_res.status_code == 201
    listing_data = listing_res.json()
    listing_id = listing_data["id"]
    assert listing_data["status"] == "ACTIVE"

    # Verify Seller entitlement is now LISTED
    my_tickets_after_listing = api_client.get_my_season_tickets(seller_token).json()
    seller_ent = next(
        e
        for e in my_tickets_after_listing[0]["entitlements"]
        if e["id"] == entitlement_id
    )
    assert seller_ent["status"] == "LISTED"

    # -------------------------------------------------------------
    # STEP 4: High-Concurrency Discovery & Reservation Race
    # -------------------------------------------------------------
    # 20 parallel buyers try to reserve the same listing simultaneously
    num_bots = 20
    bot_users = []
    for _ in range(num_bots):
        bot_p = random_user_payload()
        bot_reg = api_client.register(bot_p)
        assert bot_reg.status_code == 201
        bot_users.append({"payload": bot_p, "token": bot_reg.json()["accessToken"]})

    limits = httpx.Limits(max_connections=50, max_keepalive_connections=25)
    timeout = httpx.Timeout(20.0)

    async with httpx.AsyncClient(
        base_url="http://localhost:8080", limits=limits, timeout=timeout
    ) as client:
        tasks = [
            client.post(
                "/api/v1/reservations",
                json={"listingId": listing_id},
                headers={"Authorization": f"Bearer {u['token']}"},
            )
            for u in bot_users
        ]
        responses = await asyncio.gather(*tasks)

    status_codes = [r.status_code for r in responses]
    assert status_codes.count(201) == 1, "Expected exactly 1 successful reservation"
    assert status_codes.count(409) == num_bots - 1, (
        "Expected other buyers to get 409 Conflict"
    )

    winner_index = status_codes.index(201)
    winner = bot_users[winner_index]
    winner_token = winner["token"]
    winner_email = winner["payload"]["email"]
    reservation_id = responses[winner_index].json()["id"]

    # Listing is now RESERVED
    assert api_client.get_listing(listing_id).json()["status"] == "RESERVED"

    # -------------------------------------------------------------
    # STEP 5: Checkout & Payment Confirmation
    # -------------------------------------------------------------
    idempotency_key = random_idempotency_key()
    checkout_res = api_client.checkout(
        reservation_id=reservation_id,
        idempotency_key=idempotency_key,
        token=winner_token,
        card_number=CARD_VALID,
    )
    assert checkout_res.status_code == 201
    order_data = checkout_res.json()
    assert order_data["status"] == "COMPLETED"
    ticket_token = order_data["ticketToken"]
    assert ticket_token.startswith("TKT_")

    # Seller entitlement is now RESOLD
    my_tickets_sold = api_client.get_my_season_tickets(seller_token).json()
    seller_ent_sold = next(
        e for e in my_tickets_sold[0]["entitlements"] if e["id"] == entitlement_id
    )
    assert seller_ent_sold["status"] == "RESOLD"

    # Listing is SOLD
    assert api_client.get_listing(listing_id).json()["status"] == "SOLD"

    # -------------------------------------------------------------
    # STEP 6: Email Verification (Mailpit)
    # -------------------------------------------------------------
    seller_email_msg = mailpit.wait_for_email(
        to_email=seller_email,
        subject_contains="Vaša karta je uspešno prodata!",
        timeout=6.0,
    )
    assert seller_email_msg is not None, "Seller notification not found in Mailpit"

    buyer_email_msg = mailpit.wait_for_email(
        to_email=winner_email,
        subject_contains="Potvrda kupovine",
        timeout=6.0,
    )
    assert buyer_email_msg is not None, "Buyer notification not found in Mailpit"

    # -------------------------------------------------------------
    # STEP 7: Match Day Turnstile Gate Entry & Anti-Replay
    # -------------------------------------------------------------
    # 7.1 Original seller attempts entry with old season ticket -> 409 REJECTED
    seller_scan = api_client.validate_turnstile(
        barcode=barcode,
        turnstile_id="GATE-NORTH-1",
        match_id=match_id,
        token=admin_token,
    )
    assert seller_scan.status_code == 409

    # 7.2 Winner scans ResaleTicket at Gate 1 -> 200 GRANTED
    winner_scan_1 = api_client.validate_turnstile(
        barcode=ticket_token,
        turnstile_id="GATE-NORTH-1",
        match_id=match_id,
        token=admin_token,
    )
    assert winner_scan_1.status_code == 200
    assert winner_scan_1.json()["result"] == "GRANTED"

    # 7.3 Duplicate scan attempt at Gate 2 -> 409 REJECTED (Anti-Replay)
    winner_scan_2 = api_client.validate_turnstile(
        barcode=ticket_token,
        turnstile_id="GATE-SOUTH-1",
        match_id=match_id,
        token=admin_token,
    )
    assert winner_scan_2.status_code == 409
