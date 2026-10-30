#!/usr/bin/env bash
# Refreshes the ad lists bundled in the APK (app/assets/hosts.txt, hide.css), then rebuild. The installed app
# also refreshes its own copy weekly with the same code (ListUpdater), so this only matters for fresh installs.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out/lists app/assets
javac -nowarn -d out/lists app/src/app/webtv/ListUpdater.java
# IPv4: on networks with broken IPv6, Java (unlike curl) hangs instead of falling back.
java -Djava.net.preferIPv4Stack=true -cp out/lists app.webtv.ListUpdater app/assets
