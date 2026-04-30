# Modrinth Dashboard

Native macOS app (Apple Silicon) for tracking Modrinth mod portfolio analytics.

## Features

- **Project list** — all mods from a Modrinth user (default: Itamio)
- **Coverage tracking** — % of repository-supported versions/loaders each mod covers
- **Version status** — green (supported), orange (ghost/archived), red (missing)
- **Lifetime analytics** — downloads, views, revenue over time (requires API token)
- **Business metrics** — MoM growth, conversion rate, CAGR, retention, velocity, etc.
- **Today's stats** — current day downloads and views
- **Relative scoring** — each KPI color-coded red/orange/green relative to portfolio average

## Build

```bash
cd tools/ModrinthDashboard
./build.sh
```

Output: `build/ModrinthDashboard.app`

## Run

```bash
open build/ModrinthDashboard.app
```

Or double-click `ModrinthDashboard.app` in Finder.

## Configuration

Click the gear icon (⚙️) to configure:

- **Username** — Modrinth username (default: `Itamio`)
- **API Token** — Personal Access Token from [modrinth.com/settings/pats](https://modrinth.com/settings/pats)
  - Required scopes: `ANALYTICS_READ`, `PAYOUTS_READ`
  - Without a token: analytics are synthetic (total downloads spread evenly across days)

## Coverage Calculation

The app reads `version-manifest.json` from the repository root to determine all
supported (version, loader) targets. For each mod:

- **Supported** (green) — published & listed versions matching manifest targets
- **Ghost** (orange) — published but archived/unlisted versions
- **Missing** (red) — manifest targets not yet published

Coverage % = supported / total manifest targets × 100

## Business Metrics

| Metric | Formula | Meaning |
|--------|---------|---------|
| **MoM Growth** | (last 30d - prior 30d) / prior 30d × 100 | Month-over-month download growth rate |
| **Conversion Rate** | downloads / views × 100 | % of page views that result in a download |
| **7d Velocity** | sum(last 7 days) / 7 | Current daily download momentum |
| **30d Velocity** | sum(last 30 days) / 30 | Smoothed monthly trend |
| **Retention Rate** | 7d velocity / peak daily × 100 | % of peak still being achieved |
| **CAGR** | ((last 30d / first 30d)^(1/years) - 1) × 100 | Compound annual growth rate |
| **Rev / Download** | total revenue / total downloads | Average revenue per download |
| **Peak Daily DL** | max(daily downloads) | Highest single-day download count |

All metrics are color-coded relative to your portfolio average and best-performing mod:
- 🟢 Green: top 33% (strong performance)
- 🟠 Orange: middle 33% (average)
- 🔴 Red: bottom 33% (needs improvement)

## Requirements

- macOS 13.0+ (Ventura or later)
- Apple Silicon (arm64)
- Swift 6.2+ (included with Xcode Command Line Tools)

## Architecture

- **SwiftUI** — native macOS UI
- **Async/await** — concurrent API calls
- **Canvas** — custom chart rendering (no external dependencies)
- **Actor isolation** — thread-safe API client

All source files in `Sources/`:
- `Models.swift` — data structures
- `ManifestLoader.swift` — reads version-manifest.json
- `ModrinthAPI.swift` — HTTP client (v2 + v3 endpoints)
- `DashboardViewModel.swift` — state management
- `ChartView.swift` — line chart with hover tooltips
- `MetricsView.swift` — KPI cards
- `ProjectListView.swift` — left sidebar mod list
- `DetailSidebar.swift` — right sidebar detail view
- `SettingsView.swift` — username + token config
- `ContentView.swift` — main layout
- `App.swift` — app entry point

## API Endpoints Used

| Endpoint | Purpose | Auth |
|----------|---------|------|
| `GET /v2/user/{username}/projects` | Fetch user's mods | No |
| `GET /v2/project/{id}/version` | Fetch versions (for ghost detection) | No |
| `POST /v3/analytics` | Fetch downloads/views/revenue time series | Yes (PAT) |

## License

MIT
