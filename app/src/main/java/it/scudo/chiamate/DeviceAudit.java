package it.scudo.chiamate;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.ProxyInfo;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Controllo generale della sicurezza del telefono, con punteggio 0-100. */
final class DeviceAudit {

    static final class Check {
        String title, detail;
        int state;     // 0 ok, 1 attenzione, 2 problema
        int weight;
        Intent fix;
    }

    private DeviceAudit() {}

    static int score(List<Check> checks) {
        int s = 100;
        for (Check c : checks) {
            if (c.state == 2) s -= c.weight;
            else if (c.state == 1) s -= c.weight / 2;
        }
        return Math.max(0, s);
    }

    private static Check add(List<Check> l, String title, int state, int weight, String detail, String action) {
        Check c = new Check();
        c.title = title; c.state = state; c.weight = weight; c.detail = detail;
        if (action != null) c.fix = new Intent(action);
        l.add(c);
        return c;
    }

    static List<Check> run(Context ctx) {
        List<Check> l = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();

        // 1. Root / sistema modificato
        boolean root = Build.TAGS != null && Build.TAGS.contains("test-keys");
        for (String p : new String[]{"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
                "/data/local/xbin/su", "/data/local/bin/su", "/system/app/Superuser.apk"}) {
            if (new File(p).exists()) root = true;
        }
        if (installed(pm, "com.topjohnwu.magisk") || installed(pm, "eu.chainfire.supersu")) root = true;
        add(l, "Sistema non modificato (root)", root ? 2 : 0, 25,
                root ? "Il telefono risulta modificato (root): uno spyware può nascondersi e leggere tutto."
                        : "Nessun segno di root o sistema modificato.", null);

        // 2. Blocco schermo
        KeyguardManager km = ctx.getSystemService(KeyguardManager.class);
        boolean secure = km != null && km.isDeviceSecure();
        add(l, "Blocco schermo", secure ? 0 : 2, 15,
                secure ? "PIN, sequenza o password attivi." : "Nessun blocco: chiunque può installare un'app spia in pochi secondi.",
                Settings.ACTION_SECURITY_SETTINGS);

        // 3. Certificati installati dall'utente (intercettazione del traffico)
        List<String> userCa = userCertificates();
        add(l, "Certificati di sicurezza aggiunti", userCa.isEmpty() ? 0 : 2, 15,
                userCa.isEmpty() ? "Nessun certificato aggiunto: il traffico cifrato non può essere letto da terzi."
                        : "Certificati aggiunti a mano: " + String.join(", ", userCa)
                        + ". Permettono di leggere il traffico cifrato. Se non li hai messi tu (o la tua azienda), rimuovili.",
                Settings.ACTION_SECURITY_SETTINGS);

        // 4. Proxy
        String proxy = proxy(ctx);
        add(l, "Proxy di rete", proxy == null ? 0 : 2, 15,
                proxy == null ? "Nessun proxy: il traffico non passa da server intermedi."
                        : "Il traffico passa dal proxy " + proxy + ". Se non l'hai impostato tu, toglilo dalle impostazioni Wi-Fi.",
                Settings.ACTION_WIFI_SETTINGS);

        // 5. VPN
        boolean vpn = vpnActive(ctx);
        add(l, "VPN", vpn ? 1 : 0, 10,
                vpn ? "C'è una VPN attiva: tutto il traffico passa da lei. Va bene solo se l'hai attivata tu."
                        : "Nessuna VPN attiva.", Settings.ACTION_VPN_SETTINGS);

        // 6. Debug USB e opzioni sviluppatore
        boolean adb = Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        boolean dev = Settings.Global.getInt(ctx.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        add(l, "Debug USB", adb ? 2 : 0, 15,
                adb ? "Attivo: con un cavo si possono installare app e leggere dati senza toccare lo schermo."
                        : "Disattivato.", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        add(l, "Opzioni sviluppatore", dev && !adb ? 1 : 0, 4,
                dev ? "Attive. Se non le usi, spegnile." : "Disattivate.",
                Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);

        // 7. Servizi speciali concessi ad app non di sistema
        List<String> acc = nonSystemLabels(ctx, AppScanner.pkgsFromSetting(Settings.Secure.getString(
                ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)));
        add(l, "Servizi di accessibilità", acc.isEmpty() ? 0 : 1, 10,
                acc.isEmpty() ? "Nessuna app esterna può leggere lo schermo."
                        : "Possono leggere lo schermo e premere al posto tuo: " + String.join(", ", acc),
                Settings.ACTION_ACCESSIBILITY_SETTINGS);

        List<String> notif = nonSystemLabels(ctx, AppScanner.pkgsFromSetting(Settings.Secure.getString(
                ctx.getContentResolver(), "enabled_notification_listeners")));
        add(l, "Lettura notifiche", notif.isEmpty() ? 0 : 1, 6,
                notif.isEmpty() ? "Nessuna app esterna legge le notifiche."
                        : "Leggono tutte le notifiche (anche messaggi WhatsApp): " + String.join(", ", notif),
                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");

        DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
        List<String> adminPk = new ArrayList<>();
        if (dpm != null && dpm.getActiveAdmins() != null) {
            for (ComponentName cn : dpm.getActiveAdmins()) adminPk.add(cn.getPackageName());
        }
        List<String> admins = nonSystemLabels(ctx, new java.util.HashSet<>(adminPk));
        add(l, "Amministratori del dispositivo", admins.isEmpty() ? 0 : 1, 10,
                admins.isEmpty() ? "Nessuna app esterna ha i poteri di amministratore."
                        : "Hanno poteri di amministratore: " + String.join(", ", admins),
                Settings.ACTION_SECURITY_SETTINGS);

        // 8. Aggiornamenti di sicurezza
        int months = patchAgeMonths();
        add(l, "Aggiornamenti di sicurezza", months < 0 ? 1 : months > 12 ? 2 : months > 4 ? 1 : 0, 10,
                months < 0 ? "Data della patch non disponibile."
                        : "Ultima patch: " + Build.VERSION.SECURITY_PATCH + " (" + months + " mesi fa)."
                        + (months > 4 ? " Aggiorna il telefono: le patch chiudono le falle usate dagli spyware." : ""),
                "android.settings.SYSTEM_UPDATE_SETTINGS");

        // 9. Crittografia
        int enc = dpm != null ? dpm.getStorageEncryptionStatus() : 0;
        boolean encrypted = enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE
                || enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
                || enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY;
        add(l, "Memoria cifrata", encrypted ? 0 : 1, 5,
                encrypted ? "I dati sono cifrati." : "La memoria non risulta cifrata.", null);

        return l;
    }

    private static boolean installed(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> userCertificates() {
        List<String> out = new ArrayList<>();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidCAStore");
            ks.load(null);
            Enumeration<String> al = ks.aliases();
            while (al.hasMoreElements()) {
                String a = al.nextElement();
                if (!a.startsWith("user:")) continue;
                X509Certificate c = (X509Certificate) ks.getCertificate(a);
                String dn = c.getSubjectX500Principal().getName();
                String cn = dn;
                for (String part : dn.split(",")) if (part.trim().startsWith("CN=")) cn = part.trim().substring(3);
                out.add(cn);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String proxy(Context ctx) {
        try {
            ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
            ProxyInfo p = cm.getDefaultProxy();
            if (p == null) {
                Network n = cm.getActiveNetwork();
                LinkProperties lp = n != null ? cm.getLinkProperties(n) : null;
                p = lp != null ? lp.getHttpProxy() : null;
            }
            if (p != null && p.getHost() != null && !p.getHost().isEmpty()) return p.getHost() + ":" + p.getPort();
            if (p != null && p.getPacFileUrl() != null && !isEmptyPac(p)) return p.getPacFileUrl().toString();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isEmptyPac(ProxyInfo p) {
        return android.net.Uri.EMPTY.equals(p.getPacFileUrl());
    }

    private static boolean vpnActive(Context ctx) {
        try {
            ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static List<String> nonSystemLabels(Context ctx, Set<String> pkgs) {
        List<String> out = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();
        for (String p : pkgs) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(p, 0);
                if ((ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
                out.add(String.valueOf(ai.loadLabel(pm)));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static int patchAgeMonths() {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(Build.VERSION.SECURITY_PATCH);
            if (d == null) return -1;
            long days = (System.currentTimeMillis() - d.getTime()) / 86_400_000L;
            return (int) Math.max(0, days / 30);
        } catch (Exception e) {
            return -1;
        }
    }
}
