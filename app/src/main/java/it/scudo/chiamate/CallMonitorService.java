package it.scudo.chiamate;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.provider.CallLog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoWcdma;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Servizio in primo piano: durante ogni chiamata controlla ogni 2 secondi
 * se altre app usano il microfono e se la rete scende al 2G o cambia cella.
 */
public class CallMonitorService extends Service {

    static final String PREFS = "scudo";
    static final String KEY_ENABLED = "protezione_attiva";
    static volatile boolean running = false;

    private static final String CH_STATUS = "stato";
    private static final String CH_ALERT = "allarmi";
    private static final int ID_STATUS = 1;
    private static final long POLL_MS = 2000;

    private TelephonyManager tm;
    private AudioManager am;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Object callbackRef; // TelephonyCallback (API 31+) oppure PhoneStateListener
    private boolean inCall = false;
    private long callStart;
    private int lastNetType = -1;
    private String lastCell = null;
    private int cellChanges = 0;
    private int alertsThisCall = 0;
    private final Set<String> seenRecorders = new HashSet<>();
    private int alertId = 100;
    private String lastRingingNumber = null;
    /** Chiamata in corso (o appena squillata): tutto finisce in un'unica voce del registro. */
    private CallStore.Call cur = null;
    private static final String CH_CALLER = "chiamante";
    private static final int ID_CALLER = 2;

