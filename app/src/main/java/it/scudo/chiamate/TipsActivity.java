package it.scudo.chiamate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TipsActivity extends Activity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = Ui.page(this);
        Ui.title(this, col, "Consigli e limiti");

        section(col, "Cosa l'app NON può fare",
                "• Non può sapere se chi ti chiama ha app spia sul suo telefono: nessuna app può farlo.\n"
                + "• Non vede le intercettazioni fatte dall'operatore o dalle autorità: avvengono sulla rete,"
                + " non sul telefono.\n"
                + "• Gli avvisi sono indizi, non prove.");

        section(col, "Disattiva il 2G",
                "Il 2G ha una cifratura debole ed è la rete usata dai falsi ripetitori. Sui Samsung recenti:"
                + " Impostazioni › Connessioni › Reti mobili › Modalità di rete, e scegli una modalità senza 2G."
                + " Se c'è la voce \"Consenti 2G\", spegnila.");
        Ui.button(this, col, "Apri impostazioni reti mobili",
                v -> open(new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)));

        section(col, "Controlla chi usa il microfono",
                "Impostazioni › Sicurezza e privacy › Privacy › Autorizzazioni › Microfono. Togli il permesso"
                + " alle app che non riconosci. Quando un'app usa il microfono compare un pallino verde in alto.");

        section(col, "Accessibilità e amministratori",
                "Le app spia si attivano spesso come servizio di Accessibilità o Amministratore dispositivo."
                + " Controlla in Impostazioni › Accessibilità › App installate e in Impostazioni ›"
                + " Sicurezza e privacy › Altre impostazioni di sicurezza › App di amministrazione dispositivo.");
        Ui.button(this, col, "Apri Accessibilità",
                v -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        section(col, "Blocco automatico Samsung",
                "Attiva Impostazioni › Sicurezza e privacy › Blocco automatico (Auto Blocker): impedisce"
                + " l'installazione di app da fonti sconosciute e via cavo.");

        section(col, "Se sospetti davvero di essere spiato",
                "Non disinstallare subito l'app sospetta se pensi di dover denunciare: fai prima degli"
                + " screenshot. Poi rivolgiti alla Polizia Postale. Per una pulizia completa, fai un backup"
                + " delle foto e ripristina il telefono ai dati di fabbrica.");
    }

    private void section(LinearLayout col, String title, String body) {
        LinearLayout card = Ui.card(this, col, Ui.BLUE);
        TextView t = Ui.text(this, card, title, 17, Ui.BLUE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        Ui.text(this, card, body, 15, Color.DKGRAY);
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
