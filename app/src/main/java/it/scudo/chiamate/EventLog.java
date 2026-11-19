package it.scudo.chiamate;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Registro eventi salvato in un file privato dell'app (resta solo sul telefono). */
final class EventLog {
    static final String INFO = "INFO";
    static final String WARN = "ATTENZIONE";
    static final String ALERT = "ALLARME";

    private static final String FILE = "eventi.log";
    private static final int MAX_LINES = 2000;

    private EventLog() {}

    static synchronized void add(Context ctx, String level, String text) {
        String ts = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY).format(new Date());
        String line = ts + "|" + level + "|" + text.replace('\n', ' ').replace('|', '/');
        File f = new File(ctx.getFilesDir(), FILE);
        try (FileWriter w = new FileWriter(f, true)) {
            w.write(line);
            w.write('\n');
        } catch (IOException ignored) {
        }
        trimIfNeeded(f);
    }

    /** Righe più recenti per prime. Ogni riga: {data, livello, testo}. */
    static synchronized List<String[]> read(Context ctx) {
        List<String[]> out = new ArrayList<>();
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3) out.add(parts);
            }
        } catch (IOException ignored) {
        }
        Collections.reverse(out);
        return out;
    }

    static synchronized void clear(Context ctx) {
        //noinspection ResultOfMethodCallIgnored
        new File(ctx.getFilesDir(), FILE).delete();
    }

    private static void trimIfNeeded(File f) {
        if (f.length() < 400_000) return;
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
        } catch (IOException e) {
            return;
        }
        int from = Math.max(0, lines.size() - MAX_LINES / 2);
        try (FileWriter w = new FileWriter(f, false)) {
            for (int i = from; i < lines.size(); i++) {
                w.write(lines.get(i));
                w.write('\n');
            }
        } catch (IOException ignored) {
        }
    }
}
