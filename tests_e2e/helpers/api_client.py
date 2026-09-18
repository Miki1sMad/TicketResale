import httpx


class ApiClient:
    def __init__(self, base_url: str = "http://localhost:8080"):
        self.base_url = base_url.rstrip("/")
        self.client = httpx.Client(base_url=self.base_url, timeout=15.0)

    @staticmethod
    def auth_headers(token: str) -> dict[str, str]:
        return {"Authorization": f"Bearer {token}"}

    # --- Auth ---
    def register(self, payload: dict) -> httpx.Response:
        return self.client.post("/api/v1/auth/register", json=payload)

    def login(self, email: str, password: str) -> httpx.Response:
        return self.client.post(
            "/api/v1/auth/login", json={"email": email, "password": password}
        )

    def refresh_token(self, refresh_token: str) -> httpx.Response:
        return self.client.post(
            "/api/v1/auth/refresh", json={"refreshToken": refresh_token}
        )

    # --- Clubs ---
    def get_clubs(self) -> httpx.Response:
        return self.client.get("/api/v1/clubs")

    def create_club(self, name: str, city: str, token: str) -> httpx.Response:
        return self.client.post(
            "/api/v1/clubs",
            json={"name": name, "city": city},
            headers=self.auth_headers(token),
        )

    # --- Stadiums ---
    def get_stadiums(self) -> httpx.Response:
        return self.client.get("/api/v1/stadiums")

    def create_stadium(
        self, club_id: int, name: str, city: str, capacity: int, token: str
    ) -> httpx.Response:
        return self.client.post(
            "/api/v1/stadiums",
            json={"clubId": club_id, "name": name, "city": city, "capacity": capacity},
            headers=self.auth_headers(token),
        )

    # --- Matches ---
    def get_matches(self) -> httpx.Response:
        return self.client.get("/api/v1/matches")

    def get_match(self, match_id: int) -> httpx.Response:
        return self.client.get(f"/api/v1/matches/{match_id}")

    def create_match(
        self,
        home_club_id: int,
        away_club_id: int,
        stadium_id: int,
        kickoff_time: str,
        season: str,
        token: str,
    ) -> httpx.Response:
        return self.client.post(
            "/api/v1/matches",
            json={
                "homeClubId": home_club_id,
                "awayClubId": away_club_id,
                "stadiumId": stadium_id,
                "kickoffTime": kickoff_time,
                "season": season,
            },
            headers=self.auth_headers(token),
        )

    # --- Season Tickets ---
    def claim_season_ticket(self, barcode: str, token: str) -> httpx.Response:
        return self.client.post(
            "/api/v1/season-tickets/claim",
            json={"barcode": barcode},
            headers=self.auth_headers(token),
        )

    def get_my_season_tickets(self, token: str) -> httpx.Response:
        return self.client.get(
            "/api/v1/season-tickets/my", headers=self.auth_headers(token)
        )

    def get_available_barcodes(self, token: str) -> httpx.Response:
        return self.client.get(
            "/api/v1/season-tickets/available-barcodes",
            headers=self.auth_headers(token),
        )

    # --- Listings ---
    def create_listing(
        self, match_entitlement_id: int, price: float, token: str
    ) -> httpx.Response:
        return self.client.post(
            "/api/v1/listings",
            json={"matchEntitlementId": match_entitlement_id, "price": price},
            headers=self.auth_headers(token),
        )

    def get_listings(self, match_id: int | None = None) -> httpx.Response:
        params = {"matchId": match_id} if match_id is not None else {}
        return self.client.get("/api/v1/listings", params=params)

    def get_listing(self, listing_id: int) -> httpx.Response:
        return self.client.get(f"/api/v1/listings/{listing_id}")

    def cancel_listing(self, listing_id: int, token: str) -> httpx.Response:
        return self.client.delete(
            f"/api/v1/listings/{listing_id}", headers=self.auth_headers(token)
        )

    # --- Reservations ---
    def create_reservation(self, listing_id: int, token: str) -> httpx.Response:
        return self.client.post(
            "/api/v1/reservations",
            json={"listingId": listing_id},
            headers=self.auth_headers(token),
        )

    def get_reservation(self, reservation_id: int, token: str) -> httpx.Response:
        return self.client.get(
            f"/api/v1/reservations/{reservation_id}", headers=self.auth_headers(token)
        )

    def get_my_reservations(self, token: str) -> httpx.Response:
        return self.client.get(
            "/api/v1/reservations/my", headers=self.auth_headers(token)
        )

    # --- Orders / Checkout ---
    def checkout(
        self,
        reservation_id: int,
        idempotency_key: str,
        token: str,
        payment_method: str = "CARD",
        card_number: str = "4111222233334444",
    ) -> httpx.Response:
        headers = self.auth_headers(token)
        if idempotency_key:
            headers["Idempotency-Key"] = idempotency_key
        return self.client.post(
            "/api/v1/orders/checkout",
            json={
                "reservationId": reservation_id,
                "paymentMethod": payment_method,
                "cardNumber": card_number,
            },
            headers=headers,
        )

    def get_order(self, order_id: int, token: str) -> httpx.Response:
        return self.client.get(
            f"/api/v1/orders/{order_id}", headers=self.auth_headers(token)
        )

    def get_my_orders(self, token: str) -> httpx.Response:
        return self.client.get("/api/v1/orders/my", headers=self.auth_headers(token))

    def get_my_tickets(self, token: str) -> httpx.Response:
        return self.client.get(
            "/api/v1/orders/my-tickets", headers=self.auth_headers(token)
        )

    # --- Turnstile ---
    def validate_turnstile(
        self,
        barcode: str,
        turnstile_id: str,
        match_id: int,
        token: str,
    ) -> httpx.Response:
        return self.client.post(
            "/api/v1/turnstile/validate",
            json={"barcode": barcode, "turnstileId": turnstile_id, "matchId": match_id},
            headers=self.auth_headers(token),
        )
