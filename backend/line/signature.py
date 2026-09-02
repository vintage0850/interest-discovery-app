from __future__ import annotations

import base64
import hashlib
import hmac


def verify_line_signature(body: bytes, signature: str, channel_secret: str) -> bool:
    """x-line-signature ヘッダーを検証する。生バイト列に対してのみ計算すること。"""
    if not signature:
        return False
    digest = hmac.new(channel_secret.encode("utf-8"), body, hashlib.sha256).digest()
    expected = base64.b64encode(digest).decode("utf-8")
    return hmac.compare_digest(expected, signature)
