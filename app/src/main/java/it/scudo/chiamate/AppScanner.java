package it.scudo.chiamate;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Analizza le app installate:
 * 1) confronto con il database di spyware noti (nome pacchetto, certificato di firma, impronta APK);
 * 2) punteggio euristico (permessi, servizi speciali, icona nascosta, provenienza, nome).
 */
final class AppScanner {

    static final class Result {
        String label;
        String pkg;
        int score;
        String ioc;          // famiglia di spyware riconosciuta dal database, se trovata
        String iocHow;       // come è stata riconosciuta
        final List<String> reasons = new ArrayList<>();

        String level() {
            if (ioc != null) return "CRITICO";
            if (score >= 9) return "ALTO";
            if (score >= 5) return "MEDIO";
            return "BASSO";
        }
    }

    interface Progress { void on(int done, int total); }

    private static final String[] STRONG_NAMES = {
            "spy", "stalk", "mspy", "flexispy", "hoverwatch", "cocospy", "spyzie", "xnspy",
            "eyezy", "umobix", "truthspy", "kidsguard", "clevguard", "spyic", "minspy",
            "spyera", "highster", "ikeymonitor", "mobistealth", "catwatchful", "spyhide",
            "trackview", "mobile-tracker", "mobiletracker", "thewispy", "spymaster", "fonemonitor"
    };
    private static final String[] WEAK_NAMES = {"monitor", "tracker", "stealth", "hidden", "nascost"};
    private static final String[] DISGUISE_NAMES = {
            "assistenza", "aggiornament", "update", "servizi", "service", "operatore", "sicurezza",
            "security", "sim ", "rete", "network", "sistema", "system", "carrier", "support"
    };

    private static final Set<String> TRUSTED_STORES = new HashSet<>();
    static {
        TRUSTED_STORES.add("com.android.vending");
        TRUSTED_STORES.add("com.sec.android.app.samsungapps");
        TRUSTED_STORES.add("com.samsung.android.app.omcagent");
        TRUSTED_STORES.add("com.google.android.feedback");
    }

    private static final long MAX_HASH_BYTES = 80L * 1024 * 1024;

    private AppScanner() {}

