# Deploying the Focus Buddy sync server

The server is a single jar (Ktor + SQLite, ~100-170 MB RSS) that listens **only on the VPS's
Tailscale IP**, so it is reachable from your own devices and nothing else. Requests also need a
bearer token.

## 1. Tailscale on every device

All three must be in the same tailnet (same Tailscale account):

- **VPS (Ubuntu):** `curl -fsSL https://tailscale.com/install.sh | sh && sudo tailscale up`,
  then open the printed login link. `tailscale ip -4` should print a `100.x.y.z` address.
- **Linux laptop:** same commands (or your distro's `tailscale` package plus
  `sudo systemctl enable --now tailscaled && sudo tailscale up`).
- **Android phone:** install Tailscale from Google Play, sign in with the same account, and
  leave the VPN switched on.

## 2. Install or upgrade the server

From the repo root on your laptop (you need ssh access to the VPS as root or as a sudoer):

```sh
deploy/install-server.sh my-vps        # any ssh host or alias from ~/.ssh/config
```

The script builds `server/build/libs/focus-buddy-server.jar`, copies it and the systemd unit
to the VPS, installs Java 21 if needed, creates the `focusbuddy` user and
`/etc/focus-buddy.env`, then starts `focus-buddy-server` and checks `/health`. It ends by
printing the URL and token:

```
  URL:   http://100.x.y.z:8787
  Token: 3f9c…
```

You can re-run it at any time to upgrade. The token is created on the first run and then
kept. The data lives in `/var/lib/focus-buddy/focus-buddy.db` (SQLite in WAL mode; copy all
three `focus-buddy.db*` files if you want a backup).

## 3. Connect the apps

In **both** the desktop app and the Android app, open **Settings -> Sync** and enter the URL
and token. Each device sends its new events and pulls everyone else's. Responses hold at
most 5000 events, so a device that is far behind catches up over a few syncs.

## Useful commands on the VPS

```sh
systemctl status focus-buddy-server
journalctl -u focus-buddy-server -f
sudo cat /etc/focus-buddy.env          # shows the token again
```

To change the token, edit `BUDDY_TOKEN` in `/etc/focus-buddy.env`, run
`sudo systemctl restart focus-buddy-server`, and enter the new token in both apps.
