#!/usr/bin/env python3
"""Exercise an isolated demo deployment, then check it after container recreation.

Uses only the Python standard library. Creates a ticket and comments; never deletes
existing records. Use a separate Compose project/volume for this test.
"""

import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import threading
import time
import urllib.error
import urllib.request


class DemoApi:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")

    def request(self, method, path, user="user", data=None, expected=200):
        headers = {}
        if user is not None:
            credentials = f"{user}@ticketdesk.local:demo-password".encode()
            headers["Authorization"] = "Basic " + base64.b64encode(credentials).decode()
        if data is not None:
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            self.base_url + path, method=method, headers=headers,
            data=json.dumps(data).encode() if data is not None else None,
        )
        try:
            response = urllib.request.urlopen(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            status = response.status
            body = json.load(response)
            if expected is not None and status != expected:
                raise AssertionError(f"{method} {path}: expected {expected}, got {status}: {body}")
            return status, body, response.headers

    def wait_ready(self):
        deadline = time.monotonic() + 60
        while True:
            try:
                self.request("GET", "/api/tickets")
                return
            except (OSError, ValueError, AssertionError):
                if time.monotonic() >= deadline:
                    raise
                time.sleep(0.25)

    def report(self):
        _, body, _ = self.request("GET", "/api/reports/tickets-by-status", user="agent")
        assert [row["status"] for row in body] == ["OPEN", "IN_PROGRESS", "RESOLVED"], body
        return {row["status"]: row["count"] for row in body}


def seed(api):
    api.request("GET", "/api/tickets", user=None, expected=401)
    api.request("GET", "/api/reports/tickets-by-status", expected=403)
    api.request("POST", "/api/tickets", data={"title": " ", "description": "Invalid", "priority": "HIGH"}, expected=400)
    baseline = api.report()
    _, ticket, headers = api.request("POST", "/api/tickets", data={
        "title": "Compose smoke: Wi-Fi", "description": "Must survive container recreation", "priority": "HIGH",
    }, expected=201)
    path = f'/api/tickets/{ticket["id"]}'
    assert headers["Location"] == path, headers
    assert ticket["status"] == "OPEN", ticket
    api.request("GET", path, user="other", expected=404)
    _, listing, _ = api.request("GET", "/api/tickets?status=OPEN&priority=HIGH&size=1&sort=id,desc")
    assert listing["content"][0]["id"] == ticket["id"], listing
    api.request("PATCH", path + "/status", data={"status": "IN_PROGRESS"}, expected=403)

    barrier = threading.Barrier(2)

    def advance():
        barrier.wait(timeout=10)
        return api.request("PATCH", path + "/status", user="agent", data={"status": "IN_PROGRESS"}, expected=None)[0]

    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(advance), pool.submit(advance)]
        statuses = sorted(future.result(timeout=20) for future in futures)
    assert statuses == [200, 409], statuses
    api.request("PATCH", path + "/status", user="agent", data={"status": "OPEN"}, expected=409)

    comments = []
    for user, body in [("user", "Sorun devam ediyor."), ("agent", "Bağlantıyı kontrol ediyorum.")]:
        _, comment, _ = api.request("POST", path + "/comments", user=user, data={"body": body}, expected=201)
        comments.append(comment)
    assert comments[0]["authorId"] == ticket["creatorId"], comments
    assert comments[0]["authorId"] != comments[1]["authorId"], comments
    api.request("GET", path + "/comments", user="other", expected=404)
    api.request("POST", path + "/comments", user="other", data={"body": "Not allowed"}, expected=404)
    api.request("POST", path + "/comments", data={"body": " "}, expected=400)
    expected_report = dict(baseline)
    expected_report["IN_PROGRESS"] += 1
    state = {"ticket": ticket, "comments": comments, "report": expected_report}
    check(api, state)
    return state


def check(api, state):
    path = f'/api/tickets/{state["ticket"]["id"]}'
    _, ticket, _ = api.request("GET", path)
    assert ticket["status"] == "IN_PROGRESS", ticket
    for field in ("id", "creatorId", "title", "description", "createdAt"):
        assert ticket[field] == state["ticket"][field], (field, ticket)
    _, history, _ = api.request("GET", path + "/history")
    assert history["totalElements"] == 1, history
    assert history["content"][0]["fromStatus"] == "OPEN", history
    assert history["content"][0]["toStatus"] == "IN_PROGRESS", history
    for page, expected in enumerate(state["comments"]):
        _, comments, _ = api.request("GET", path + f"/comments?size=1&page={page}")
        assert comments["totalElements"] == 2, comments
        assert comments["content"] == [expected], comments
    assert api.report() == state["report"], "Report changed or demo seed duplicated tickets"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=["seed", "check"])
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--state", type=Path, required=True, help="State file outside the repository")
    args = parser.parse_args()
    api = DemoApi(args.base_url)
    api.wait_ready()
    if args.phase == "seed":
        state = seed(api)
        args.state.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")
        print("PASS: create, ownership, roles, concurrent 200/409, one audit row, comments, pagination and report")
    else:
        check(api, json.loads(args.state.read_text(encoding="utf-8")))
        print("PASS: ticket, status, comments, history, credentials and report survived container recreation")


if __name__ == "__main__":
    main()
