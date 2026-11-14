#!/usr/bin/env python3
"""Read-only preflight for shared Morkovki web auth over direct HTTPS IPv4.

Never submit credentials, create a user, or touch the database.
Does not prove that an existing nickname/password works: that requires a
real device + an authorized test account against the SAME backend/database.
"""
import argparse
import ipaddress
import ssl
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import (
    HTTPRedirectHandler, HTTPSHandler, ProxyHandler, Request, build_opener
)


class PreflightError(Exception):
    pass


class NeverRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def validate_origin(raw):
    try:
        parts = urlsplit(raw)
        addr = ipaddress.IPv4Address(parts.hostname or "")
        port = parts.port
    except (ValueError, ipaddress.AddressValueError) as exc:
        raise PreflightError(f"Invalid IPv4 origin: {exc}") from exc
    if not (parts.scheme == "https" and addr.is_global
            and not parts.username and not parts.password
            and not parts.query and not parts.fragment
            and parts.path in ("", "/")
            and port != 80):
        raise PreflightError(
            "Only publicly routable HTTPS IPv4 origin is allowed; "
            "example https://212.220.99.132/")
    return raw.rstrip("/") + "/"


def get_no_redirect(opener, url, accept):
    req = Request(url, method="GET", headers={
        "User-Agent": "Morkovki-Direct-IP-Auth-Preflight/1.0",
        "Accept": accept,
        "Cache-Control": "no-cache",
    })
    try:
        with opener.open(req, timeout=20) as response:
            code = response.getcode()
            headers = response.headers
            content = response.read(4096)
    except HTTPError as exc:
        # urllib treats non-2xx (including intended /auth/me 401) as errors.
        code, headers, content = exc.code, exc.headers, b""
        exc.close()
    except (OSError, URLError) as exc:
        raise PreflightError(f"Cannot reach direct IP with valid HTTPS/TLS: {exc}") from exc
    if 300 <= code < 400 or headers.get("Location"):
        raise PreflightError(f"{url} redirects (HTTP {code}, Location={headers.get('Location')!r})")
    return code, headers, content


def verify_routes(origin, opener):
    chat_code, chat_headers, chat_content = get_no_redirect(
        opener, origin + "chat", "text/html")
    if chat_code != 200:
        raise PreflightError(f"GET /chat must return 200; got HTTP {chat_code}")
    mime = chat_headers.get("Content-Type", "").lower()
    if "text/html" not in mime:
        raise PreflightError(f"GET /chat must return HTML, got {mime!r}")
    if b"<html" not in chat_content.lower() and b"<!doctype" not in chat_content.lower():
        raise PreflightError("GET /chat did not look like an HTML application")
    auth_code, _, _ = get_no_redirect(
        opener, origin + "api/auth/me", "application/json")
    if auth_code != 401:
        raise PreflightError(
            f"GET /api/auth/me without cookies must return 401, got HTTP {auth_code}")
    return True


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--origin", required=True, help="https://public.IPv4/")
    args = parser.parse_args(argv)
    try:
        origin = validate_origin(args.origin)
        # No proxies: connect by IP, no external DNS/domain fallback.
        opener = build_opener(
            ProxyHandler({}),
            HTTPSHandler(context=ssl.create_default_context()),
            NeverRedirect(),
        )
        verify_routes(origin, opener)
    except PreflightError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    print(f"PASS: {origin}chat serves HTML with trusted IP TLS and no redirects.")
    print("PASS: /api/auth/me returns 401 without a cookie (shared auth route exists).")
    print("NOT VERIFIED: nickname/password registration, account database identity,")
    print("session cookie persistence, CSRF, or WebSocket: test with an authorized")
    print("account on the Android device after preparing the server.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
