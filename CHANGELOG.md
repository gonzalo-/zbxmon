# Changelog

## 1.0.0 - 2026-11-20
First public release.

- Hosts, problems, items and graphs (1 h / 6 h / 24 h / 7 d); acknowledge, remove acknowledgement and comment; history of resolved problems (7 days); share a problem as text.
- Persistent, auto-saved filters: host states with live counts, problem severity, unacknowledged only, show/hide suppressed, host group / tag.
- Sort order (severity / name / most recent), favorite hosts pinned on top, compact or comfortable density, configurable refresh interval.
- Notifications without Google services: WorkManager polling plus an optional foreground service; minimum severity, resolved notices,
  single grouped notification, quiet hours, snooze / mute per host or problem.
- Home screen widget with the latest per-server summary.
- Multiple server profiles with a per-profile connection mode (system CA, pinned self-signed certificate, plain HTTP for LAN/VPN).
- API tokens encrypted with the Android Keystore; backups disabled.
- English and Spanish UI (selectable in Settings), Minimal and Terminal styles.
- Donation details (Bitcoin) in Settings.
