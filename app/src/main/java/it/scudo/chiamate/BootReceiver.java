package it.scudo.chiamate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Riattiva la protezione dopo il riavvio del telefono, se era accesa. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && CallMonitorService.isEnabled(ctx)) {
            try {
                CallMonitorService.start(ctx);
            } catch (Exception ignored) {
                // Alcune versioni di Android possono impedirlo: si riattiva aprendo l'app.
            }
        }
    }
}
