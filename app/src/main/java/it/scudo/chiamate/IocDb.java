package it.scudo.chiamate;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Indicatori pubblici di spyware (assets/ioc.json), costruiti da:
 * AssoEchap/stalkerware-indicators e mvt-project/mvt-indicators (Amnesty, Citizen Lab, Lookout, Google TAG).
 */
final class IocDb {
    private static IocDb instance;

    final Map<String, String> packages = new HashMap<>();
    final Map<String, String> certSha1 = new HashMap<>();
    final Map<String, String> certSha256 = new HashMap<>();
    final Map<String, String> apkSha256 = new HashMap<>();

    static synchronized IocDb get(Context ctx) {
        if (instance == null) instance = new IocDb(ctx.getApplicationContext());
        return instance;
    }

    private IocDb(Context ctx) {
        try (InputStream in = ctx.getAssets().open("ioc.json")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONObject o = new JSONObject(bo.toString("UTF-8"));
            fill(o.optJSONObject("packages"), packages, false);
            fill(o.optJSONObject("cert_sha1"), certSha1, true);
            fill(o.optJSONObject("cert_sha256"), certSha256, true);
            fill(o.optJSONObject("apk_sha256"), apkSha256, false);
        } catch (Exception ignored) {
        }
    }

    private static void fill(JSONObject src, Map<String, String> dst, boolean upper) {
        if (src == null) return;
        Iterator<String> it = src.keys();
        while (it.hasNext()) {
            String k = it.next();
            dst.put(upper ? k.toUpperCase() : k, src.optString(k));
        }
    }

    int size() {
        return packages.size() + certSha1.size() + certSha256.size() + apkSha256.size();
    }
}
