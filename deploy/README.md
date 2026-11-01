# Deploying the Focus Buddy sync server

The server is a single jar (Ktor + SQLite, ~100-170 MB RSS) that stores an append-only event log.
Two ways to expose it:

- **Tailnet only (default):** binds the VPS's Tailscale IP. Every device must run Tailscale.
- **Tailscale Funnel:** binds `127.0.0.1` and publishes HTTPS at
  `https://<host>.<tailnet>.ts.net`. Clients need only the URL and token, no VPN app.

Both modes require the bearer token; requests are also rate limited per client
(`BUDDY_RATE_PER_MIN`, default 60/min) because a public endpoint should not be hammerable.

## 1. Install or upgrade

From the repo root on your laptop (ssh access to the VPS as root or a sudoer):

```sh
deploy/install-server.sh my-vps              # tailnet only
deploy/install-server.sh my-vps --funnel     # public HTTPS via Tailscale Funnel
```

The script builds the jar, copies it and the systemd unit, installs Java 21 if needed, creates
the `focusbuddy` user and `/etc/focus-buddy.env`, starts the service and checks `/health`, then
prints the URL and token:

```
  URL:   https://focus-buddy-tokyo.tail57e2d4.ts.net
  Token: 3f9c...
```

It is idempotent: re-run to upgrade, the token and database are kept. Data lives in
`/var/lib/focus-buddy/focus-buddy.db` (SQLite WAL; copy all `focus-buddy.db*` files to back up).

### Funnel details

Funnel needs to be allowed for the node in the Tailscale admin console
(Machines -> your VPS -> Funnel, or an ACL `nodeAttrs` entry). The script runs
`tailscale funnel --bg <port>` on the VPS; check it with `tailscale funnel status`.
Turning it off: `tailscale funnel --https=443 off`.

Because the Funnel endpoint is public, keep the token secret. The server writes nothing but the
event log, and `/health` is the only unauthenticated route.

## 2. Tailscale (only for the tailnet-only mode)

The VPS, laptop and phone must be in one tailnet:

- **VPS:** `curl -fsSL https://tailscale.com/install.sh | sh && sudo tailscale up`.
- **Laptop:** distro package plus `sudo systemctl enable --now tailscaled && sudo tailscale up`.
- **Phone:** Tailscale from the Play Store, same account, VPN on.

With Funnel, clients do not need Tailscale at all; only the VPS does.

## 3. Connect the apps

In both apps: **Settings -> Sync**, enter the URL and token, press Save. Each device pushes its
new events and pulls everyone else's; a device far behind catches up over a few syncs (5000
events per response). When the server is unreachable both apps keep working and retry every
5 minutes.

## Useful commands on the VPS

```sh
systemctl status focus-buddy-server
journalctl -u focus-buddy-server -f
sudo cat /etc/focus-buddy.env          # the token
tailscale funnel status
```

To change the token: edit `BUDDY_TOKEN` in `/etc/focus-buddy.env`, restart the service, and
enter the new token in both apps. To change the rate limit, set `BUDDY_RATE_PER_MIN`.
