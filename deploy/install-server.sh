#!/usr/bin/env bash
# Build the Focus Buddy sync server and install/update it on an Ubuntu host reachable over ssh.
#
#   deploy/install-server.sh <ssh-host>
#
# Idempotent: re-run it to upgrade. The token in /etc/focus-buddy.env is generated once and
# kept on later runs; BUDDY_HOST is refreshed to the host's current Tailscale IPv4 every time.
set -euo pipefail

if [ $# -ne 1 ] || [ -z "$1" ]; then
    echo "usage: $0 <ssh-host>" >&2
    exit 64
fi
HOST="$1"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/server/build/libs/focus-buddy-server.jar"
UNIT="$ROOT/deploy/focus-buddy-server.service"

echo "==> Building fat jar"
(cd "$ROOT" && ./gradlew --quiet :server:buildFatJar)
[ -f "$JAR" ] || { echo "ERROR: $JAR was not built" >&2; exit 1; }

LOCAL_TMP="$(mktemp -d)"
REMOTE_TMP=""
cleanup() {
    rm -rf "$LOCAL_TMP"
    if [ -n "$REMOTE_TMP" ]; then ssh "$HOST" "rm -rf '$REMOTE_TMP'" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

# ---- the part that runs on the server (as root) ----
cat > "$LOCAL_TMP/remote-install.sh" <<'REMOTE'
set -euo pipefail
SRC="$1"
ENV_FILE=/etc/focus-buddy.env
export DEBIAN_FRONTEND=noninteractive

if ! command -v tailscale >/dev/null 2>&1; then
    echo "ERROR: tailscale is not installed on this host." >&2
    echo "       Install it:  curl -fsSL https://tailscale.com/install.sh | sh && sudo tailscale up" >&2
    exit 1
fi
TS_IP="$(tailscale ip -4 2>/dev/null | head -n1 || true)"
if [ -z "$TS_IP" ]; then
    echo "ERROR: Tailscale is not up on this host (no IPv4 from 'tailscale ip -4')." >&2
    echo "       Run 'sudo tailscale up', log in, then re-run this script." >&2
    exit 1
fi
echo "==> Tailscale IPv4: $TS_IP"

java_major() {
    local v
    v="$(/usr/bin/java -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p' || true)"
    echo "${v:-0}"
}
apt_updated=0
apt_install() {
    if [ "$apt_updated" = 0 ]; then apt-get update -q; apt_updated=1; fi
    apt-get install -y -q "$@"
}
if [ ! -x /usr/bin/java ] || [ "$(java_major)" -lt 21 ]; then
    echo "==> Installing openjdk-21-jre-headless"
    apt_install openjdk-21-jre-headless
fi
if [ "$(java_major)" -lt 21 ]; then
    echo "ERROR: /usr/bin/java is Java $(java_major) but 21+ is required." >&2
    echo "       Select Java 21 with: sudo update-alternatives --config java" >&2
    exit 1
fi
command -v curl >/dev/null 2>&1 || apt_install curl

echo "==> Installing files"
id -u focusbuddy >/dev/null 2>&1 ||
    useradd --system --user-group --home-dir /var/lib/focus-buddy --no-create-home \
        --shell /usr/sbin/nologin focusbuddy
install -d -m 755 /opt/focus-buddy
install -d -o focusbuddy -g focusbuddy -m 700 /var/lib/focus-buddy
install -m 644 "$SRC/focus-buddy-server.jar" /opt/focus-buddy/focus-buddy-server.jar
install -m 644 "$SRC/focus-buddy-server.service" /etc/systemd/system/focus-buddy-server.service

if [ ! -f "$ENV_FILE" ]; then
    echo "==> Creating $ENV_FILE with a new token"
    token="$(head -c 32 /dev/urandom | od -An -v -tx1 | tr -d ' \n')"
    (umask 077 && printf 'BUDDY_TOKEN=%s\nBUDDY_HOST=%s\nBUDDY_PORT=8787\n' "$token" "$TS_IP" > "$ENV_FILE")
fi
if grep -q '^BUDDY_HOST=' "$ENV_FILE"; then
    sed -i "s/^BUDDY_HOST=.*/BUDDY_HOST=$TS_IP/" "$ENV_FILE"
else
    echo "BUDDY_HOST=$TS_IP" >> "$ENV_FILE"
fi
chown root:root "$ENV_FILE"
chmod 600 "$ENV_FILE"
PORT="$(sed -n 's/^BUDDY_PORT=//p' "$ENV_FILE" | tail -n1)"
PORT="${PORT:-8787}"
TOKEN="$(sed -n 's/^BUDDY_TOKEN=//p' "$ENV_FILE" | tail -n1)"

echo "==> (Re)starting focus-buddy-server"
systemctl daemon-reload
systemctl enable --quiet focus-buddy-server.service
systemctl restart focus-buddy-server.service

URL="http://$TS_IP:$PORT"
ok=0
for _ in $(seq 1 30); do
    if [ "$(curl -fsS --max-time 2 "$URL/health" 2>/dev/null || true)" = "ok" ]; then ok=1; break; fi
    sleep 1
done
if [ "$ok" != 1 ]; then
    echo "ERROR: $URL/health did not answer within 30s. Recent logs:" >&2
    journalctl -u focus-buddy-server.service -n 30 --no-pager >&2 || true
    exit 1
fi

echo
echo "Focus Buddy sync server is running."
echo "In both apps open Settings -> Sync and enter:"
echo "  URL:   $URL"
echo "  Token: $TOKEN"
REMOTE

echo "==> Uploading to $HOST"
REMOTE_TMP="$(ssh "$HOST" 'mktemp -d')"
scp -q "$JAR" "$UNIT" "$LOCAL_TMP/remote-install.sh" "$HOST:$REMOTE_TMP/"

if [ "$(ssh "$HOST" 'id -u')" = 0 ]; then SUDO=""; else SUDO="sudo"; fi
# -t so sudo can prompt for a password if it needs one.
ssh -t "$HOST" "$SUDO bash '$REMOTE_TMP/remote-install.sh' '$REMOTE_TMP'"
