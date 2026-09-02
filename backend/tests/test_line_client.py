from __future__ import annotations

import json

import httpx
import pytest

from line.line_client import LineApiException, LineClient


def test_push_message_sends_correct_request() -> None:
    captured: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["auth"] = request.headers.get("authorization")
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={})

    client = LineClient(
        channel_access_token="test-token",
        http_client=httpx.Client(
            base_url="https://api.line.me", transport=httpx.MockTransport(handler)
        ),
    )

    client.push_message("U123", "リマインダー：勉強する")

    assert captured["url"] == "https://api.line.me/v2/bot/message/push"
    assert captured["auth"] == "Bearer test-token"
    assert captured["body"] == {
        "to": "U123",
        "messages": [{"type": "text", "text": "リマインダー：勉強する"}],
    }


def test_push_message_raises_on_non_200() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(400, json={"message": "invalid"})

    client = LineClient(
        channel_access_token="test-token",
        http_client=httpx.Client(
            base_url="https://api.line.me", transport=httpx.MockTransport(handler)
        ),
    )

    with pytest.raises(LineApiException):
        client.push_message("U123", "テスト")
