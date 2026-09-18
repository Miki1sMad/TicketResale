import uuid
from faker import Faker

fake = Faker()


def random_email() -> str:
    return f"user_{uuid.uuid4().hex[:10]}@example.com"


def random_password() -> str:
    return "Password123!"


def random_user_payload(season_ticket_barcode: str | None = None) -> dict:
    payload = {
        "email": random_email(),
        "password": random_password(),
        "firstName": fake.first_name(),
        "lastName": fake.last_name(),
    }
    if season_ticket_barcode:
        payload["seasonTicketBarcode"] = season_ticket_barcode
    return payload


def random_idempotency_key() -> str:
    return str(uuid.uuid4())


CARD_VALID = "4111222233334444"
CARD_DECLINED = "4111222233330000"
