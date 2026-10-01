#!/usr/bin/env python3
"""
============================================================
Movie Ticket System — Test Data Seeder
============================================================
Seeds the database with test data for manual testing or
before running Gatling load tests.

Prerequisites: All services must be running.
Usage: python load-tests/scripts/seed-test-data.py
============================================================
"""

import json
import sys
from datetime import date, timedelta
from urllib.request import Request, urlopen
from urllib.error import HTTPError

USERS_URL = "http://localhost:8081"
THEATERS_URL = "http://localhost:8082"
MOVIES_URL = "http://localhost:8083"
HEADERS = {"Content-Type": "application/json"}


def colored(text, color):
    """Simple ANSI color support."""
    colors = {
        "cyan": "\033[96m",
        "yellow": "\033[93m",
        "green": "\033[92m",
        "gray": "\033[90m",
        "white": "\033[97m",
        "darkyellow": "\033[33m",
        "red": "\033[91m",
    }
    reset = "\033[0m"
    return f"{colors.get(color, '')}{text}{reset}"


def api_request(url, data=None, method="POST"):
    """Make an HTTP request and return parsed JSON response."""
    body = json.dumps(data).encode("utf-8") if data else None
    req = Request(url, data=body, headers=HEADERS, method=method)
    try:
        with urlopen(req) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except HTTPError as e:
        error_body = e.read().decode("utf-8")
        print(colored(f"  HTTP {e.code}: {error_body}", "red"))
        raise


