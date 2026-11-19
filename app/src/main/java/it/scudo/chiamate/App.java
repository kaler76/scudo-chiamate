package it.scudo.chiamate;

import android.app.Application;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Salva il dettaglio di ogni chiusura improvvisa, per mostrarlo alla riapertura. */
public class App extends Application {
    static final String CRASH_FILE = "ultimo_errore.txt";

    @Override public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try (FileWriter w = new FileWriter(new File(getFilesDir(), CRASH_FILE))) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                w.write("Versione " + BuildConfig.VERSION_NAME + " · thread " + t.getName() + "\n" + sw);
            } catch (Exception ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
}
