import time
import httpx


class MailpitClient:
    def __init__(self, base_url: str = "http://localhost:8025"):
        self.base_url = base_url.rstrip("/")
        self.client = httpx.Client(base_url=self.base_url, timeout=5.0)

    def delete_all(self) -> None:
        self.client.delete("/api/v1/messages")

    def get_messages(self) -> list[dict]:
        res = self.client.get("/api/v1/messages")
        if res.status_code == 200:
            return res.json().get("messages", [])
        return []

    def get_message(self, message_id: str) -> dict:
        res = self.client.get(f"/api/v1/message/{message_id}")
        res.raise_for_status()
        return res.json()

    def wait_for_email(
        self,
        to_email: str,
        subject_contains: str | None = None,
        timeout: float = 6.0,
        poll_interval: float = 0.3,
    ) -> dict | None:
        start_time = time.time()
        while time.time() - start_time < timeout:
            messages = self.get_messages()
            for msg in messages:
                recipients = [r.get("Address", "") for r in msg.get("To", [])]
                if to_email in recipients:
                    if subject_contains is None or subject_contains in msg.get(
                        "Subject", ""
                    ):
                        return self.get_message(msg["ID"])
            time.sleep(poll_interval)
        return None
