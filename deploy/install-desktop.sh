#!/usr/bin/env bash
# Build Focus Buddy for the desktop and install it for the current user (Linux):
# jar + icon under ~/.local/share/focus-buddy-app, launcher in ~/.local/share/applications.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$HOME/.local/share/focus-buddy-app"
JAVA="${JAVA:-$(command -v java)}"

(cd "$ROOT" && ./gradlew --quiet :composeApp:packageUberJarForCurrentOS)
JAR="$(ls -t "$ROOT"/composeApp/build/compose/jars/focus-buddy-*.jar | head -n1)"

install -d "$APP_DIR" "$HOME/.local/share/applications" "$HOME/.local/bin"
install -m 644 "$JAR" "$APP_DIR/focus-buddy.jar"
install -m 644 "$ROOT/composeApp/icon.png" "$APP_DIR/icon.png"

cat > "$HOME/.local/bin/focus-buddy" <<SH
#!/bin/sh
exec "$JAVA" -Xms64m -Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED -jar "$APP_DIR/focus-buddy.jar" "\$@"
SH
chmod 755 "$HOME/.local/bin/focus-buddy"

cat > "$HOME/.local/share/applications/focus-buddy.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Focus Buddy
Comment=Cozy focus timer with Pip
Exec=$HOME/.local/bin/focus-buddy
Icon=$APP_DIR/icon.png
Categories=Utility;Office;
StartupWMClass=focus-buddy
DESKTOP
update-desktop-database "$HOME/.local/share/applications" >/dev/null 2>&1 || true
echo "Installed. Launch 'Focus Buddy' from your app menu or run: focus-buddy"
