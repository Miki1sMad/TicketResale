import pytest
from helpers.api_client import ApiClient
from helpers.data_factory import CARD_DECLINED, CARD_VALID, random_idempotency_key


def test_01_checkout_and_ticket_issuance_happy_path(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    buyer = register_user()

    # 1. Listing & Reservation
    entitlement_id = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]["id"]
    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=3000.0, token=seller["accessToken"]
    ).json()["id"]
    reservation_id = api_client.create_reservation(
        listing_id=listing_id, token=buyer["accessToken"]
    ).json()["id"]

    # 2. Checkout
    idempotency_key = random_idempotency_key()
    checkout_res = api_client.checkout(
        reservation_id=reservation_id,
        idempotency_key=idempotency_key,
        token=buyer["accessToken"],
        card_number=CARD_VALID,
    )
    assert checkout_res.status_code == 201
    order = checkout_res.json()
    assert order["status"] == "COMPLETED"
    assert order["ticketToken"].startswith("TKT_")
    assert len(order["tickets"]) == 1
    assert order["tickets"][0]["status"] == "VALID"

    # 3. Verify domain states
    # Listing -> SOLD
    listing_check = api_client.get_listing(listing_id)
    assert listing_check.json()["status"] == "SOLD"

    # Seller entitlement -> RESOLD
    seller_tickets = api_client.get_my_season_tickets(seller["accessToken"]).json()
    seller_ent = next(
        e for e in seller_tickets[0]["entitlements"] if e["id"] == entitlement_id
    )
    assert seller_ent["status"] == "RESOLD"

    # Reservation -> COMPLETED
    res_check = api_client.get_reservation(reservation_id, buyer["accessToken"])
    assert res_check.json()["status"] == "COMPLETED"


def test_02_idempotency_key_duplicate_request(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    buyer = register_user()

    entitlement_id = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]["id"]
    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2800.0, token=seller["accessToken"]
    ).json()["id"]
    reservation_id = api_client.create_reservation(
        listing_id=listing_id, token=buyer["accessToken"]
    ).json()["id"]

    idempotency_key = random_idempotency_key()

    # First checkout
    res1 = api_client.checkout(
        reservation_id=reservation_id,
        idempotency_key=idempotency_key,
        token=buyer["accessToken"],
        card_number=CARD_VALID,
    )
    assert res1.status_code == 201
    order1 = res1.json()

    # Duplicate checkout with same Idempotency-Key
    res2 = api_client.checkout(
        reservation_id=reservation_id,
        idempotency_key=idempotency_key,
        token=buyer["accessToken"],
        card_number=CARD_VALID,
    )
    assert res2.status_code in (200, 201)
    order2 = res2.json()
    assert order2["id"] == order1["id"]
    assert order2["status"] == "COMPLETED"


def test_03_payment_declined_flow(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    buyer = register_user()

    entitlement_id = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]["id"]
    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2000.0, token=seller["accessToken"]
    ).json()["id"]
    reservation_id = api_client.create_reservation(
        listing_id=listing_id, token=buyer["accessToken"]
    ).json()["id"]

    # Checkout with declined card
    idempotency_key = random_idempotency_key()
    res = api_client.checkout(
        reservation_id=reservation_id,
        idempotency_key=idempotency_key,
        token=buyer["accessToken"],
        card_number=CARD_DECLINED,
    )
    assert res.status_code == 400

    # Reservation must remain PENDING
    res_check = api_client.get_reservation(reservation_id, buyer["accessToken"])
    assert res_check.json()["status"] == "PENDING"


def test_04_checkout_without_idempotency_key(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    buyer = register_user()

    entitlement_id = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]["id"]
    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2400.0, token=seller["accessToken"]
    ).json()["id"]
    reservation_id = api_client.create_reservation(
        listing_id=listing_id, token=buyer["accessToken"]
    ).json()["id"]

    # Checkout without Idempotency-Key
    res = api_client.client.post(
        "/api/v1/orders/checkout",
        json={
            "reservationId": reservation_id,
            "paymentMethod": "CARD",
            "cardNumber": CARD_VALID,
        },
        headers=ApiClient.auth_headers(buyer["accessToken"]),
    )
    assert res.status_code == 400


def test_05_checkout_other_users_reservation_denied(
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
        match_entitlement_id=entitlement_id, price=2500.0, token=seller["accessToken"]
    ).json()["id"]
    reservation_id = api_client.create_reservation(
        listing_id=listing_id, token=buyer1["accessToken"]
    ).json()["id"]

    # Buyer 2 tries to checkout Buyer 1's reservation
    idempotency_key = random_idempotency_key()
    res = api_client.checkout(
        reservation_id=reservation_id,
        idempotency_key=idempotency_key,
        token=buyer2["accessToken"],
        card_number=CARD_VALID,
    )
    assert res.status_code == 400


def test_06_view_my_tickets(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    buyer = register_user()

    entitlement_id = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]["id"]
    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2900.0, token=seller["accessToken"]
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

    # View my tickets
    res = api_client.get_my_tickets(buyer["accessToken"])
    assert res.status_code == 200
    tickets = res.json()
    assert len(tickets) >= 1
    assert tickets[0]["status"] == "VALID"
    assert "matchId" in tickets[0]
