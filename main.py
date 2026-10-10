"""
PHASE 2 BACKEND SETUP (RUN THESE EXACT COMMANDS)
------------------------------------------------
1) Install dependencies:
   pip install fastapi "uvicorn[standard]" scikit-learn numpy pydantic

2) Start the server:
   python main.py
"""

from __future__ import annotations

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from ml_clustering import generate_danger_zones


class DangerZone(BaseModel):
    latitude: float
    longitude: float


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


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)