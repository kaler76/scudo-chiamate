#!/bin/bash
# Test automatico: installa l'app su un telefono virtuale, dà i permessi e apre ogni schermata.
P=it.scudo.chiamate
adb install -r ScudoChiamate.apk
for perm in READ_PHONE_STATE ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS READ_CALL_LOG READ_CONTACTS CALL_PHONE; do
  adb shell pm grant $P android.permission.$perm || true
done
adb shell appops set $P GET_USAGE_STATS allow || true
adb shell appops set $P android:get_usage_stats allow || true
adb logcat -c
adb shell am start -W -n $P/.MainActivity
sleep 6
for t in app sicurezza whatsapp traffico registro consigli deviazioni chi pdf protezione; do
  echo "=== TEST $t"
  adb shell am force-stop $P; sleep 1
  adb shell am start -W -n $P/.MainActivity --es test $t
  sleep 12
  adb shell input keyevent KEYCODE_BACK
  sleep 2
done
echo "=== CHIAMATA A PROTEZIONE SPENTA"
adb emu gsm call 3331234567; sleep 5
adb emu gsm cancel 3331234567; sleep 4
echo "=== CHIAMATA SIMULATA"
adb shell am force-stop $P; sleep 1
adb shell am start -W -n $P/.MainActivity --es test protezione
sleep 6
adb emu gsm call 3331234567; sleep 6
adb emu gsm accept 3331234567; sleep 12
adb emu gsm cancel 3331234567; sleep 8
adb emu gsm call 0541999888; sleep 6
adb emu gsm cancel 0541999888; sleep 8
adb emu gsm call 3331234567; sleep 5
adb emu gsm cancel 3331234567; sleep 8
adb shell run-as $P cat files/chiamate.jsonl || echo "nessuna chiamata registrata"
adb shell am force-stop $P; sleep 1; adb shell am start -W -n $P/.MainActivity --es test elenco
sleep 5
echo "=== ELENCO UNITO"
adb shell run-as $P cat files/test_elenco.txt || true
echo "=== REGISTRO CON MOLTE CHIAMATE"
NOW=$(($(date +%s)*1000))
for i in $(seq 1 400); do
  adb shell content insert --uri content://call_log/calls --bind number:s:33300$((i % 120)) --bind type:i:$((i % 3 + 1)) --bind duration:l:$((i % 90)) --bind date:l:$((NOW - i * 3600000)) >/dev/null 2>&1
done
adb shell am force-stop $P; sleep 1
adb shell am start -W -n $P/.MainActivity --es test registro
sleep 15
adb shell dumpsys activity processes | grep -i "anr\|not responding" | head -5 || true
adb logcat -d | grep -i "ANR in it.scudo" | head -5 || echo "nessun blocco (ANR)"
echo "=== RIAVVIO DOPO AGGIORNAMENTO"
adb install -r ScudoChiamate.apk
sleep 8
adb shell dumpsys activity services $P | grep -c CallMonitorService || true
adb shell am start -W -n $P/.MainActivity --es test registro
sleep 6
adb shell am start -W -n $P/.MainActivity --es test pdf
sleep 15
echo "=== FILE PDF"
adb shell ls -la /sdcard/Download/ || true
echo "=== TEST completa"
adb shell am start -W -n $P/.MainActivity --es test completa
sleep 70
echo "=== CRASH (AndroidRuntime)"
adb logcat -d -s AndroidRuntime:E | tail -120
echo "=== FILE ERRORE"
adb shell run-as $P cat files/ultimo_errore.txt || echo "nessun file errore"
echo "=== REGISTRO APP"
adb shell run-as $P cat files/eventi.log | tail -40 || true
echo "=== FINE"
