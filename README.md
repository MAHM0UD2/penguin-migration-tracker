# P2 Penguin Tracking Mission

This project simulates and analyzes satellite constellations for tracking penguin migrations. It utilizes orbital mechanics and propagation models to evaluate different satellite configurations, ensuring reliable communication and tracking of penguin tags.

## Overview

The goal of this mission is to track penguins using satellite relays. The project evaluates different orbital configurations to maximize access time and minimize communication blackouts. 

Key technical aspects include:
- **Orbit Propagation**: Uses both Keplerian and numerical propagators (e.g., Dormand-Prince 853), accounting for perturbations like Earth's oblateness (J2).
- **Access Analysis**: Calculates access windows (opportunities, mean duration, total access, and maximum gap) between ground tags and satellites.
- **Data Handling**: Properly merges overlapping access intervals from multiple satellites to ensure accurate continuity measurements and avoid double-counting.
- **Orekit Integration**: Utilizes Orekit for physical datasets and orbital calculations.

## Findings and Recommendations

The analysis compares a single relay against two satellite constellations (inclinations of 53° and 70°).

- **Single Relay**: Provides very limited access (~2%) with a maximum communication gap of nearly 15.5 hours.
- **Constellation (i=53°)**: Offers the highest total access time (~14.4%), but has a maximum communication gap of roughly 14.4 hours.
- **Constellation (i=70°)**: Offers lower total access time (~9.4%) but features a shorter maximum communication gap of roughly 12.0 hours.

**Recommendation**: The constellation with an inclination of 70° is recommended. While its overall access time is lower, reducing the maximum communication blackout to 12 hours is critical to prevent losing track of the migration.

## Project Structure

- `report.txt`: Detailed technical report containing orbit checks, assumptions, and access analysis.
- `orekit-data/`: Contains frequently updated physical datasets required for Orekit calculations (maintained independently of application code).
- `python/`: Source code for the orbital simulations and data analysis.
- `cesium/` & `gmat/`: Related visualization tools and mission analysis data.
- `penguin_migration.csv`: Dataset containing penguin migration coordinates or related telemetry.

## Analysis Plots

![Position Difference](out/position-difference.pdf)
![Position Difference - TwoBody vs. J2](out/position-difference-task13.pdf)
