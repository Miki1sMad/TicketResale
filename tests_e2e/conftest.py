import pytest
from helpers.api_client import ApiClient
from helpers.data_factory import random_email, random_password, random_user_payload
from helpers.mailpit_client import MailpitClient


@pytest.fixture(scope="session")
def base_url() -> str:
    return "http://localhost:8080"


@pytest.fixture(scope="session")
def mailpit_url() -> str:
    return "http://localhost:8025"


@pytest.fixture(scope="session")
def api_client(base_url: str) -> ApiClient:
    return ApiClient(base_url=base_url)


@pytest.fixture(scope="session")
def mailpit(mailpit_url: str) -> MailpitClient:
    return MailpitClient(base_url=mailpit_url)


@pytest.fixture(scope="session")
def admin_credentials() -> tuple[str, str]:
    return "admin@ticketresale.com", "Admin123!Safe"


@pytest.fixture(scope="session")
def admin_token(api_client: ApiClient, admin_credentials: tuple[str, str]) -> str:
    email, password = admin_credentials
    res = api_client.login(email, password)
    assert res.status_code == 200, f"Admin login failed: {res.text}"
    return res.json()["accessToken"]


@pytest.fixture(scope="session")
def admin_auth_headers(admin_token: str) -> dict[str, str]:
    return ApiClient.auth_headers(admin_token)


@pytest.fixture
def get_available_barcode(api_client: ApiClient, admin_token: str):
    def _helper() -> str:
        res = api_client.get_available_barcodes(admin_token)
        assert res.status_code == 200, f"Failed to get available barcodes: {res.text}"
        barcodes = res.json()
        assert len(barcodes) > 0, "No available barcodes found in system"
        return barcodes[0]

    return _helper


@pytest.fixture
def register_user(api_client: ApiClient):
    def _helper(season_ticket_barcode: str | None = None) -> dict:
        payload = random_user_payload(season_ticket_barcode=season_ticket_barcode)
        res = api_client.register(payload)
        assert res.status_code == 201, f"User registration failed: {res.text}"
        tokens = res.json()
        return {
            "user": payload,
            "accessToken": tokens["accessToken"],
            "refreshToken": tokens["refreshToken"],
            "auth_headers": ApiClient.auth_headers(tokens["accessToken"]),
        }

    return _helper


@pytest.fixture
def match_factory(api_client: ApiClient, admin_token: str):
    def _create_match(
        home_club_id: int = 1,
        away_club_id: int = 2,
        stadium_id: int = 1,
        kickoff_time: str = "2026-12-01T18:00:00Z",
        season: str = "2025/2026",
    ) -> dict:
        res = api_client.create_match(
            home_club_id=home_club_id,
            away_club_id=away_club_id,
            stadium_id=stadium_id,
            kickoff_time=kickoff_time,
            season=season,
            token=admin_token,
        )
        assert res.status_code == 201, f"Failed to create match: {res.text}"
        return res.json()

    return _create_match
