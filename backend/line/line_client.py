from __future__ import annotations

import httpx


class LineApiException(Exception):
    """LINE Messaging API呼び出し時のエラー。"""


class LineClient:
    """LINE Messaging APIをhttpxで呼び出すクライアント。"""

    def __init__(self, channel_access_token: str, http_client: httpx.Client | None = None) -> None:
        self._token = channel_access_token
        self._client = http_client or httpx.Client(base_url="https://api.line.me")

    def push_message(self, line_user_id: str, message: str) -> None:
        response = self._client.post(
            "/v2/bot/message/push",
            headers={"Authorization": f"Bearer {self._token}"},
            json={"to": line_user_id, "messages": [{"type": "text", "text": message}]},
        )
        if response.status_code != 200:
            raise LineApiException(
                f"LINE push failed (HTTP {response.status_code}): {response.text}"
            )
