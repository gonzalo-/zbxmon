# Security

## Reporting a vulnerability
Please use GitHub's private vulnerability reporting ("Security" tab → "Report a vulnerability")
instead of opening a public issue.

## How secrets are handled
- API tokens are stored encrypted (AES-256-GCM) with a key held in the Android Keystore.
- `android:allowBackup` is disabled: tokens are never included in device backups.
- Self-signed servers are authenticated by pinning the SHA-256 fingerprint of the server certificate.
  The fingerprint is read once over an unauthenticated connection when you tap "Get server fingerprint",
  so verify it against your server before saving.
- Plain `http://` URLs are allowed for LAN/VPN use only; the token is sent unencrypted in that case.
- Use a dedicated, least-privileged Zabbix user for the API token.
