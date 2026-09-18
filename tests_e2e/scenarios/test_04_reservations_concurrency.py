import asyncio
import httpx
import pytest
from helpers.api_client import ApiClient
from helpers.data_factory import random_user_payload


def test_01_standard_reservation_happy_path(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    buyer = register_user()

    entitlement_id = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]["id"]
    listing_res = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2000.0, token=seller["accessToken"]
    )
    listing_id = listing_res.json()["id"]

    # Buyer reserves listing
    res_res = api_client.create_reservation(
        listing_id=listing_id, token=buyer["accessToken"]
    )
    assert res_res.status_code == 201
    reservation = res_res.json()
    assert reservation["listingId"] == listing_id
    assert reservation["status"] == "PENDING"
    assert "expiresAt" in reservation

    # Verify listing is now RESERVED
    listing_check = api_client.get_listing(listing_id)
    assert listing_check.json()["status"] == "RESERVED"


def test_02_seller_cannot_reserve_own_listing(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    token = seller["accessToken"]

    entitlement_id = api_client.get_my_season_tickets(token).json()[0]["entitlements"][
        0
    ]["id"]
    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=1800.0, token=token
    ).json()["id"]

    # Seller attempts to reserve own listing -> 400 Bad Request
    res = api_client.create_reservation(listing_id=listing_id, token=token)
    assert res.status_code == 400


def test_03_reserve_already_reserved_listing_conflict(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    buyer1 = register_user()
    buyer2 = register_user()

    entitlement_id = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]["id"]
    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2200.0, token=seller["accessToken"]
    ).json()["id"]

    # Buyer 1 reserves
    res1 = api_client.create_reservation(
        listing_id=listing_id, token=buyer1["accessToken"]
    )
    assert res1.status_code == 201

    # Buyer 2 tries to reserve same listing -> 409 Conflict
    res2 = api_client.create_reservation(
        listing_id=listing_id, token=buyer2["accessToken"]
    )
    assert res2.status_code == 409


@pytest.mark.concurrency
async def test_04_high_concurrency_race_condition_simulation(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)

    entitlement_id = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]["id"]
    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=3500.0, token=seller["accessToken"]
    ).json()["id"]

    # Register 50 distinct buyers
    num_buyers = 50
    buyers = [register_user() for _ in range(num_buyers)]
    tokens = [b["accessToken"] for b in buyers]

    # Concurrency test with 50 parallel requests
    limits = httpx.Limits(max_connections=200, max_keepalive_connections=100)
    timeout = httpx.Timeout(30.0)

    async with httpx.AsyncClient(
        base_url="http://localhost:8080", limits=limits, timeout=timeout
    ) as client:
        tasks = [
            client.post(
                "/api/v1/reservations",
                json={"listingId": listing_id},
                headers={"Authorization": f"Bearer {token}"},
            )
            for token in tokens
        ]
        responses = await asyncio.gather(*tasks)

    status_codes = [r.status_code for r in responses]
    success_count = status_codes.count(201)
    conflict_count = status_codes.count(409)

    assert success_count == 1, f"Expected exactly 1 success (201), got {success_count}"
    assert conflict_count == num_buyers - 1, (
        f"Expected {num_buyers - 1} conflicts (409), got {conflict_count}"
    )

    # Verify listing status is RESERVED
    listing_check = api_client.get_listing(listing_id)
    assert listing_check.json()["status"] == "RESERVED"
