"""Replay Olist order milestones through TraceGuard's simulated-reader API."""

import argparse
import csv
import getpass
import hashlib
import json
import os
import sys
from datetime import UTC, date, datetime, time, timedelta
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

MILESTONES = (
    ("order_approved_at", "Olist approval (simulated)", "REGISTERED"),
    ("order_delivered_carrier_date", "Olist carrier handoff (simulated)", "RECEIVED"),
    ("order_delivered_customer_date", "Olist delivery (simulated)", "DELIVERED"),
)
ORG_NAME = "Olist academic replay"
PRODUCT_TYPE = "olist-replay"


def load_orders(path, limit):
    orders = {}
    with open(path, encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        required = {"order_id", "order_status", *(field for field, _, _ in MILESTONES)}
        missing = required.difference(reader.fieldnames or [])
        if missing:
            raise ValueError(f"Missing CSV columns: {', '.join(sorted(missing))}")
        for row in reader:
            order_id = (row["order_id"] or "").strip()
            if row["order_status"] != "delivered" or not order_id:
                continue
            try:
                # These source timestamps do not carry offsets. Use UTC for the demo clock.
                times = tuple(datetime.strptime(row[field] or "", "%Y-%m-%d %H:%M:%S").replace(tzinfo=UTC)
                              for field, _, _ in MILESTONES)
            except ValueError:
                continue
            if times[0] <= times[1] <= times[2]:
                if order_id in orders and orders[order_id] != times:
                    raise ValueError(f"Conflicting timestamps on CSV line {reader.line_num}")
                orders[order_id] = times
    selected = sorted(orders.items(), key=lambda item: (item[1][-1], item[0]), reverse=True)[:limit]
    if not selected:
        raise ValueError("No delivered orders have a complete, chronological set of milestones")
    return selected, len(orders)


class NoRedirects(HTTPRedirectHandler):
    """Keep the login and bearer token on the configured TraceGuard origin."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class TraceGuardApi:
    def __init__(self, base_url, email, password):
        url = urlsplit(base_url)
        loopback = url.hostname in {"localhost", "127.0.0.1", "::1"}
        if (url.scheme != "https" and not (url.scheme == "http" and loopback)) or not url.hostname:
            raise ValueError("Use HTTPS for a remote TraceGuard URL, or HTTP on localhost")
        if url.username or url.password or url.query or url.fragment or url.path not in {"", "/"}:
            raise ValueError("Use the TraceGuard origin without credentials, path, query or fragment")
        self.base_url = base_url.rstrip("/") + "/api"
        self.opener = build_opener(NoRedirects())
        self.token = None
        self.token = self.request("/auth/login", {"email": email, "password": password})["token"]

    def request(self, path, body=None, method=None):
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        if body is not None:
            headers["Content-Type"] = "application/json"
        request = Request(self.base_url + path,
                          data=json.dumps(body).encode() if body is not None else None,
                          headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=25) as response:
                return json.load(response)
        except HTTPError as error:
            raise ValueError(f"{request.method} {path}: HTTP {error.code}") from error


def find_or_create(api, path, field, body, items=None):
    existing = api.request(path) if items is None else items
    matches = [item for item in existing if item[field] == body[field]]
    if len(matches) > 1:
        raise ValueError(f"Multiple entries match {body[field]!r} at {path}")
    return matches[0] if matches else api.request(path, body)


def replay(api, orders, replay_end):
    shift = (datetime.combine(replay_end, time(12), UTC) - orders[0][1][-1]
             if replay_end else timedelta())
    source = [(order_id, tuple((stamp + shift).isoformat() for stamp in stamps))
              for order_id, stamps in orders]
    fingerprint = hashlib.sha256(json.dumps(source).encode()).hexdigest()[:16]
    org = find_or_create(api, "/organizations", "name", {"name": ORG_NAME, "type": "MANUFACTURER"})
    if org["type"] != "MANUFACTURER":
        raise ValueError("The reserved Olist replay organization has the wrong type")
    checkpoints = [cp for cp in api.request("/checkpoints") if cp["organizationId"] == org["id"]]
    checkpoint_ids = []
    for _, name, _ in MILESTONES:
        cp = find_or_create(api, "/checkpoints", "name", {
            "organizationId": org["id"], "name": name, "locationName": "Virtual replay site",
            "latitude": 0, "longitude": 0,
        }, checkpoints)
        if cp["latitude"] != 0 or cp["longitude"] != 0:
            raise ValueError(f"{name} must be at the shared virtual site")
        checkpoint_ids.append(cp["id"])
    policy = find_or_create(api, "/route-policies", "productType", {
        "productType": PRODUCT_TYPE, "maxTotalHours": 24 * 366,
        "steps": [{"checkpointId": checkpoint_id} for checkpoint_id in checkpoint_ids],
    })
    if ([step["checkpointId"] for step in policy["steps"]] != checkpoint_ids
            or policy["maxTotalHours"] != 24 * 366
            or any(step.get("maxHoursFromPrevious") is not None for step in policy["steps"])):
        raise ValueError("The existing Olist replay route policy differs from the expected route")
    product = find_or_create(api, "/products", "name", {
        "name": "Olist order milestones (academic replay)", "organizationId": org["id"],
        "productType": PRODUCT_TYPE,
    }, [p for p in api.request("/products") if p["organizationId"] == org["id"]])
    if product["productType"] != PRODUCT_TYPE:
        raise ValueError("The existing Olist replay product has an unexpected product type")
    batch = find_or_create(api, f"/products/{product['id']}/batches", "label",
                           {"label": f"OLIST-{fingerprint}"})
    if batch["recalled"]:
        raise ValueError("The replay batch was recalled")
    tags_path = f"/batches/{batch['id']}/tags"
    tags = api.request(tags_path)["tagIds"]
    if not tags:
        tags = api.request(f"{tags_path}?count={len(source)}", method="POST")["tagIds"]
    if len(tags) != len(source):
        raise ValueError("The replay batch has an unexpected tag count")
    for tag, (_, stamps) in zip(sorted(tags), source, strict=True):
        expected = list(zip(checkpoint_ids, (kind for _, _, kind in MILESTONES), stamps, strict=True))
        existing = api.request(f"/tags/{tag}/events")
        actual = [(event["checkpointId"], event["eventType"],
                   datetime.fromisoformat(event["occurredAt"]).isoformat()) for event in existing]
        if actual != expected[:len(actual)]:
            raise ValueError(f"Existing events for {tag} differ from the replay; ledger left intact")
        for checkpoint_id, kind, timestamp in expected[len(actual):]:
            api.request("/scans/simulate", {
                "tagId": tag, "checkpointId": checkpoint_id, "eventType": kind, "timestamp": timestamp,
            })
    print(f"Replayed {len(source)} journeys in {batch['label']}. Example tag: {sorted(tags)[0]}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_path", help="olist_orders_dataset.csv downloaded from Kaggle")
    parser.add_argument("--limit", type=int, default=50, help="Number of journeys to replay (1–1000)")
    parser.add_argument("--replay-end-date", type=date.fromisoformat,
                        help="Move the newest delivery to noon UTC on YYYY-MM-DD, preserving all time gaps")
    parser.add_argument("--url", default="http://localhost:5173", help="TraceGuard origin")
    parser.add_argument("--email", help="TraceGuard admin email")
    parser.add_argument("--dry-run", action="store_true", help="Validate and count orders without calling TraceGuard")
    args = parser.parse_args()
    if not 1 <= args.limit <= 1000:
        parser.error("--limit must be between 1 and 1000")
    try:
        orders, eligible = load_orders(args.csv_path, args.limit)
        print(f"CSV contains {eligible:,} eligible orders. Selected {len(orders):,} journeys ({len(orders) * 3:,} events).")
        if not args.dry_run:
            if not args.email:
                parser.error("--email is required unless using --dry-run")
            password = os.environ.get("TRACEGUARD_REPLAY_PASSWORD") or getpass.getpass("TraceGuard admin password: ")
            replay(TraceGuardApi(args.url, args.email, password), orders, args.replay_end_date)
    except (ValueError, OSError, OverflowError) as error:
        print(f"Replay failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
