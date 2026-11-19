package it.scudo.chiamate;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
        EventLog.add(this, EventLog.INFO, "Protezione chiamate attivata");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacks(poll);
        unregisterCallListener();
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
            EventLog.add(this, EventLog.INFO, "Chiamata in arrivo");
        } else if (state == TelephonyManager.CALL_STATE_OFFHOOK && !inCall) {
            inCall = true;
            callStart = System.currentTimeMillis();
            seenRecorders.clear();
            lastNetType = -1;
            lastCell = null;
            cellChanges = 0;
            alertsThisCall = 0;
            EventLog.add(this, EventLog.INFO, "Chiamata iniziata: avvio controlli");
            updateStatus("Chiamata in corso: controllo microfono e rete");
            handler.post(poll);
        } else if (state == TelephonyManager.CALL_STATE_IDLE && inCall) {
            inCall = false;
            handler.removeCallbacks(poll);
            long secs = (System.currentTimeMillis() - callStart) / 1000;
            String esito = alertsThisCall == 0
                    ? "nessun segnale sospetto"
                    : alertsThisCall + " segnalazioni (vedi sopra)";
            EventLog.add(this, alertsThisCall == 0 ? EventLog.INFO : EventLog.WARN,
                    "Chiamata terminata (" + secs + " s, cambi di cella: " + cellChanges + "): " + esito);
            updateStatus("Protezione attiva: controllo le chiamate");
        }
    }

    // -------------------------------------------------------------- microfono

    private void checkMicrophone() {
        List<AudioRecordingConfiguration> cfgs = am.getActiveRecordingConfigurations();
        if (cfgs == null) return;
        for (AudioRecordingConfiguration c : cfgs) {
            int src = c.getClientAudioSource();
            String key = c.getClientAudioSessionId() + ":" + src;
            if (seenRecorders.contains(key)) continue;
            seenRecorders.add(key);

            boolean silenced = Build.VERSION.SDK_INT >= 29 && c.isClientSilenced();
            String srcName = sourceName(src);
            String msg;
            if (src == MediaRecorder.AudioSource.VOICE_CALL
                    || src == MediaRecorder.AudioSource.VOICE_DOWNLINK
                    || src == MediaRecorder.AudioSource.VOICE_UPLINK) {
                msg = "Un'app sta cercando di REGISTRARE LA CHIAMATA (sorgente: " + srcName + ")";
            } else {
                msg = "Un'altra app sta usando il microfono durante la chiamata (sorgente: " + srcName + ")";
            }
            if (silenced) msg += ". Android la sta bloccando (audio silenziato)";
            msg += ". Se hai attivato la registrazione chiamate Samsung o un assistente vocale, può essere normale.";
            alert(silenced ? EventLog.WARN : EventLog.ALERT, msg);
        }
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
                alert(EventLog.ALERT, "La chiamata è passata su rete 2G (" + name + "). Il 2G ha una cifratura debole"
                        + " ed è la rete usata dai falsi ripetitori (IMSI catcher). Può succedere anche per"
                        + " semplice scarsa copertura: è un indizio, non una prova.");
            } else if (lastNetType != -1) {
                EventLog.add(this, EventLog.INFO, "Rete della chiamata cambiata: " + name);
            } else {
                EventLog.add(this, EventLog.INFO, "Rete della chiamata: " + name);
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
            EventLog.add(this, EventLog.INFO, "Cambio cella durante la chiamata: " + cell);
            if (cellChanges == 4) {
                alert(EventLog.WARN, "Molti cambi di cella in questa chiamata. Normale se sei in movimento,"
                        + " sospetto se sei fermo.");
            }
        } else if (lastCell == null) {
            EventLog.add(this, EventLog.INFO, "Cella iniziale: " + cell);
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
        alertsThisCall++;
        EventLog.add(this, level, msg);
        NotificationManager nm = getSystemService(NotificationManager.class);
        Notification n = new Notification.Builder(this, CH_ALERT)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(EventLog.ALERT.equals(level) ? "Possibile intercettazione" : "Attenzione")
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
}