    @SuppressWarnings("deprecation")
    static List<Result> scan(Context ctx, Progress progress) {
        PackageManager pm = ctx.getPackageManager();
        ContentResolver cr = ctx.getContentResolver();
        IocDb db = IocDb.get(ctx);

        Set<String> accessibility = pkgsFromSetting(
                Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        Set<String> notifListeners = pkgsFromSetting(
                Settings.Secure.getString(cr, "enabled_notification_listeners"));
        Set<String> admins = new HashSet<>();
        DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) {
            List<ComponentName> list = dpm.getActiveAdmins();
            if (list != null) for (ComponentName cn : list) admins.add(cn.getPackageName());
        }

        int flags = PackageManager.GET_PERMISSIONS
                | (Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
        List<PackageInfo> pkgs = pm.getInstalledPackages(flags);
        List<Result> results = new ArrayList<>();
        int done = 0;
        for (PackageInfo pi : pkgs) {
            done++;
            if (progress != null && done % 10 == 0) progress.on(done, pkgs.size());
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null || pi.packageName.equals(ctx.getPackageName())) continue;

            Result r = new Result();
            r.pkg = pi.packageName;
            r.label = String.valueOf(ai.loadLabel(pm));

            // ---------- 1) Database spyware noti (anche per le app di sistema)
            String fam = db.packages.get(r.pkg);
            if (fam != null) { r.ioc = fam; r.iocHow = "nome del pacchetto"; }
            boolean system = (ai.flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (r.ioc == null && !system) {
                for (Signature s : signatures(pi)) {
                    byte[] der = s.toByteArray();
                    String f1 = db.certSha1.get(hex(digest("SHA-1", der)));
                    String f2 = db.certSha256.get(hex(digest("SHA-256", der)));
                    if (f1 != null || f2 != null) {
                        r.ioc = f1 != null ? f1 : f2;
                        r.iocHow = "certificato di firma";
                        break;
                    }
                }
            }

            String installer = installerOf(pm, r.pkg);
            boolean fromStore = installer != null && TRUSTED_STORES.contains(installer);

            if (r.ioc == null && !system && !fromStore && ai.sourceDir != null) {
                File apk = new File(ai.sourceDir);
                if (apk.length() > 0 && apk.length() < MAX_HASH_BYTES) {
                    String h = fileSha256(apk);
                    if (h != null && db.apkSha256.containsKey(h)) {
                        r.ioc = db.apkSha256.get(h);
                        r.iocHow = "impronta del file APK";
                    }
                }
            }

            if (r.ioc != null) {
                r.score = 100;
                r.reasons.add("RICONOSCIUTA nel database spyware: " + r.ioc + " (tramite " + r.iocHow + ")");
                results.add(r);
                continue;
            }
            // Le app di sistema non riconosciute si saltano: un'app spia non può diventare
            // app di sistema senza modificare il telefono (root).
            if (system) continue;

            // ---------- 2) Punteggio euristico
            Set<String> granted = grantedPermissions(pi);
            boolean mic = granted.contains("android.permission.RECORD_AUDIO");
            boolean callLog = granted.contains("android.permission.READ_CALL_LOG")
                    || granted.contains("android.permission.PROCESS_OUTGOING_CALLS");
            boolean phone = granted.contains("android.permission.READ_PHONE_STATE");
            boolean sms = granted.contains("android.permission.READ_SMS")
                    || granted.contains("android.permission.RECEIVE_SMS");
            boolean bgLocation = granted.contains("android.permission.ACCESS_BACKGROUND_LOCATION");
            boolean location = granted.contains("android.permission.ACCESS_FINE_LOCATION");
            boolean contacts = granted.contains("android.permission.READ_CONTACTS");
            boolean boot = granted.contains("android.permission.RECEIVE_BOOT_COMPLETED");
            boolean overlay = granted.contains("android.permission.SYSTEM_ALERT_WINDOW");
            boolean hidden = pm.getLaunchIntentForPackage(r.pkg) == null;
            String hay = (r.pkg + " " + r.label).toLowerCase(Locale.ROOT);

            int w = fromStore ? 0 : 1;
            if (mic) add(r, 3 * w, "Può usare il microfono");
            if (callLog) add(r, 3 * w, "Può leggere il registro chiamate");
            if (phone) add(r, w, "Può sapere quando sei in chiamata");
            if (sms) add(r, 2 * w, "Può leggere gli SMS");
            if (bgLocation) add(r, 2 * w, "Posizione anche in background");
            else if (location) add(r, w, "Può conoscere la posizione");
            if (contacts) add(r, w, "Può leggere i contatti");
            if (boot) add(r, w, "Si avvia da sola all'accensione");
            if (overlay) add(r, w, "Può disegnare sopra le altre app");

            if (accessibility.contains(r.pkg)) add(r, fromStore ? 2 : 5, "Servizio di ACCESSIBILITÀ attivo (può leggere lo schermo)");
            if (admins.contains(r.pkg)) add(r, fromStore ? 2 : 4, "È AMMINISTRATORE del dispositivo");
            if (notifListeners.contains(r.pkg)) add(r, fromStore ? 1 : 3, "Legge tutte le notifiche");
            if (hidden) add(r, 3, "Nessuna icona nel menu app (nascosta)");
            if (!fromStore) {
                add(r, 2, "Non installata da Play Store o Galaxy Store"
                        + (installer == null ? "" : " (" + installer + ")"));
            }
            for (String k : STRONG_NAMES) {
                if (hay.contains(k)) { add(r, 7, "Nome simile a un'app spia nota (\"" + k + "\")"); break; }
            }
            for (String k : WEAK_NAMES) {
                if (hay.contains(k)) { add(r, 2, "Nome sospetto (\"" + k + "\")"); break; }
            }
            if (mic && callLog && boot) {
                if (fromStore) add(r, 4, "Microfono + registro chiamate + avvio automatico: combinazione tipica dei trojan");
                for (String k : DISGUISE_NAMES) {
                    if (hay.contains(k)) { add(r, 3, "Nome da finta app di servizio (\"" + k.trim() + "\")"); break; }
                }
            }
            if (!fromStore && mic && (callLog || phone) && (accessibility.contains(r.pkg) || hidden)) {
                add(r, 3, "Combinazione tipica delle app spia");
            }
            if (r.score > 0 || !fromStore) results.add(r);
        }
        if (progress != null) progress.on(pkgs.size(), pkgs.size());
        Collections.sort(results, (a, b) -> Integer.compare(b.score, a.score));
        return results;
    }

    private static void add(Result r, int pts, String why) {
        r.score += pts;
        r.reasons.add(why);
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signatures(PackageInfo pi) {
        if (Build.VERSION.SDK_INT >= 28) {
            SigningInfo si = pi.signingInfo;
            if (si == null) return new Signature[0];
            Signature[] s = si.hasMultipleSigners() ? si.getApkContentsSigners() : si.getSigningCertificateHistory();
            return s != null ? s : new Signature[0];
        }
        return pi.signatures != null ? pi.signatures : new Signature[0];
    }

    private static byte[] digest(String alg, byte[] data) {
        try {
            return MessageDigest.getInstance(alg).digest(data);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static String fileSha256(File f) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return hex(md.digest()).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02X", x));
        return sb.toString();
    }

    private static Set<String> grantedPermissions(PackageInfo pi) {
        Set<String> out = new HashSet<>();
        if (pi.requestedPermissions == null) return out;
        for (int i = 0; i < pi.requestedPermissions.length; i++) {
            int f = pi.requestedPermissionsFlags != null && i < pi.requestedPermissionsFlags.length
                    ? pi.requestedPermissionsFlags[i] : 0;
            if ((f & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) out.add(pi.requestedPermissions[i]);
        }
        return out;
    }

    static Set<String> pkgsFromSetting(String value) {
        Set<String> out = new HashSet<>();
        if (value == null || value.isEmpty()) return out;
        for (String part : value.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (cn != null) out.add(cn.getPackageName());
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    static String installerOf(PackageManager pm, String pkg) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                InstallSourceInfo info = pm.getInstallSourceInfo(pkg);
                return info.getInstallingPackageName();
            }
            return pm.getInstallerPackageName(pkg);
        } catch (Exception e) {
            return null;
        }
    }
}
