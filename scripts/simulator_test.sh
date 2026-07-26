#!/bin/bash
set -euo pipefail
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
PKG="com.azaditunnel.vpn"
ACTIVITY="com.psiphon3.MainActivity"
LOG="/tmp/azadi_sim_test.log"
RESULT="/tmp/azadi_sim_test_results.txt"

: > "$RESULT"
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$RESULT"; }
tap() { $ADB shell input tap "$1" "$2"; sleep "$3"; }
ui_text() { $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $ADB shell cat /sdcard/ui.xml | rg -o 'text="[^"]*"' | rg -v 'text=""' | sort -u; }
wait_text() {
  local t="$1" timeout="$2" i=0
  while [ $i -lt "$timeout" ]; do
    if ui_text | rg -q "$t"; then return 0; fi
    sleep 1; i=$((i+1))
  done
  return 1
}

$ADB logcat -c
$ADB shell pm clear "$PKG" >/dev/null
log "=== TEST 1: Fresh launch + language + onboarding ==="
$ADB shell am start -n "$PKG/$ACTIVITY" >/dev/null
sleep 2
if wait_text "Choose your language" 8; then log "PASS: Language screen"; else log "FAIL: Language screen"; fi
tap 540 1100 1   # English
if wait_text "Welcome to AzadiTunnel" 5; then log "PASS: Onboarding page 1"; else log "FAIL: Onboarding page 1"; fi
for i in 1 2 3 4; do tap 540 2100 1; done  # Next x4
tap 540 2100 1   # Get started
sleep 1
if wait_text "AzadiTunnel" 5 && wait_text "Not secured" 5; then log "PASS: Dashboard after onboarding"; else log "FAIL: Dashboard"; fi

log "=== TEST 2: Disclaimer + Connect ==="
tap 540 1037 1   # Connect
sleep 1
if wait_text "Before you connect" 5; then log "PASS: Disclaimer shown"; else log "FAIL: Disclaimer"; fi
tap 800 1900 1    # I agree (right button)
sleep 25
if ui_text | rg -q "Secured"; then log "PASS: Connected status"; else log "FAIL: Connected status"; fi
$ADB logcat -d | rg -q "TUNNEL_CONNECTED|Tunnel connected" && log "PASS: Logcat tunnel connected" || log "FAIL: Logcat tunnel"
$ADB logcat -d | rg -q "INTERNET_TEST_PASSED" && log "PASS: Internet test" || log "WARN: Internet test log missing"
ui_text | rg -i "FRONTED|auto|direct|cdn|conduit|Protocol" | head -3 | tee -a "$RESULT"

log "=== TEST 3: Ping + Diagnostics UI ==="
tap 270 1450 1   # Ping tile area
sleep 3
ui_text | rg -i "ms|Diagnostics|Internet" | head -5 | tee -a "$RESULT"
log "INFO: Ping/diagnostics UI captured above"

log "=== TEST 4: Settings tab ==="
tap 900 2200 1   # Options tab
sleep 1
if wait_text "Transport" 5 || wait_text "Connection" 5; then log "PASS: Settings screen"; else log "FAIL: Settings"; fi
ui_text | head -20 | tee -a "$RESULT"

log "=== TEST 5: Logs screen ==="
tap 540 1200 1   # try logs row - may need scroll
sleep 1
# Navigate via settings logs if visible
if ui_text | rg -q "Logs"; then
  # tap Logs text approximate
  tap 200 900 1
  sleep 1
fi
$ADB shell input swipe 540 1500 540 500 300
sleep 1
tap 200 1100 1
sleep 1
if ui_text | rg -qi "Events|Tunnel|VPN_CONNECT"; then log "PASS: Logs screen"; else log "WARN: Logs screen uncertain"; fi

log "=== TEST 6: Disconnect ==="
$ADB shell input keyevent KEYCODE_BACK
sleep 1
$ADB shell input keyevent KEYCODE_BACK
sleep 1
tap 100 2200 1   # VPN tab
sleep 1
tap 540 1037 2   # Disconnect
sleep 5
if wait_text "Not secured" 8; then log "PASS: Disconnected"; else log "FAIL: Disconnect"; fi
$ADB logcat -d | rg -q "VPN_DISCONNECT_REQUESTED|TUNNEL_STOP" && log "PASS: Disconnect logs" || log "WARN: Disconnect logs"

log "=== TEST 7: Reconnect without disclaimer ==="
tap 540 1037 1
sleep 20
if ui_text | rg -q "Secured"; then log "PASS: Reconnect without disclaimer"; else log "FAIL: Reconnect"; fi

log "=== TEST 8: Find Best Connection (start only) ==="
$ADB shell input swipe 540 1500 540 300 400
sleep 1
tap 270 1700 1   # Find Best button area
sleep 8
ui_text | rg -i "Scanning|Find Best|Direct|cdn" | head -5 | tee -a "$RESULT"
$ADB logcat -d | rg "BEST_CONN" | tail -5 | tee -a "$RESULT"
$ADB logcat -d | rg -q "BEST_CONN_STARTED" && log "PASS: Find Best started" || log "WARN: Find Best not confirmed"

log "=== TEST 9: Region picker ==="
$ADB shell input keyevent KEYCODE_BACK
sleep 1
tap 540 1200 1   # region card
sleep 1
if ui_text | rg -q "United States|Germany|Any"; then log "PASS: Region picker"; else log "WARN: Region picker"; fi
tap 540 400 1    # dismiss
sleep 1

log "=== TEST 10: Proxy Only Wi-Fi check (settings) ==="
tap 900 2200 1
sleep 1
$ADB shell input swipe 540 1800 540 400 500
sleep 1
tap 300 1000 1   # Proxy-only nav row approx
sleep 1
if ui_text | rg -qi "proxy"; then log "PASS: Proxy Only screen"; else log "WARN: Proxy Only screen"; fi

log "=== DONE ==="
cat "$RESULT"
