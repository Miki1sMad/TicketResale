import asyncio
import httpx
import pytest
from helpers.api_client import ApiClient
from helpers.data_factory import CARD_VALID, random_idempotency_key


def test_01_resale_ticket_validation_and_anti_replay(
    api_client: ApiClient, register_user, get_available_barcode, admin_token
):
    # 1. Purchase a ticket
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    buyer = register_user()

    entitlement = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]
    match_id = entitlement["matchId"]
    entitlement_id = entitlement["id"]

    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2500.0, token=seller["accessToken"]
    ).json()["id"]
    reservation_id = api_client.create_reservation(
        listing_id=listing_id, token=buyer["accessToken"]
    ).json()["id"]

    order = api_client.checkout(
        reservation_id=reservation_id,
        idempotency_key=random_idempotency_key(),
        token=buyer["accessToken"],
        card_number=CARD_VALID,
    ).json()
    ticket_token = order["ticketToken"]

    # 2. First scan -> 200 GRANTED
    scan_res1 = api_client.validate_turnstile(
        barcode=ticket_token,
        turnstile_id="GATE-NORTH-1",
        match_id=match_id,
        token=admin_token,
    )
    assert scan_res1.status_code == 200
    scan_data1 = scan_res1.json()
    assert scan_data1["result"] == "GRANTED"
    assert scan_data1["ticketType"] == "RESALE_TICKET"

    # 3. Second scan (Replay) -> 409 Conflict
    scan_res2 = api_client.validate_turnstile(
        barcode=ticket_token,
        turnstile_id="GATE-NORTH-2",
        match_id=match_id,
        token=admin_token,
    )
    assert scan_res2.status_code == 409


@pytest.mark.concurrency
async def test_02_double_entry_concurrent_race_condition(
    api_client: ApiClient, register_user, get_available_barcode, admin_token
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    buyer = register_user()

    entitlement = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]
    match_id = entitlement["matchId"]
    entitlement_id = entitlement["id"]

    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2500.0, token=seller["accessToken"]
    ).json()["id"]
    reservation_id = api_client.create_reservation(
        listing_id=listing_id, token=buyer["accessToken"]
    ).json()["id"]

    order = api_client.checkout(
        reservation_id=reservation_id,
        idempotency_key=random_idempotency_key(),
        token=buyer["accessToken"],
        card_number=CARD_VALID,
    ).json()
    ticket_token = order["ticketToken"]

    limits = httpx.Limits(max_connections=20, max_keepalive_connections=10)
    timeout = httpx.Timeout(15.0)

    async with httpx.AsyncClient(
        base_url="http://localhost:8080", limits=limits, timeout=timeout
    ) as client:
        req1 = client.post(
            "/api/v1/turnstile/validate",
            json={
                "barcode": ticket_token,
                "turnstileId": "GATE-EAST-1",
                "matchId": match_id,
            },
            headers={"Authorization": f"Bearer {admin_token}"},
        )
        req2 = client.post(
            "/api/v1/turnstile/validate",
            json={
                "barcode": ticket_token,
                "turnstileId": "GATE-WEST-1",
                "matchId": match_id,
            },
            headers={"Authorization": f"Bearer {admin_token}"},
        )
        responses = await asyncio.gather(req1, req2)

    status_codes = [r.status_code for r in responses]
    assert 200 in status_codes, "Expected one granted entry"
    assert 409 in status_codes, "Expected one rejected replay entry"


def test_03_resold_season_ticket_entry_rejected(
    api_client: ApiClient, register_user, get_available_barcode, admin_token
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    buyer = register_user()

    entitlement = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]
    match_id = entitlement["matchId"]
    entitlement_id = entitlement["id"]

    # Sell the ticket
    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=3000.0, token=seller["accessToken"]
    ).json()["id"]
    reservation_id = api_client.create_reservation(
        listing_id=listing_id, token=buyer["accessToken"]
    ).json()["id"]
    api_client.checkout(
        reservation_id=reservation_id,
        idempotency_key=random_idempotency_key(),
        token=buyer["accessToken"],
        card_number=CARD_VALID,
    )

    # Original seller tries to enter using season ticket barcode -> 409 Conflict
    scan_res = api_client.validate_turnstile(
        barcode=barcode,
        turnstile_id="GATE-MAIN",
        match_id=match_id,
        token=admin_token,
    )
    assert scan_res.status_code == 409


def test_04_valid_season_ticket_entry_and_anti_replay(
    api_client: ApiClient, register_user, get_available_barcode, admin_token
):
    barcode = get_available_barcode()
    user = register_user(season_ticket_barcode=barcode)

    entitlement = api_client.get_my_season_tickets(user["accessToken"]).json()[0][
        "entitlements"
    ][0]
    match_id = entitlement["matchId"]

    # 1. Valid entry with season ticket -> 200 GRANTED
    scan_res1 = api_client.validate_turnstile(
        barcode=barcode,
        turnstile_id="GATE-SEASON-1",
        match_id=match_id,
        token=admin_token,
    )
    assert scan_res1.status_code == 200
    assert scan_res1.json()["result"] == "GRANTED"
    assert scan_res1.json()["ticketType"] == "SEASON_TICKET"

    # 2. Re-entry with same season ticket for same match -> 409 Conflict
    scan_res2 = api_client.validate_turnstile(
        barcode=barcode,
        turnstile_id="GATE-SEASON-2",
        match_id=match_id,
        token=admin_token,
    )
    assert scan_res2.status_code == 409


def test_05_tampered_token_bad_request(api_client: ApiClient, admin_token):
    fake_token = "TKT_12345678-1234-1234-1234-123456789abc_corruptedfakehmacsignature"
    scan_res = api_client.validate_turnstile(
        barcode=fake_token,
        turnstile_id="GATE-1",
        match_id=1,
        token=admin_token,
    )
    assert scan_res.status_code == 400


def test_06_wrong_match_id_bad_request(
    api_client: ApiClient, register_user, get_available_barcode, admin_token
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    buyer = register_user()

    entitlement = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]
    match_id = entitlement["matchId"]
    entitlement_id = entitlement["id"]

    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2000.0, token=seller["accessToken"]
    ).json()["id"]
    reservation_id = api_client.create_reservation(
        listing_id=listing_id, token=buyer["accessToken"]
    ).json()["id"]
    order = api_client.checkout(
        reservation_id=reservation_id,
        idempotency_key=random_idempotency_key(),
        token=buyer["accessToken"],
        card_number=CARD_VALID,
    ).json()

    # Scan with non-matching matchId (e.g. 99999)
    scan_res = api_client.validate_turnstile(
        barcode=order["ticketToken"],
        turnstile_id="GATE-1",
        match_id=99999,
        token=admin_token,
    )
    assert scan_res.status_code == 400


def test_07_regular_user_forbidden_from_turnstile(api_client: ApiClient, register_user):
    user = register_user()
    scan_res = api_client.validate_turnstile(
        barcode="ST-2025-001",
        turnstile_id="GATE-1",
        match_id=1,
        token=user["accessToken"],
    )
    assert scan_res.status_code == 403
