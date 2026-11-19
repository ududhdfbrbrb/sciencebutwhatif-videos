#!/bin/bash
# Installs the APK on the emulator, opens it, and records whether it stays alive.
cd "$(dirname "$0")"
APK=app/build/outputs/apk/release/app-release.apk
OUT=smoke.txt
adb install -r "$APK" > $OUT 2>&1
adb logcat -c
adb shell am start -W -n com.sbwi.autoupload/.MainActivity >> $OUT 2>&1
sleep 10
# tap each bottom tab
W=$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1); WW=${W%x*}; HH=${W#*x}
for i in 1 3 5 7 9; do adb shell input tap $((WW*i/10)) $((HH-HH/40)); sleep 2; done
sleep 3
PID=$(adb shell pidof com.sbwi.autoupload)
if [ -n "$PID" ]; then echo "RESULT: ALIVE pid=$PID" >> $OUT; else echo "RESULT: DEAD" >> $OUT; fi
adb exec-out screencap -p > smoke.png
echo "---- crash log ----" >> $OUT
adb logcat -d -b crash >> $OUT 2>&1
adb logcat -d | grep -E "AndroidRuntime|com.sbwi|FATAL" | tail -80 >> $OUT
exit 0
