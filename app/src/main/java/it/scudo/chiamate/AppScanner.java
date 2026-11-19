package it.scudo.chiamate;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Analizza le app installate e assegna un punteggio di rischio "app spia"
 * in base a permessi, servizi speciali attivi, icona nascosta e provenienza.
 */
final class AppScanner {

    static final class Result {
        String label;
        String pkg;
        int score;
        final List<String> reasons = new ArrayList<>();

        String level() {
            if (score >= 9) return "ALTO";
            if (score >= 5) return "MEDIO";
            return "BASSO";
        }
    }

    /** Nomi di app spia commerciali note (stalkerware). */
    private static final String[] STRONG_NAMES = {
            "spy", "stalk", "mspy", "flexispy", "hoverwatch", "cocospy", "spyzie", "xnspy",
            "eyezy", "umobix", "truthspy", "kidsguard", "clevguard", "spyic", "minspy",
            "spyera", "highster", "ikeymonitor", "mobistealth", "catwatchful", "spyhide",
            "trackview", "mobile-tracker", "mobiletracker", "thewispy", "spymaster", "fonemonitor"
    };
    /** Parole generiche: pesano poco perché usate anche da app innocue. */
    private static final String[] WEAK_NAMES = {"monitor", "tracker", "stealth", "hidden", "nascost"};

    private static final Set<String> TRUSTED_STORES = new HashSet<>();
    static {
        TRUSTED_STORES.add("com.android.vending");               // Google Play
        TRUSTED_STORES.add("com.sec.android.app.samsungapps");   // Galaxy Store
        TRUSTED_STORES.add("com.samsung.android.app.omcagent");  // preinstallate Samsung
        TRUSTED_STORES.add("com.google.android.feedback");
    }

    private AppScanner() {}

    static List<Result> scan(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        ContentResolver cr = ctx.getContentResolver();

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

        List<Result> results = new ArrayList<>();
        List<PackageInfo> pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        for (PackageInfo pi : pkgs) {
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null || pi.packageName.equals(ctx.getPackageName())) continue;

            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean updatedSystem = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            boolean special = accessibility.contains(pi.packageName)
                    || admins.contains(pi.packageName)
                    || notifListeners.contains(pi.packageName);
            // Le app di sistema pure si saltano, a meno che abbiano servizi speciali attivi.
            if (system && !updatedSystem && !special) continue;

            Result r = new Result();
            r.pkg = pi.packageName;
            r.label = String.valueOf(ai.loadLabel(pm));

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

            if (mic) add(r, 3, "Può usare il microfono");
            if (callLog) add(r, 3, "Può leggere il registro chiamate");
            if (phone) add(r, 1, "Può sapere quando sei in chiamata");
            if (sms) add(r, 2, "Può leggere gli SMS");
            if (bgLocation) add(r, 2, "Posizione anche in background");
            else if (location) add(r, 1, "Può conoscere la posizione");
            if (contacts) add(r, 1, "Può leggere i contatti");
            if (boot) add(r, 1, "Si avvia da sola all'accensione");
            if (overlay) add(r, 1, "Può disegnare sopra le altre app");

            if (accessibility.contains(r.pkg)) add(r, 5, "Servizio di ACCESSIBILITÀ attivo (può leggere lo schermo)");
            if (admins.contains(r.pkg)) add(r, 4, "È AMMINISTRATORE del dispositivo (difficile da disinstallare)");
            if (notifListeners.contains(r.pkg)) add(r, 3, "Legge tutte le notifiche");

            if (pm.getLaunchIntentForPackage(r.pkg) == null && !system) {
                add(r, 3, "Nessuna icona nel menu app (nascosta)");
            }

            String installer = installerOf(pm, r.pkg);
            if (!system && (installer == null || !TRUSTED_STORES.contains(installer))) {
                add(r, 2, "Non installata da Play Store o Galaxy Store"
                        + (installer == null ? "" : " (" + installer + ")"));
            }

            String hay = (r.pkg + " " + r.label).toLowerCase(Locale.ROOT);
            for (String k : STRONG_NAMES) {
                if (hay.contains(k)) { add(r, 7, "Nome simile a un'app spia nota (\"" + k + "\")"); break; }
            }
            for (String k : WEAK_NAMES) {
                if (hay.contains(k)) { add(r, 2, "Nome sospetto (\"" + k + "\")"); break; }
            }

            // Combinazione tipica delle app spia: microfono + chiamate + nascosta o accessibilità.
            if (mic && (callLog || phone) && (accessibility.contains(r.pkg)
                    || pm.getLaunchIntentForPackage(r.pkg) == null)) {
                add(r, 3, "Combinazione tipica delle app spia");
            }

            if (r.score > 0) results.add(r);
        }
        Collections.sort(results, (a, b) -> Integer.compare(b.score, a.score));
        return results;
    }

    private static void add(Result r, int pts, String why) {
        r.score += pts;
        r.reasons.add(why);
    }

    private static Set<String> grantedPermissions(PackageInfo pi) {
        Set<String> out = new HashSet<>();
        if (pi.requestedPermissions == null) return out;
        for (int i = 0; i < pi.requestedPermissions.length; i++) {
            int flags = pi.requestedPermissionsFlags != null && i < pi.requestedPermissionsFlags.length
                    ? pi.requestedPermissionsFlags[i] : 0;
            if ((flags & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) out.add(pi.requestedPermissions[i]);
        }
        return out;
    }

    private static Set<String> pkgsFromSetting(String value) {
        Set<String> out = new HashSet<>();
        if (value == null || value.isEmpty()) return out;
        for (String part : value.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(part);
            if (cn != null) out.add(cn.getPackageName());
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    private static String installerOf(PackageManager pm, String pkg) {
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
