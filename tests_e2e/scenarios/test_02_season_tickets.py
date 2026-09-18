import pytest
from helpers.api_client import ApiClient


def test_01_claim_season_ticket_and_conflict(
    api_client: ApiClient, register_user, get_available_barcode
):
    user_data = register_user()
    user_token = user_data["accessToken"]
    barcode = get_available_barcode()

    # 1. Claim season ticket
    claim_res = api_client.claim_season_ticket(barcode, user_token)
    assert claim_res.status_code == 200
    ticket_data = claim_res.json()
    assert ticket_data["barcode"] == barcode
    assert ticket_data["status"] == "ACTIVE"
    assert len(ticket_data["entitlements"]) >= 1
    for ent in ticket_data["entitlements"]:
        assert ent["status"] == "OWNER_HELD"

    # 2. Duplicate claim attempt -> 409 Conflict
    user2 = register_user()
    dup_res = api_client.claim_season_ticket(barcode, user2["accessToken"])
    assert dup_res.status_code == 409


def test_02_view_my_season_tickets(
    api_client: ApiClient, register_user, get_available_barcode
):
    barcode = get_available_barcode()
    user_data = register_user(season_ticket_barcode=barcode)
    token = user_data["accessToken"]

    my_res = api_client.get_my_season_tickets(token)
    assert my_res.status_code == 200
    tickets = my_res.json()
    assert len(tickets) >= 1
    target = next(t for t in tickets if t["barcode"] == barcode)
    assert target["sectionName"] is not None
    assert target["rowNumber"] is not None
    assert target["seatNumber"] is not None
    assert len(target["entitlements"]) > 0


def test_03_protect_available_barcodes_endpoint(
    api_client: ApiClient, register_user, admin_token
):
    user_data = register_user()
    user_token = user_data["accessToken"]

    # 1. USER tries to get available barcodes -> 403 Forbidden
    res_user = api_client.get_available_barcodes(user_token)
    assert res_user.status_code == 403

    # 2. ADMIN gets available barcodes -> 200 OK
    res_admin = api_client.get_available_barcodes(admin_token)
    assert res_admin.status_code == 200
    assert isinstance(res_admin.json(), list)
