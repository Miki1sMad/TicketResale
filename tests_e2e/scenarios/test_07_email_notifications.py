import pytest
from helpers.api_client import ApiClient
from helpers.data_factory import CARD_VALID, random_idempotency_key, random_user_payload
from helpers.mailpit_client import MailpitClient


def test_01_registration_welcome_email(api_client: ApiClient, mailpit: MailpitClient):
    payload = random_user_payload()
    res = api_client.register(payload)
    assert res.status_code == 201

    email = mailpit.wait_for_email(
        to_email=payload["email"],
        subject_contains="Dobrodošli na TicketResale platformu!",
        timeout=6.0,
    )
    assert email is not None, f"Welcome email not received for {payload['email']}"
    assert "Dobrodošli" in email["Subject"]


def test_02_listing_confirmation_email(
    api_client: ApiClient,
    register_user,
    get_available_barcode,
    mailpit: MailpitClient,
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    seller_email = seller["user"]["email"]

    entitlement_id = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]["id"]
    listing_res = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=2800.0, token=seller["accessToken"]
    )
    assert listing_res.status_code == 201

    email = mailpit.wait_for_email(
        to_email=seller_email,
        subject_contains="Potvrda: Vaša karta je oglašena na berzi",
        timeout=6.0,
    )
    assert email is not None, f"Listing email not received for seller {seller_email}"
    assert "2800" in email.get("Text", "") or "2800" in email.get("Snippet", "")


def test_03_order_completed_and_sale_notifications(
    api_client: ApiClient,
    register_user,
    get_available_barcode,
    mailpit: MailpitClient,
):
    barcode = get_available_barcode()
    seller = register_user(season_ticket_barcode=barcode)
    seller_email = seller["user"]["email"]

    buyer = register_user()
    buyer_email = buyer["user"]["email"]

    entitlement_id = api_client.get_my_season_tickets(seller["accessToken"]).json()[0][
        "entitlements"
    ][0]["id"]
    listing_id = api_client.create_listing(
        match_entitlement_id=entitlement_id, price=3100.0, token=seller["accessToken"]
    ).json()["id"]

    reservation_id = api_client.create_reservation(
        listing_id=listing_id, token=buyer["accessToken"]
    ).json()["id"]

    checkout_res = api_client.checkout(
        reservation_id=reservation_id,
        idempotency_key=random_idempotency_key(),
        token=buyer["accessToken"],
        card_number=CARD_VALID,
    )
    assert checkout_res.status_code == 201

    # 1. Verify seller email (ticket sold)
    seller_mail = mailpit.wait_for_email(
        to_email=seller_email,
        subject_contains="Vaša karta je uspešno prodata!",
        timeout=6.0,
    )
    assert seller_mail is not None, "Seller sale notification email not received"

    # 2. Verify buyer email (order confirmation)
    buyer_mail = mailpit.wait_for_email(
        to_email=buyer_email,
        subject_contains="Potvrda kupovine",
        timeout=6.0,
    )
    assert buyer_mail is not None, "Buyer order confirmation email not received"
