"""
BACKEND SETUP
-------------
1) Install dependencies:
   python -m pip install -r requirements.txt

2) Configure the Directions API key:
   Copy .env.example to .env and replace the placeholder with your real key.
   The application loads .env automatically at startup.

   Alternatively, set the environment variable directly (e.g. on a hosting
   platform where .env files are not used):
   Windows PowerShell:  $env:DIRECTIONS_API_KEY = "YOUR_KEY_HERE"
   macOS/Linux:         export DIRECTIONS_API_KEY="YOUR_KEY_HERE"
   An environment variable already present takes precedence over .env.

3) Start the server:
   python main.py
"""

from __future__ import annotations

import os
from pathlib import Path

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from ml_clustering import generate_danger_zones

# ---------------------------------------------------------------------------
# Load .env from the same directory as this file before reading any secrets.
# override=False means a variable already set in the OS environment wins.
# ---------------------------------------------------------------------------
_PROJECT_ROOT = Path(__file__).resolve().parent
load_dotenv(_PROJECT_ROOT / ".env", override=False)

# The Directions API key is kept on the server only — never sent to the app.
_DIRECTIONS_API_KEY: str = os.environ.get("DIRECTIONS_API_KEY", "")
_DIRECTIONS_BASE_URL = "https://maps.googleapis.com/maps/api/directions/json"


class DangerZone(BaseModel):
    latitude: float
    longitude: float


class RoutePolyline(BaseModel):
    """Encoded overview polyline for a single Directions API route."""
    overview_polyline: str


app = FastAPI(
    title="Disha AI Danger Zone API",
    version="1.0.0",
    description="FastAPI service exposing DBSCAN-derived danger-zone centroids for Faridabad.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/v1/danger-zones", response_model=list[DangerZone])
async def get_danger_zones() -> list[DangerZone]:
    try:
        zones = generate_danger_zones()
        return [DangerZone(**zone) for zone in zones]
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to generate danger zones: {exc}",
        ) from exc


@app.get("/api/v1/directions", response_model=list[RoutePolyline])
async def get_directions(
    origin_lat: float = Query(..., description="Origin latitude"),
    origin_lng: float = Query(..., description="Origin longitude"),
    dest_lat: float = Query(..., description="Destination latitude"),
    dest_lng: float = Query(..., description="Destination longitude"),
) -> list[RoutePolyline]:
    """
    Proxy the Google Directions API and return only encoded overview polylines.

    The Directions API key is kept server-side in the DIRECTIONS_API_KEY
    environment variable and is never forwarded to the Android client.
    """
    if not _DIRECTIONS_API_KEY:
        raise HTTPException(
            status_code=503,
            detail="Directions service is not configured on this server. "
                   "Set the DIRECTIONS_API_KEY environment variable.",
        )

    params = {
        "origin": f"{origin_lat},{origin_lng}",
        "destination": f"{dest_lat},{dest_lng}",
        "mode": "driving",
        "alternatives": "true",
        "key": _DIRECTIONS_API_KEY,
    }

    async with httpx.AsyncClient(timeout=10.0) as client:
        try:
            resp = await client.get(_DIRECTIONS_BASE_URL, params=params)
            resp.raise_for_status()
        except httpx.HTTPStatusError as exc:
            # Do not forward the upstream URL (it contains the key).
            raise HTTPException(
                status_code=502,
                detail=f"Directions upstream error: HTTP {exc.response.status_code}",
            ) from exc
        except httpx.RequestError as exc:
            raise HTTPException(
                status_code=502,
                detail=f"Directions upstream unreachable: {type(exc).__name__}",
            ) from exc

    data = resp.json()
    status = data.get("status", "UNKNOWN")
    if status != "OK":
        error_message = data.get("error_message", "")
        raise HTTPException(
            status_code=502,
            detail=f"Directions API status: {status}. {error_message}".strip(),
        )

    routes = data.get("routes", [])
    return [
        RoutePolyline(overview_polyline=route["overview_polyline"]["points"])
        for route in routes
        if route.get("overview_polyline", {}).get("points")
    ]


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)