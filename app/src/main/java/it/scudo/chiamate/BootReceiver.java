package it.scudo.chiamate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Riattiva la protezione dopo il riavvio del telefono o un aggiornamento dell'app, se era accesa. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a);
        if (boot && CallMonitorService.isEnabled(ctx)) {
            try {
                CallMonitorService.start(ctx);
            } catch (Exception ignored) {
                // Alcune versioni di Android possono impedirlo: si riattiva aprendo l'app.
            }
        }
    }
}
