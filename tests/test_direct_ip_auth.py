"""Offline tests for the read-only direct-IP auth preflight."""
import io
import unittest
from urllib.error import HTTPError
from scripts.check_direct_ip_auth import (
    PreflightError, validate_origin, verify_routes
)


class FakeResponse:
    def __init__(self, code, headers=None, content=b""):
        self.code = code
        self.headers = headers or {}
        self.content = content

    def getcode(self):
        return self.code

    def read(self, count):
        return self.content[:count]

    def __enter__(self):
        return self

    def __exit__(self, *unused):
        return False


class FakeOpener:
    def __init__(self, auth_code=401, chat_code=200, redirect=False):
        self.auth_code = auth_code
        self.chat_code = chat_code
        self.redirect = redirect
        self.requests = []

    def open(self, req, timeout):
        self.requests.append((req.get_method(), req.full_url))
        if self.redirect:
            raise HTTPError(req.full_url, 302, "Redirect",
                            {"Location": "https://morkovkionline.ru/chat"},
                            io.BytesIO(b""))
        if req.full_url.endswith("/chat"):
            return FakeResponse(self.chat_code, {"Content-Type": "text/html; charset=utf-8"},
                                b"<!doctype html><html><body></body></html>")
        raise HTTPError(req.full_url, self.auth_code, "Anonymous",
                        {"Content-Type": "application/json"},
                        io.BytesIO(b"{}"))


class PreflightTests(unittest.TestCase):
    def test_secure_public_ip(self):
        self.assertEqual(validate_origin("https://212.220.99.132/"),
                         "https://212.220.99.132/")

    def test_reject_unsafe_origins(self):
        bad = (
            "http://212.220.99.132:5761/",
            "https://morkovkionline.ru/",
            "https://127.0.0.1/",
            "https://192.168.1.1/",
            "https://212.220.99.132/chat",
            "https://212.220.99.132/?x=1",
            "https://212.220.99.132:80/",
            "https://user:pass@212.220.99.132/",
        )
        for origin in bad:
            with self.subTest(origin=origin), self.assertRaises(PreflightError):
                validate_origin(origin)

    def test_anonymous_auth_required(self):
        opener = FakeOpener()
        self.assertTrue(verify_routes("https://212.220.99.132/", opener))
        self.assertEqual([method for method, _ in opener.requests], ["GET", "GET"])
        self.assertTrue(all("212.220.99.132" in url for _, url in opener.requests))

    def test_auth_must_not_return_success(self):
        with self.assertRaises(PreflightError):
            verify_routes("https://212.220.99.132/", FakeOpener(auth_code=200))

    def test_auth_must_exist(self):
        with self.assertRaises(PreflightError):
            verify_routes("https://212.220.99.132/", FakeOpener(auth_code=404))

    def test_redirect_forbidden(self):
        with self.assertRaises(PreflightError):
            verify_routes("https://212.220.99.132/", FakeOpener(redirect=True))


if __name__ == "__main__":
    unittest.main()
