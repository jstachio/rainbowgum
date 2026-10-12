#!/usr/bin/env bash
# Regenerates the README screenshots in etc/screenshots.
#
# Each shot logs the same sample events through a real Rainbow Gum configured only by
# properties, feeds the ANSI output through xterm.js (the terminal emulator VS Code
# uses, headless build) and screenshots the resulting cells with headless Firefox.
#
# Needs: a built core jar (./mvnw install -pl core), node/npm, firefox, python3 with Pillow.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
JAR=$(ls "$ROOT"/core/target/rainbowgum-core-*-SNAPSHOT.jar | grep -v javadoc | head -1)
# Under $HOME rather than /tmp: a snap packaged Firefox cannot read /tmp.
WORK=$(mktemp -d "$HOME/rg-screenshots.XXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/profile" "$WORK/raw"
cp "$HERE/render.mjs" "$WORK/"
(cd "$WORK" && npm init -y >/dev/null && npm install --silent @xterm/headless@6.0.0)

shot() { # name terminal-profile title max-events properties...
	local name=$1 profile=$2 title=$3 max=$4
	shift 4
	printf '%s\n' "logging.level=TRACE" "$@" >"$WORK/$name.properties"
	TZ=UTC java -cp "$JAR" "$HERE/Shot.java" "$WORK/$name.properties" "$max" >"$WORK/$name.ansi"
	read -r W H < <(cd "$WORK" && node render.mjs "$name.ansi" "$name.html" "$profile" "$title")
	firefox --headless --profile "$WORK/profile" --window-size="$W,$H" \
		--screenshot "$WORK/raw/$name.png" "file://$WORK/$name.html" >/dev/null 2>&1
	python3 "$HERE/frame.py" "$WORK/raw/$name.png" "$HERE/../$name.png"
}

# color=force because the output is captured, not written to a terminal.
E=logging.appender.console.encoder
shot ttll-rainbowgum dark "encoder=ttll   theme=rainbowgum" 6 "$E.uri=ttll" "$E.ttll.color=force"
shot ttll-spring light "encoder=ttll   theme=spring" 6 "$E.uri=ttll" "$E.ttll.color=force" "$E.ttll.theme=spring"
shot ttll-one_dark one_dark "encoder=ttll   theme=one_dark" 6 "$E.uri=ttll" "$E.ttll.color=force" "$E.ttll.theme=one_dark"
shot ttll-darcula darcula "encoder=ttll   theme=darcula" 6 "$E.uri=ttll" "$E.ttll.color=force" "$E.ttll.theme=darcula"
shot logfmt-one_dark one_dark "encoder=logfmt   theme=one_dark" 6 "$E.uri=logfmt" "$E.logfmt.color=force" "$E.logfmt.theme=one_dark"
shot json5-rainbowgum dark "encoder=json5   prettyPrint=full" 2 "$E.uri=json5" "$E.json5.color=force" "$E.json5.prettyPrint=full"
