package it.scudo.chiamate;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Controlli intorno a WhatsApp (l'app non può leggere dentro WhatsApp: Android isola ogni app).
 * Riusa la struttura dei controlli di DeviceAudit.
 */
final class WhatsAppCheck {
    static final String WA = "com.whatsapp";
    static final String WA_BUSINESS = "com.whatsapp.w4b";
    private static final String[] KNOWN_MODS = {"com.gbwhatsapp", "com.fmwhatsapp", "com.yowhatsapp"};

    private WhatsAppCheck() {}

    static boolean installed(Context ctx) {
        return info(ctx.getPackageManager(), WA) != null || info(ctx.getPackageManager(), WA_BUSINESS) != null;
    }

    static List<DeviceAudit.Check> run(Context ctx) {
        List<DeviceAudit.Check> l = new ArrayList<>();
        PackageManager pm = ctx.getPackageManager();

        // 1. WhatsApp originale?
        for (String pkg : new String[]{WA, WA_BUSINESS}) {
            if (info(pm, pkg) == null) continue;
            String name = WA.equals(pkg) ? "WhatsApp" : "WhatsApp Business";
            String inst = AppScanner.installerOf(pm, pkg);
            boolean store = "com.android.vending".equals(inst) || "com.sec.android.app.samsungapps".equals(inst);
            add(l, name + " originale", store ? 0 : 2, 25,
                    store ? "Installato dallo store ufficiale."
                            : "NON installato da Play Store o Galaxy Store" + (inst != null ? " (" + inst + ")" : "")
                            + ". Potrebbe essere una versione modificata: disinstallalo e reinstallalo dal Play Store"
                            + " (fai prima il backup delle chat).",
                    null);
        }

        // 2. Cloni / versioni modificate
        List<String> mods = new ArrayList<>();
        for (PackageInfo pi : pm.getInstalledPackages(0)) {
            String p = pi.packageName;
            if (p.equals(WA) || p.equals(WA_BUSINESS) || pi.applicationInfo == null) continue;
            String label = String.valueOf(pi.applicationInfo.loadLabel(pm)).toLowerCase(Locale.ROOT);
            boolean known = false;
            for (String m : KNOWN_MODS) if (p.equals(m)) known = true;
            boolean looksLike = p.toLowerCase(Locale.ROOT).contains("whatsapp") || label.contains("whatsapp");
            boolean system = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (known || (looksLike && !system)) mods.add(pi.applicationInfo.loadLabel(pm) + " (" + p + ")");
        }
        add(l, "Versioni modificate di WhatsApp", mods.isEmpty() ? 0 : 2, 25,
                mods.isEmpty() ? "Nessun GBWhatsApp, FMWhatsApp o simili."
                        : "Trovate: " + String.join(", ", mods)
                        + ". Queste versioni sono state usate più volte per diffondere trojan: disinstallale.",
                null);

        // 3. App che possono leggere i messaggi WhatsApp
        Set<String> notif = AppScanner.pkgsFromSetting(Settings.Secure.getString(
                ctx.getContentResolver(), "enabled_notification_listeners"));
        List<String> nl = labels(pm, notif, true);
        add(l, "App che leggono le notifiche", nl.isEmpty() ? 0 : 1, 15,
                nl.isEmpty() ? "Nessuna app esterna legge le notifiche dei messaggi."
                        : "Vedono il testo di ogni messaggio WhatsApp che arriva: " + String.join(", ", nl)
                        + ". Va bene solo se le riconosci (es. smartwatch).",
                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");

        Set<String> acc = AppScanner.pkgsFromSetting(Settings.Secure.getString(
                ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        List<String> al = labels(pm, acc, true);
        add(l, "App che leggono lo schermo", al.isEmpty() ? 0 : 2, 20,
                al.isEmpty() ? "Nessuna app esterna può leggere lo schermo mentre usi WhatsApp."
                        : "Possono leggere le chat aperte sullo schermo: " + String.join(", ", al),
                Settings.ACTION_ACCESSIBILITY_SETTINGS);

        // 4. App con accesso a tutti i file (vedono foto, vocali e documenti di WhatsApp)
        List<String> files = allFilesApps(ctx);
        add(l, "Accesso a foto e vocali", files.isEmpty() ? 0 : 1, 10,
                files.isEmpty() ? "Nessuna app esterna ha accesso a tutti i file."
                        : "Possono leggere foto, vocali e documenti ricevuti su WhatsApp: " + String.join(", ", files),
                "android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION");

        return l;
    }

    private static void add(List<DeviceAudit.Check> l, String title, int state, int weight, String detail, String action) {
        DeviceAudit.Check c = new DeviceAudit.Check();
        c.title = title; c.state = state; c.weight = weight; c.detail = detail;
        if (action != null) c.fix = new Intent(action);
        l.add(c);
    }

    private static ApplicationInfo info(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationInfo(pkg, 0);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> labels(PackageManager pm, Set<String> pkgs, boolean skipSystem) {
        List<String> out = new ArrayList<>();
        for (String p : pkgs) {
            ApplicationInfo ai = info(pm, p);
            if (ai == null) continue;
            if (skipSystem && (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
            out.add(String.valueOf(ai.loadLabel(pm)));
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    private static List<String> allFilesApps(Context ctx) {
        List<String> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT < 30) return out;
        PackageManager pm = ctx.getPackageManager();
        AppOpsManager aom = ctx.getSystemService(AppOpsManager.class);
        for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null || pi.requestedPermissions == null || pi.packageName.equals(ctx.getPackageName())) continue;
            if ((ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
            boolean asks = false;
            for (String p : pi.requestedPermissions) {
                if ("android.permission.MANAGE_EXTERNAL_STORAGE".equals(p)) asks = true;
            }
            if (!asks) continue;
            try {
                int mode = aom.unsafeCheckOpNoThrow("android:manage_external_storage", ai.uid, pi.packageName);
                if (mode == AppOpsManager.MODE_ALLOWED) out.add(String.valueOf(ai.loadLabel(pm)));
            } catch (Exception ignored) {
            }
        }
        return out;
    }
}
