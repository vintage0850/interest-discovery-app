from __future__ import annotations

import base64
import hashlib
import hmac

from line.signature import verify_line_signature

SECRET = "test-channel-secret"
BODY = b'{"events":[]}'


def _sign(body: bytes, secret: str) -> str:
    digest = hmac.new(secret.encode("utf-8"), body, hashlib.sha256).digest()
    return base64.b64encode(digest).decode("utf-8")


def test_valid_signature_returns_true() -> None:
    signature = _sign(BODY, SECRET)
    assert verify_line_signature(BODY, signature, SECRET) is True


def test_invalid_signature_returns_false() -> None:
    assert verify_line_signature(BODY, "invalid-signature", SECRET) is False


def test_signature_for_different_body_returns_false() -> None:
    signature = _sign(BODY, SECRET)
    tampered_body = b'{"events":[{"type":"follow"}]}'
    assert verify_line_signature(tampered_body, signature, SECRET) is False


def test_empty_signature_returns_false() -> None:
    assert verify_line_signature(BODY, "", SECRET) is False
