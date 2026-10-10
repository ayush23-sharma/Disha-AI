"""
Generate synthetic Faridabad crime coordinates and detect danger zones with DBSCAN.

This module is intentionally deterministic for demo reproducibility.
"""

from __future__ import annotations

import logging
from collections import defaultdict
from typing import DefaultDict

import numpy as np
from sklearn.cluster import DBSCAN

# Expanded Geographic bounds for the entire Faridabad District
LATITUDE_MIN: float = 28.3200  # South (Past Ballabgarh)
LATITUDE_MAX: float = 28.5100  # North (Delhi Border)
LONGITUDE_MIN: float = 77.2600 # West (Surajkund/Aravalli side)
LONGITUDE_MAX: float = 77.3800 # East (Greater Faridabad / Neharpar)

# Synthetic dataset size and reproducibility controls.
TOTAL_POINTS: int = 1000
RANDOM_SEED: int = 42

# Earth radius (km) and physical distance threshold converted to radians.
EARTH_RADIUS_KM: float = 6371.0088
EPSILON_RAD: float = 0.8 / EARTH_RADIUS_KM
MIN_SAMPLES: int = 8

logger = logging.getLogger(__name__)


def _configure_logging() -> None:
    """Configure logging if the host application has not already done so."""
    if not logging.getLogger().handlers:
        logging.basicConfig(
            level=logging.INFO,
            format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
        )


def generate_danger_zones() -> list[dict[str, float]]:
    """
    Create synthetic coordinates and return cluster centroids as danger zones.

    Returns:
        List of JSON-safe centroid dictionaries:
        [{"latitude": 28.4x, "longitude": 77.3x}, ...]
    """
    _configure_logging()
    logger.info("Starting synthetic crime generation for Faridabad.")

    try:
        np.random.seed(RANDOM_SEED)
        logger.info("Random seed set to %d for reproducibility.", RANDOM_SEED)

        latitudes: np.ndarray = np.random.uniform(LATITUDE_MIN, LATITUDE_MAX, TOTAL_POINTS)
        longitudes: np.ndarray = np.random.uniform(LONGITUDE_MIN, LONGITUDE_MAX, TOTAL_POINTS)
        coords: np.ndarray = np.column_stack((latitudes, longitudes))
        logger.info("Generated %d synthetic coordinates.", coords.shape[0])

        coords_rad: np.ndarray = np.radians(coords)
        logger.info("Converted coordinate matrix to radians for haversine distance.")

        model = DBSCAN(
            eps=EPSILON_RAD,
            min_samples=MIN_SAMPLES,
            metric="haversine",
            algorithm="ball_tree",
        )
        labels: np.ndarray = model.fit_predict(coords_rad)
        logger.info("DBSCAN completed. Unique labels found: %d", len(set(labels.tolist())))

        # Group valid (non-noise) coordinates by cluster id.
        clustered_points: DefaultDict[int, list[np.ndarray]] = defaultdict(list)
        for idx, label in enumerate(labels):
            if label == -1:
                continue
            clustered_points[int(label)].append(coords[idx])

        if not clustered_points:
            logger.warning("No valid clusters produced (all points classified as noise).")
            return []

        # Compute centroids and cast to native float for JSON safety.
        danger_zones: list[dict[str, float]] = []
        for cluster_label in sorted(clustered_points.keys()):
            cluster_array = np.array(clustered_points[cluster_label], dtype=np.float64)
            mean_latitude = float(np.mean(cluster_array[:, 0]))
            mean_longitude = float(np.mean(cluster_array[:, 1]))
            danger_zones.append({"latitude": mean_latitude, "longitude": mean_longitude})

        logger.info(
            "Generated %d danger-zone centroids after filtering noise.",
            len(danger_zones),
        )
        return danger_zones

    except ValueError as exc:
        logger.exception("Invalid data or DBSCAN configuration: %s", exc)
        raise
    except Exception as exc:
        logger.exception("Unexpected failure during danger-zone generation: %s", exc)
        raise RuntimeError("Failed to generate danger zones.") from exc


import json

if __name__ == "__main__":
    zones = generate_danger_zones()
    print(json.dumps(zones, indent=2))