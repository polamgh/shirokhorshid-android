#!/bin/bash
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
PKG="com.azaditunnel.vpn"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPORT="/tmp/azadi_full_report.txt"
: > "$REPORT"

log() { echo "$1" | tee -a "$REPORT"; }

ui_dump() {
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  $ADB shell cat /sdcard/ui.xml
}

ui_has() { ui_dump | rg -qi "$1"; }

dismiss_16k() {
  ui_dump | rg -q "16 KB" && $ADB shell input tap 519 1684 && sleep 1 || true
}

tap_center() { $ADB shell input tap "$1" "$2"; sleep "$3"; }

$ADB shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
$ADB shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true

log "=== PHASE A: First Launch ==="
$ADB shell pm clear "$PKG" >/dev/null
$ADB shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
$ADB shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true
$ADB shell am start -n "$PKG/com.psiphon3.MainActivity" >/dev/null
sleep 2; dismiss_16k
if ui_has "Choose your language"; then log "PASS A1 Language"; else log "FAIL A1 Language"; fi
tap_center 540 1100 1; dismiss_16k
if ui_has "Welcome to AzadiTunnel"; then log "PASS A2 Onboarding"; else log "FAIL A2 Onboarding"; fi
for _ in 1 2 3 4; do tap_center 540 2100 0.5; done
tap_center 540 2100 1; dismiss_16k
if ui_has "Not secured"; then log "PASS A3 Dashboard"; else log "FAIL A3 Dashboard"; fi

log "=== PHASE B: Connect + Disclaimer ==="
$ADB logcat -c
tap_center 540 1037 1
if ui_has "Before you connect"; then log "PASS B1 Disclaimer"; else log "FAIL B1 Disclaimer"; fi
"$SCRIPT_DIR/tap_text.sh" "I understand and agree" || tap_center 800 1900 0
sleep 35; dismiss_16k
B_PASS=0
ui_has "Secured" && B_PASS=1
$ADB logcat -d | rg -q "Tunnel connected|TUNNEL_CONNECTED" && B_PASS=$((B_PASS+1))
if [ "$B_PASS" -ge 1 ]; then log "PASS B2 Connected (signals=$B_PASS)"; else log "FAIL B2 Connected"; fi
$ADB logcat -d | rg "DISCLAIMER|VPN_CONNECT|FALLBACK|INTERNET_TEST|TUNNEL_CONNECTED|PSIPHON_TUNNEL|Tunnel connected" | tail -20 >> "$REPORT" || true
log "UI snapshot: $(ui_dump | rg -o 'text=\"[^\"]*\"' | rg -vi 'text=\"\"' | rg -i 'Secured|Protocol|ms|United|IP|Download|Error|Fallback' | tr '\n' ' ')"

log "=== PHASE C: Dashboard extras ==="
tap_center 200 1450 2
if ui_has "[0-9]+ms"; then log "PASS C1 Ping"; else log "WARN C1 Ping"; fi
$ADB shell input swipe 540 1500 540 300 350; sleep 0.5
"$SCRIPT_DIR/tap_text.sh" "Find Best Connection" || tap_center 270 1700 0
sleep 8
$ADB logcat -d | rg "BEST_CONN" | tail -6 >> "$REPORT" || true
if $ADB logcat -d | rg -q "BEST_CONN_STARTED"; then log "PASS C2 FindBest started"; else log "WARN C2 FindBest"; fi
tap_center 540 1037 4

log "=== PHASE D: Settings ==="
$ADB shell input tap 900 2200; sleep 1
if ui_has "TRANSPORT"; then log "PASS D1 Settings"; else log "FAIL D1 Settings"; fi
for label in "Bypass Iran IPs" "Secure DNS" "Proxy-only mode" "Share proxy on LAN" "Logs" "About" "Legal"; do
  $ADB shell input swipe 540 1500 540 500 300; sleep 0.3
  if "$SCRIPT_DIR/tap_text.sh" "$label" 2>/dev/null; then
    sleep 1; log "PASS D-open: $label"; $ADB shell input keyevent KEYCODE_BACK; sleep 0.6
  else
    log "WARN D-miss: $label"
  fi
done

log "=== PHASE E: Disconnect/Reconnect ==="
$ADB shell input tap 100 2200; sleep 1
$ADB logcat -c
tap_center 540 1037 6
if ui_has "Not secured"; then log "PASS E1 Disconnect"; else log "FAIL E1 Disconnect"; fi
$ADB logcat -d | rg "VPN_DISCONNECT|PSIPHON_STOPPED|TUNNEL_STOP" | tail -5 >> "$REPORT" || true
tap_center 540 1037 0
sleep 32
if ui_has "Secured"; then log "PASS E2 Reconnect"; else log "FAIL E2 Reconnect"; fi

log "=== PHASE F: Region picker ==="
$ADB shell input swipe 540 900 540 1400 300; sleep 0.5
tap_center 540 1250 1
if ui_has "Germany|United States|Canada"; then log "PASS F1 Region picker"; else log "WARN F1 Region picker"; fi

log "=== COUNTS ==="
rg "^(PASS|FAIL|WARN)" "$REPORT" | sed 's/:.*//' | sort | uniq -c | tee -a "$REPORT"
