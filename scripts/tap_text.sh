#!/bin/bash
# Usage: tap_text.sh "Button text"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
TEXT="$1"
$ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
BOUNDS=$($ADB shell cat /sdcard/ui.xml | tr '>' '\n' | rg "text=\"$TEXT\"" -m1 | rg -o 'bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"' || true)
if [ -z "$BOUNDS" ]; then
  BOUNDS=$($ADB shell cat /sdcard/ui.xml | tr '>' '\n' | rg -i "text=\"[^\"]*${TEXT}[^\"]*\"" -m1 | rg -o 'bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"' || true)
fi
if [ -z "$BOUNDS" ]; then echo "NOT_FOUND:$TEXT"; exit 1; fi
eval $(echo "$BOUNDS" | sed -E 's/bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/x1=\1 y1=\2 x2=\3 y2=\4/')
CX=$(( (x1 + x2) / 2 ))
CY=$(( (y1 + y2) / 2 ))
$ADB shell input tap "$CX" "$CY"
echo "TAPPED:$TEXT@$CX,$CY"
