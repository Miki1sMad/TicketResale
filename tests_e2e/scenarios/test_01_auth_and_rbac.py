import pytest
from helpers.api_client import ApiClient
from helpers.data_factory import random_user_payload


def test_01_successful_registration_and_login(api_client: ApiClient):
    payload = random_user_payload()

    # 1. Register
    reg_res = api_client.register(payload)
    assert reg_res.status_code == 201
    reg_data = reg_res.json()
    assert "accessToken" in reg_data
    assert "refreshToken" in reg_data
    assert reg_data["tokenType"] == "Bearer"
    assert reg_data["expiresIn"] == 3600

    # 2. Login
    login_res = api_client.login(payload["email"], payload["password"])
    assert login_res.status_code == 200
    login_data = login_res.json()
    assert "accessToken" in login_data
    assert "refreshToken" in login_data


def test_02_registration_with_season_ticket_auto_claim(
    api_client: ApiClient, get_available_barcode
):
    barcode = get_available_barcode()
    payload = random_user_payload(season_ticket_barcode=barcode)

    # Register with season ticket barcode
    reg_res = api_client.register(payload)
    assert reg_res.status_code == 201
    token = reg_res.json()["accessToken"]

    # Verify season ticket claimed
    my_res = api_client.get_my_season_tickets(token)
    assert my_res.status_code == 200
    my_tickets = my_res.json()
    assert len(my_tickets) >= 1
    ticket = next(t for t in my_tickets if t["barcode"] == barcode)
    assert ticket["status"] == "ACTIVE"
    for ent in ticket["entitlements"]:
        assert ent["status"] == "OWNER_HELD"


def test_03_registration_duplicate_email_conflict(api_client: ApiClient):
    payload = random_user_payload()
    res1 = api_client.register(payload)
    assert res1.status_code == 201

    # Attempt second registration with same email -> 400 Bad Request or 409 Conflict
    res2 = api_client.register(payload)
    assert res2.status_code in (400, 409)
    assert "already exists" in res2.text.lower() or res2.status_code == 409


def test_04_registration_with_already_used_season_barcode(
    api_client: ApiClient, get_available_barcode
):
    barcode = get_available_barcode()

    # User 1 registers with barcode
    payload1 = random_user_payload(season_ticket_barcode=barcode)
    res1 = api_client.register(payload1)
    assert res1.status_code == 201

    # User 2 attempts registration with same barcode
    payload2 = random_user_payload(season_ticket_barcode=barcode)
    res2 = api_client.register(payload2)
    assert res2.status_code == 409


def test_05_registration_with_non_existent_season_barcode(api_client: ApiClient):
    payload = random_user_payload(season_ticket_barcode="NON-EXISTENT-999")
    res = api_client.register(payload)
    assert res.status_code == 400


def test_06_refresh_token_rotation_and_invalid_refresh(
    api_client: ApiClient, register_user
):
    user_data = register_user()
    refresh_token = user_data["refreshToken"]

    # 1. Valid refresh
    refresh_res = api_client.refresh_token(refresh_token)
    assert refresh_res.status_code == 200
    refreshed_data = refresh_res.json()
    assert "accessToken" in refreshed_data
    assert "refreshToken" in refreshed_data

    # 2. Invalid refresh token
    bad_res = api_client.refresh_token("corrupted.jwt.token.signature")
    assert bad_res.status_code in (400, 401, 403)


def test_07_rbac_authorization(api_client: ApiClient, register_user):
    user_data = register_user()
    user_token = user_data["accessToken"]

    # 1. USER tries admin endpoint POST /api/v1/clubs -> 403 Forbidden
    res_forbidden = api_client.create_club(
        name="Unauthorized Club", city="Belgrade", token=user_token
    )
    assert res_forbidden.status_code == 403

    # 2. Unauthenticated request -> 401 Unauthorized or 403 Forbidden
    res_unauth = api_client.client.post(
        "/api/v1/clubs", json={"name": "No Auth Club", "city": "Novi Sad"}
    )
    assert res_unauth.status_code in (401, 403)
