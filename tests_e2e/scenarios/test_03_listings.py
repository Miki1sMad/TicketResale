import pytest
from helpers.api_client import ApiClient


def test_01_create_listing_and_verify_entitlement_status(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    token = seller["accessToken"]

    # 1. Get entitlements
    my_res = api_client.get_my_season_tickets(token)
    assert my_res.status_code == 200
    entitlements = my_res.json()[0]["entitlements"]
    entitlement_id = entitlements[0]["id"]

    # 2. Create listing
    create_res = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=3000.0, token=token
    )
    assert create_res.status_code == 201
    listing = create_res.json()
    assert listing["status"] == "ACTIVE"
    assert listing["price"] == 3000.0
    assert listing["matchEntitlementId"] == entitlement_id

    # 3. Verify entitlement is now LISTED
    my_res2 = api_client.get_my_season_tickets(token)
    updated_ent = next(
        e for e in my_res2.json()[0]["entitlements"] if e["id"] == entitlement_id
    )
    assert updated_ent["status"] == "LISTED"


def test_02_cancel_own_listing(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    token = seller["accessToken"]

    my_res = api_client.get_my_season_tickets(token)
    entitlement_id = my_res.json()[0]["entitlements"][0]["id"]

    create_res = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2500.0, token=token
    )
    assert create_res.status_code == 201
    listing_id = create_res.json()["id"]

    # Cancel listing
    cancel_res = api_client.cancel_listing(listing_id, token)
    assert cancel_res.status_code == 200
    assert cancel_res.json()["status"] == "CANCELLED"

    # Verify entitlement is back to OWNER_HELD
    my_res2 = api_client.get_my_season_tickets(token)
    updated_ent = next(
        e for e in my_res2.json()[0]["entitlements"] if e["id"] == entitlement_id
    )
    assert updated_ent["status"] == "OWNER_HELD"


def test_03_create_listing_for_other_users_entitlement(
    api_client: ApiClient, register_user, get_available_barcode
):
    # User 1 has entitlement
    barcode = get_available_barcode()
    user1 = register_user(season_ticket_barcode=barcode)
    entitlement_id = api_client.get_my_season_tickets(user1["accessToken"]).json()[0][
        "entitlements"
    ][0]["id"]

    # User 2 attempts to list User 1's entitlement -> 400 Bad Request
    user2 = register_user()
    res = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2000.0, token=user2["accessToken"]
    )
    assert res.status_code == 400


def test_04_double_listing_same_entitlement_conflict(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    token = seller["accessToken"]

    entitlement_id = api_client.get_my_season_tickets(token).json()[0]["entitlements"][
        0
    ]["id"]

    # First listing
    res1 = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=1500.0, token=token
    )
    assert res1.status_code == 201

    # Second listing attempt for same entitlement -> 409 Conflict
    res2 = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=1800.0, token=token
    )
    assert res2.status_code == 409


def test_05_cancel_other_users_listing(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    token_seller = seller["accessToken"]

    entitlement_id = api_client.get_my_season_tickets(token_seller).json()[0][
        "entitlements"
    ][0]["id"]
    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2500.0, token=token_seller
    ).json()["id"]

    # Other user attempts to cancel -> 400 Bad Request
    other_user = register_user()
    res = api_client.cancel_listing(listing_id, other_user["accessToken"])
    assert res.status_code == 400


def test_06_filter_and_search_active_listings(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    token = seller["accessToken"]

    entitlement = api_client.get_my_season_tickets(token).json()[0]["entitlements"][0]
    match_id = entitlement["matchId"]
    entitlement_id = entitlement["id"]

    api_client.create_listing(
        match_entitlement_id=entitlement_id, price=3200.0, token=token
    )

    # Search with matchId filter
    res = api_client.get_listings(match_id=match_id)
    assert res.status_code == 200
    listings = res.json()
    assert len(listings) >= 1
    for item in listings:
        assert item["matchId"] == match_id
        assert item["status"] == "ACTIVE"