    /** Il sistema invia il numero in arrivo solo alle app con permesso Registro chiamate. */
    private final BroadcastReceiver phoneState = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String state = i.getStringExtra(TelephonyManager.EXTRA_STATE);
            @SuppressWarnings("deprecation")
            String num = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) && num != null
                    && !num.equals(lastRingingNumber)) {
                lastRingingNumber = num;
                showCaller(num);
            } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                lastRingingNumber = null;
            }
        }
    };

    // ---- chiamate via internet (WhatsApp, Telegram, Messenger…)
    private boolean inVoip = false;
    private long voipStart;
    private int voipOwnerSession = -1;
    private Object modeListener;
    private final Runnable modePoll = new Runnable() {
        @Override public void run() {
            onAudioMode(am.getMode());
            handler.postDelayed(this, 3000);
        }
    };
    private final Runnable voipPoll = new Runnable() {
        @Override public void run() {
            if (!inVoip) return;
            checkVoipMicrophone();
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!inCall) return;
            checkMicrophone();
            checkNetwork();
            handler.postDelayed(this, POLL_MS);
        }
    };

    static void start(Context ctx) {
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply();
        Intent i = new Intent(ctx, CallMonitorService.class);
        ctx.startForegroundService(i);
    }

    static void stop(Context ctx) {
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply();
        ctx.stopService(new Intent(ctx, CallMonitorService.class));
    }

    static boolean isEnabled(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
        return p.getBoolean(KEY_ENABLED, false);
    }

    @Override public void onCreate() {
        super.onCreate();
        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        createChannels();
        Notification n = statusNotification("Protezione attiva: controllo le chiamate");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID_STATUS, n);
        }
        running = true;
        registerCallListener();
        startVoipWatch();
        IntentFilter f = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(phoneState, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(phoneState, f);
        EventLog.add(this, EventLog.INFO, "Protezione chiamate attivata");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacks(poll);
        handler.removeCallbacks(modePoll);
        handler.removeCallbacks(voipPoll);
        if (Build.VERSION.SDK_INT >= 31 && modeListener != null) {
            am.removeOnModeChangedListener((AudioManager.OnModeChangedListener) modeListener);
        }
        unregisterCallListener();
        try { unregisterReceiver(phoneState); } catch (Exception ignored) { }
        EventLog.add(this, EventLog.INFO, "Protezione chiamate disattivata");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    // ---------------------------------------------------------------- chiamate

    private void registerCallListener() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            EventLog.add(this, EventLog.WARN, "Permesso Telefono mancante: impossibile rilevare le chiamate");
            return;
        }
        if (Build.VERSION.SDK_INT >= 31) {
            CallCallback cb = new CallCallback();
            tm.registerTelephonyCallback(getMainExecutor(), cb);
            callbackRef = cb;
        } else {
            PhoneStateListener l = new PhoneStateListener() {
                @Override public void onCallStateChanged(int state, String phoneNumber) {
                    onCallState(state);
                }
            };
            tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE);
            callbackRef = l;
        }
    }

    private void unregisterCallListener() {
        if (callbackRef == null) return;
        if (Build.VERSION.SDK_INT >= 31 && callbackRef instanceof TelephonyCallback) {
            tm.unregisterTelephonyCallback((TelephonyCallback) callbackRef);
        } else if (callbackRef instanceof PhoneStateListener) {
            tm.listen((PhoneStateListener) callbackRef, PhoneStateListener.LISTEN_NONE);
        }
        callbackRef = null;
    }

    private class CallCallback extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override public void onCallStateChanged(int state) { onCallState(state); }
    }

    private void onCallState(int state) {
        if (state == TelephonyManager.CALL_STATE_RINGING) {
            if (cur == null) newCall(false);
        } else if (state == TelephonyManager.CALL_STATE_OFFHOOK && !inCall) {
            if (cur == null) newCall(false);
            inCall = true;
            callStart = System.currentTimeMillis();
            seenRecorders.clear();
            lastNetType = -1;
            lastCell = null;
            cellChanges = 0;
            alertsThisCall = 0;
            updateStatus("Chiamata in corso: controllo microfono e rete");
            handler.post(poll);
        } else if (state == TelephonyManager.CALL_STATE_IDLE) {
            if (inCall) {
                inCall = false;
                handler.removeCallbacks(poll);
                updateStatus("Protezione attiva: controllo le chiamate");
            }
            if (cur != null) {
                CallStore.Call done = cur;
                cur = null;
                done.end = System.currentTimeMillis();
                handler.postDelayed(() -> finishCall(done), 2500);
            }
        }
    }

    private void newCall(boolean voip) {
        recorderSince.clear();
        cur = new CallStore.Call();
        cur.start = System.currentTimeMillis();
        cur.voip = voip;
        if (!voip && lastRingingNumber != null) cur.number = lastRingingNumber;
    }

    /** Completa la voce con i dati del registro chiamate di Android (numero, direzione, durata) e la salva. */
    private void finishCall(CallStore.Call c) {
        if (!c.voip && checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            String[] cols = {CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DURATION,
                    CallLog.Calls.NUMBER_PRESENTATION, CallLog.Calls.DATE};
            try (Cursor q = getContentResolver().query(CallLog.Calls.CONTENT_URI, cols, null, null,
                    CallLog.Calls.DATE + " DESC")) {
                if (q != null && q.moveToFirst() && Math.abs(q.getLong(4) - c.start) < 120_000) {
                    c.number = q.getInt(3) == CallLog.Calls.PRESENTATION_ALLOWED ? q.getString(0) : null;
                    c.durationSec = q.getLong(2);
                    switch (q.getInt(1)) {
                        case CallLog.Calls.INCOMING_TYPE: c.dir = CallStore.IN; break;
                        case CallLog.Calls.OUTGOING_TYPE: c.dir = CallStore.OUT; break;
                        case CallLog.Calls.MISSED_TYPE: c.dir = CallStore.MISSED; break;
                        case CallLog.Calls.REJECTED_TYPE: c.dir = CallStore.REJECTED; break;
                        case CallLog.Calls.BLOCKED_TYPE: c.dir = CallStore.BLOCKED; break;
                        default: c.dir = CallStore.UNKNOWN;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (c.durationSec == 0 && c.end > c.start && !CallStore.MISSED.equals(c.dir)
                && !CallStore.REJECTED.equals(c.dir) && !CallStore.BLOCKED.equals(c.dir)) {
            c.durationSec = (c.end - c.start) / 1000;
        }
        CallStore.save(this, c);
    }

    // ------------------------------------------------------------- chiamante

    private void showCaller(String num) {
        if (cur != null && cur.number == null) cur.number = num;
        new Thread(() -> {
            String name = NumberInfo.contactName(this, num);
            PhoneHistory.Stats st = PhoneHistory.stats(this, num, System.currentTimeMillis() - 5000);
            String title = name != null ? name : "Numero non in rubrica: " + num;
            String text = NumberInfo.kind(num) + "\n" + st.summary();
            if (st.blocked > 0) text += "\nIn passato il telefono ha bloccato questo numero " + st.blocked + " volte.";
            Intent look = new Intent(this, LookupActivity.class)
                    .putExtra(LookupActivity.EXTRA_NUMBER, num)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent lookPi = PendingIntent.getActivity(this, 77, look,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(this, CH_CALLER)
                    .setContentIntent(lookPi)
                    .addAction(new Notification.Action.Builder(null, "Chi è questo numero?", lookPi).build())
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle(title)
                    .setContentText(st.summary())
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setTimeoutAfter(90_000)
                    .setAutoCancel(true)
                    .build();
            getSystemService(NotificationManager.class).notify(ID_CALLER, n);
        }).start();
    }

    // -------------------------------------------------------------- microfono

    /** Da quando ogni registratore è attivo: si segnala solo chi ascolta per almeno MIC_MIN_MS. */
    private final java.util.Map<String, Long> recorderSince = new java.util.HashMap<>();
    private static final long MIC_MIN_MS = 6000;

    private void checkMicrophone() {
        List<AudioRecordingConfiguration> cfgs = am.getActiveRecordingConfigurations();
        if (cfgs == null) return;
        long now = System.currentTimeMillis();
        java.util.Set<String> active = new java.util.HashSet<>();
        for (AudioRecordingConfiguration c : cfgs) {
            int src = c.getClientAudioSource();
            String key = c.getClientAudioSessionId() + ":" + src;
            active.add(key);
            if (seenRecorders.contains(key)) continue;
            boolean silenced = Build.VERSION.SDK_INT >= 29 && c.isClientSilenced();
            if (silenced) {
                // Android l'ha già bloccata: riceve solo silenzio. Lo annotiamo nella chiamata, senza allarme.
                seenRecorders.add(key);
                if (cur != null) cur.alerts.add(new String[]{EventLog.INFO,
                        "Un'app ha provato ad ascoltare ma Android l'ha bloccata: nessun rischio."});
                continue;
            }
            Long since = recorderSince.get(key);
            if (since == null) { recorderSince.put(key, now); continue; }
            if (now - since < MIC_MIN_MS) continue;
            seenRecorders.add(key);
            boolean callRec = src == MediaRecorder.AudioSource.VOICE_CALL
                    || src == MediaRecorder.AudioSource.VOICE_DOWNLINK
                    || src == MediaRecorder.AudioSource.VOICE_UPLINK;
            String at = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALY).format(new java.util.Date(since));
            alert(EventLog.ALERT,
                    callRec ? "Un'app sta REGISTRANDO la chiamata" : "Un'altra app ascolta dal microfono",
                    (callRec ? "Un'app sta registrando la chiamata, compresa la voce di chi ti parla."
                            : "Mentre eri al telefono un'app diversa dalla chiamata ha usato il microfono per più di 6 secondi e poteva sentirti.")
                            + " Per sapere quale: Impostazioni › Privacy › Uso autorizzazioni › Microfono, e guarda le app delle "
                            + at + ". Se è Bixby, \"Ok Google\" o la registrazione chiamate Samsung, è normale.");
        }
        recorderSince.keySet().retainAll(active);
    }

    private static String sourceName(int s) {
        switch (s) {
            case MediaRecorder.AudioSource.MIC: return "microfono";
            case MediaRecorder.AudioSource.VOICE_UPLINK: return "voce in uscita";
            case MediaRecorder.AudioSource.VOICE_DOWNLINK: return "voce in entrata";
            case MediaRecorder.AudioSource.VOICE_CALL: return "chiamata completa";
            case MediaRecorder.AudioSource.CAMCORDER: return "videocamera";
            case MediaRecorder.AudioSource.VOICE_RECOGNITION: return "riconoscimento vocale";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION: return "comunicazione vocale";
            case MediaRecorder.AudioSource.UNPROCESSED: return "audio grezzo";
            default: return "tipo " + s;
        }
    }

    // ------------------------------------------------------------------- rete

    @SuppressWarnings("deprecation")
    private void checkNetwork() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return;
        int net;
        try {
            net = tm.getVoiceNetworkType();
        } catch (SecurityException e) {
            return;
        }
        if (net != lastNetType) {
            String name = netName(net);
            if (is2G(net)) {
                alert(EventLog.ALERT, "Chiamata passata al 2G",
                        "Il telefono è sceso sulla rete " + name + ", che ha una protezione debole ed è quella usata dai"
                        + " falsi ripetitori. Spesso succede solo per scarsa copertura (cantine, campagna). Se ti capita"
                        + " spesso in posti dove di solito hai il 4G, disattiva il 2G: Consigli › Disattiva il 2G.");
            }
            if (cur != null) {
                if (cur.network == null) cur.network = name;
                if (cur.networks.isEmpty() || !cur.networks.get(cur.networks.size() - 1).equals(name)) cur.networks.add(name);
            }
            lastNetType = net;
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        String cell;
        try {
            cell = currentCell();
        } catch (SecurityException e) {
            return;
        }
        if (cell == null) return;
        if (lastCell != null && !cell.equals(lastCell)) {
            cellChanges++;
            if (cur != null) cur.cells.add(cell);
            if (cellChanges == 4) {
                alert(EventLog.WARN, "Molti cambi di cella",
                        "Durante questa chiamata il telefono ha cambiato ripetitore 4 volte. È normale se eri in"
                        + " movimento (auto, treno); se eri fermo può essere un segnale di falso ripetitore.");
            }
        } else if (lastCell == null) {
            if (cur != null) cur.cells.add(cell);
        }
        lastCell = cell;
    }

    private String currentCell() {
        List<CellInfo> all = tm.getAllCellInfo();
        if (all == null) return null;
        for (CellInfo ci : all) {
            if (!ci.isRegistered()) continue;
            if (ci instanceof CellInfoLte) {
                CellIdentityLte id = ((CellInfoLte) ci).getCellIdentity();
                return "4G cella " + id.getCi() + " area " + id.getTac();
            } else if (Build.VERSION.SDK_INT >= 29 && ci instanceof CellInfoNr) {
                CellIdentityNr id = (CellIdentityNr) ((CellInfoNr) ci).getCellIdentity();
                return "5G cella " + id.getNci() + " area " + id.getTac();
            } else if (ci instanceof CellInfoWcdma) {
                CellIdentityWcdma id = ((CellInfoWcdma) ci).getCellIdentity();
                return "3G cella " + id.getCid() + " area " + id.getLac();
            } else if (ci instanceof CellInfoGsm) {
                CellIdentityGsm id = ((CellInfoGsm) ci).getCellIdentity();
                return "2G cella " + id.getCid() + " area " + id.getLac();
            }
        }
        return null;
    }

    static boolean is2G(int t) {
        return t == TelephonyManager.NETWORK_TYPE_GSM
                || t == TelephonyManager.NETWORK_TYPE_GPRS
                || t == TelephonyManager.NETWORK_TYPE_EDGE
                || t == TelephonyManager.NETWORK_TYPE_CDMA
                || t == TelephonyManager.NETWORK_TYPE_1xRTT
                || t == TelephonyManager.NETWORK_TYPE_IDEN;
    }

    static String netName(int t) {
        switch (t) {
            case TelephonyManager.NETWORK_TYPE_GSM: return "2G GSM";
            case TelephonyManager.NETWORK_TYPE_GPRS: return "2G GPRS";
            case TelephonyManager.NETWORK_TYPE_EDGE: return "2G EDGE";
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT: return "2G CDMA";
            case TelephonyManager.NETWORK_TYPE_IDEN: return "2G iDEN";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA: return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE: return "4G (VoLTE)";
            case TelephonyManager.NETWORK_TYPE_IWLAN: return "Chiamate Wi-Fi";
            case TelephonyManager.NETWORK_TYPE_NR: return "5G";
            case TelephonyManager.NETWORK_TYPE_UNKNOWN: return "sconosciuta";
            default: return "tipo " + t;
        }
    }

    // ------------------------------------------------------------- notifiche

    private void alert(String level, String msg) {
        alert(level, EventLog.ALERT.equals(level) ? "Allarme durante la chiamata" : "Attenzione", msg);
    }

    private void alert(String level, String title, String msg) {
        alertsThisCall++;
        String full = title + ". " + msg;
        if (cur != null) cur.alerts.add(new String[]{level, full});
        EventLog.add(this, level, full, cur != null ? cur.number : null);
        NotificationManager nm = getSystemService(NotificationManager.class);
        Notification n = new Notification.Builder(this, CH_ALERT)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(msg)
                .setStyle(new Notification.BigTextStyle().bigText(msg))
                .setContentIntent(openApp(LogActivity.class))
                .setAutoCancel(true)
                .build();
        nm.notify(alertId++, n);
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_STATUS, "Stato protezione",
                NotificationManager.IMPORTANCE_LOW));
        NotificationChannel a = new NotificationChannel(CH_ALERT, "Allarmi intercettazione",
                NotificationManager.IMPORTANCE_HIGH);
        a.enableVibration(true);
        nm.createNotificationChannel(a);
        nm.createNotificationChannel(new NotificationChannel(CH_CALLER, "Chi chiama",
                NotificationManager.IMPORTANCE_DEFAULT));
    }

    private Notification statusNotification(String text) {
        return new Notification.Builder(this, CH_STATUS)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(openApp(MainActivity.class))
                .build();
    }

    private void updateStatus(String text) {
        getSystemService(NotificationManager.class).notify(ID_STATUS, statusNotification(text));
    }

    private PendingIntent openApp(Class<?> cls) {
        Intent i = new Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, cls.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ------------------------------------------------------- chiamate via internet

    private void startVoipWatch() {
        if (Build.VERSION.SDK_INT >= 31) {
            AudioManager.OnModeChangedListener l = this::onAudioMode;
            am.addOnModeChangedListener(getMainExecutor(), l);
            modeListener = l;
            onAudioMode(am.getMode());
        } else {
            handler.post(modePoll);
        }
    }

    /** MODE_IN_COMMUNICATION = chiamata via internet; MODE_IN_CALL = chiamata normale (già gestita). */
    private void onAudioMode(int mode) {
        if (mode == AudioManager.MODE_IN_COMMUNICATION && !inVoip && !inCall) {
            inVoip = true;
            voipStart = System.currentTimeMillis();
            voipOwnerSession = -1;
            seenRecorders.clear();
            alertsThisCall = 0;
            newCall(true);
            cur.dir = CallStore.UNKNOWN;
            updateStatus("Chiamata WhatsApp/internet: controllo il microfono");
            handler.postDelayed(voipPoll, 1500);
        } else if (mode != AudioManager.MODE_IN_COMMUNICATION && inVoip) {
            inVoip = false;
            handler.removeCallbacks(voipPoll);
            if (cur != null && cur.voip) {
                cur.end = System.currentTimeMillis();
                cur.durationSec = (cur.end - voipStart) / 1000;
                CallStore.save(this, cur);
                cur = null;
            }
            updateStatus("Protezione attiva: controllo le chiamate");
        }
    }

    /** Il primo registratore è l'app che sta chiamando; ogni altro è un'app che ascolta in più. */
    private void checkVoipMicrophone() {
        List<AudioRecordingConfiguration> cfgs = am.getActiveRecordingConfigurations();
        if (cfgs == null || cfgs.isEmpty()) return;
        if (voipOwnerSession == -1) voipOwnerSession = cfgs.get(0).getClientAudioSessionId();
        long now = System.currentTimeMillis();
        java.util.Set<String> active = new java.util.HashSet<>();
        for (AudioRecordingConfiguration c : cfgs) {
            int session = c.getClientAudioSessionId();
            if (session == voipOwnerSession) continue;
            String key = "voip:" + session;
            active.add(key);
            if (seenRecorders.contains(key)) continue;
            if (Build.VERSION.SDK_INT >= 29 && c.isClientSilenced()) {
                seenRecorders.add(key);
                if (cur != null) cur.alerts.add(new String[]{EventLog.INFO,
                        "Un'app ha provato ad ascoltare ma Android l'ha bloccata: nessun rischio."});
                continue;
            }
            Long since = recorderSince.get(key);
            if (since == null) { recorderSince.put(key, now); continue; }
            if (now - since < MIC_MIN_MS) continue;
            seenRecorders.add(key);
            String at = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALY).format(new java.util.Date(since));
            alert(EventLog.ALERT, "Un'altra app ascolta la chiamata WhatsApp",
                    "Durante la chiamata via internet un'app diversa da quella che chiama ha usato il microfono per più"
                            + " di 6 secondi. Per sapere quale: Impostazioni › Privacy › Uso autorizzazioni › Microfono,"
                            + " app delle " + at + ".");
        }
        recorderSince.keySet().retainAll(active);
    }
}