def main():
    print(colored("\n=== Movie Ticket System — Seeding Test Data ===", "cyan"))

    # ── Step 1: Register a test admin user ──
    print(colored("\n[1/4] Registering test user...", "yellow"))
    register_body = {
        "name": "Test Admin",
        "email": "admin@movietickets.com",
        "password": "admin123",
        "phone": "+91-9876543210",
    }

    try:
        auth_response = api_request(f"{USERS_URL}/api/users/register", register_body)
        token = auth_response["data"]["token"]
        user_id = auth_response["data"]["user"]["id"]
        print(colored(f"  User registered: {user_id}", "green"))
        print(colored(f"  JWT Token: {token[:30]}...", "gray"))
    except Exception:
        print(colored("  Registration failed (user may already exist), trying login...", "darkyellow"))
        login_body = {
            "email": "admin@movietickets.com",
            "password": "admin123",
        }
        auth_response = api_request(f"{USERS_URL}/api/users/login", login_body)
        token = auth_response["data"]["token"]
        user_id = auth_response["data"]["user"]["id"]
        print(colored(f"  User logged in: {user_id}", "green"))

    # ── Step 2: Create a Theater with 2 screens ──
    print(colored("\n[2/4] Creating theater with 2 screens...", "yellow"))
    theater_body = {
        "name": "PVR Cinemas - Test",
        "city": "Mumbai",
        "address": "Phoenix Mall, Lower Parel",
        "screens": [
            {
                "name": "IMAX Screen 1",
                "seatLayouts": [
                    {
                        "category": "REGULAR",
                        "rowPrefix": "A",
                        "rowCount": 8,
                        "seatsPerRow": 12,
                        "price": 250.00,
                    },
                    {
                        "category": "PREMIUM",
                        "rowPrefix": "I",
                        "rowCount": 4,
                        "seatsPerRow": 12,
                        "price": 450.00,
                    },
                    {
                        "category": "VIP",
                        "rowPrefix": "M",
                        "rowCount": 2,
                        "seatsPerRow": 8,
                        "price": 750.00,
                    },
                ],
            },
            {
                "name": "Screen 2",
                "seatLayouts": [
                    {
                        "category": "REGULAR",
                        "rowPrefix": "A",
                        "rowCount": 10,
                        "seatsPerRow": 10,
                        "price": 200.00,
                    }
                ],
            },
        ],
    }

    theater_response = api_request(f"{THEATERS_URL}/api/theaters", theater_body)
    theater_id = theater_response["data"]["id"]
    screen1_id = theater_response["data"]["screens"][0]["id"]
    screen2_id = theater_response["data"]["screens"][1]["id"]
    print(colored(f"  Theater: {theater_id}", "green"))
    print(colored(f"  Screen 1 (IMAX): {screen1_id}", "green"))
    print(colored(f"  Screen 2: {screen2_id}", "green"))

    # ── Step 3: Create 2 Movies ──
    print(colored("\n[3/4] Creating movies...", "yellow"))
    today = date.today().isoformat()

    movie1_body = {
        "title": "Interstellar",
        "description": "A team of explorers travel through a wormhole in space",
        "genre": "Sci-Fi",
        "durationMinutes": 169,
        "language": "English",
        "releaseDate": today,
    }
    movie1_response = api_request(f"{MOVIES_URL}/api/movies", movie1_body)
    movie1_id = movie1_response["data"]["id"]
    print(colored(f"  Movie 1: {movie1_id} (Interstellar)", "green"))

    movie2_body = {
        "title": "The Dark Knight",
        "description": "Batman faces the Joker",
        "genre": "Action",
        "durationMinutes": 152,
        "language": "English",
        "releaseDate": today,
    }
    movie2_response = api_request(f"{MOVIES_URL}/api/movies", movie2_body)
    movie2_id = movie2_response["data"]["id"]
    print(colored(f"  Movie 2: {movie2_id} (The Dark Knight)", "green"))

    # ── Step 4: Create Schedules ──
    print(colored("\n[4/4] Creating schedules...", "yellow"))
    tomorrow = (date.today() + timedelta(days=1)).isoformat()

    schedule1_body = {
        "movieId": movie1_id,
        "screenId": screen1_id,
        "showDate": tomorrow,
        "showTime": "14:00",
    }
    schedule1_response = api_request(f"{MOVIES_URL}/api/movies/schedules", schedule1_body)
    schedule1_id = schedule1_response["data"]["id"]
    print(colored(f"  Schedule 1: {schedule1_id} (Interstellar @ IMAX, 2:00 PM)", "green"))

    schedule2_body = {
        "movieId": movie2_id,
        "screenId": screen2_id,
        "showDate": tomorrow,
        "showTime": "18:00",
    }
    schedule2_response = api_request(f"{MOVIES_URL}/api/movies/schedules", schedule2_body)
    schedule2_id = schedule2_response["data"]["id"]
    print(colored(f"  Schedule 2: {schedule2_id} (Dark Knight @ Screen 2, 6:00 PM)", "green"))

    # ── Summary ──
    print(colored("\n=== Seed Complete ===", "cyan"))
    print(colored("Seats are being seeded via Kafka (wait ~5 seconds)", "gray"))
    print()
    print(colored("Key IDs for testing:", "white"))
    print(f"  User ID:      {user_id}")
    print(f"  Theater ID:   {theater_id}")
    print(f"  Screen 1 ID:  {screen1_id}")
    print(f"  Screen 2 ID:  {screen2_id}")
    print(f"  Movie 1 ID:   {movie1_id}")
    print(f"  Movie 2 ID:   {movie2_id}")
    print(f"  Schedule 1:   {schedule1_id}")
    print(f"  Schedule 2:   {schedule2_id}")
    print(f"  JWT Token:    {token[:50]}...")
    print()
    print(colored("Test a booking:", "yellow"))
    booking_json = json.dumps({
        "userId": user_id,
        "scheduleId": schedule1_id,
        "seatNumbers": ["A1", "A2"],
    })
    print(f"  curl -X POST http://localhost:8080/api/bookings \\")
    print(f'    -H "Authorization: Bearer {token[:20]}..." \\')
    print(f'    -H "Content-Type: application/json" \\')
    print(f"    -d '{booking_json}'")
    print()


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(colored(f"\nError: {e}", "red"))
        sys.exit(1)
