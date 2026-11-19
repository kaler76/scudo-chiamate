package it.scudo.chiamate;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** "Chi è questo numero?": cosa si ricava dal numero e ricerca sui siti di segnalazione. */
public class LookupActivity extends Activity {
    static final String EXTRA_NUMBER = "numero";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String raw = getIntent().getStringExtra(EXTRA_NUMBER);
        LinearLayout col = Ui.page(this);
        Ui.header(this, col, "Chi è questo numero?", null);

        if (raw == null || raw.trim().isEmpty()) {
            Ui.text(this, col, "Numero nascosto: nessuna ricerca possibile. Solo l'operatore può risalire al"
                    + " chiamante, su denuncia.", 15, Ui.SUB);
            return;
        }
        String national = national(raw);

        LinearLayout card = Ui.card(this, col, Ui.ACCENT);
        TextView num = Ui.text(this, card, raw, 26, Ui.TEXT);
        num.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        num.setTextIsSelectable(true);
        Ui.text(this, card, NumberInfo.describe(this, raw), 14, Ui.SUB);

        String intl = international(raw);
        Ui.sectionLabel(this, col, "Profili collegati al numero");
        Ui.text(this, col, "Se la persona usa WhatsApp o Telegram con questo numero, spesso vedi foto profilo, nome e"
                + " info (dipende dalle sue impostazioni di privacy). Il numero non viene salvato in rubrica.", 13, Ui.SUB);
        Ui.menuRow(this, col, R.drawable.ic_chat, Ui.GREEN, "Apri su WhatsApp",
                "Foto profilo, nome e info, senza salvare il contatto",
                v -> open("https://wa.me/" + intl));
        Ui.menuRow(this, col, R.drawable.ic_chat, Ui.ACCENT, "Apri su Telegram",
                "Solo se la persona permette di essere trovata col numero",
                v -> open("https://t.me/+" + intl));

        Ui.sectionLabel(this, col, "Cerca chi è");
        Ui.text(this, col, "Questi siti raccolgono le segnalazioni delle persone: se il numero è di un call center,"
                + " di una truffa o di un'azienda, di solito lo trovi lì.", 13, Ui.SUB);
        Ui.menuRow(this, col, R.drawable.ic_search, Ui.ACCENT, "Cerca su Google",
                "Risultati e commenti su questo numero",
                v -> open("https://www.google.com/search?q=" + Uri.encode("\"" + national + "\" OR \"" + raw + "\"")));
        Ui.menuRow(this, col, R.drawable.ic_list, Ui.ORANGE, "tellows",
                "Segnalazioni e punteggio di affidabilità",
                v -> open("https://www.tellows.it/num/" + Uri.encode(national)));
        Ui.menuRow(this, col, R.drawable.ic_info, Ui.GREEN, "Cerca nei siti di segnalazione italiani",
                "chi-chiama, chistachiamando e altri",
                v -> open("https://www.google.com/search?q=" + Uri.encode(national + " chi chiama segnalazioni")));
        if (national.startsWith("0")) {
            Ui.menuRow(this, col, R.drawable.ic_list, Ui.SUB, "Pagine Bianche",
                    "Per i numeri fissi in elenco: nome e indirizzo dell'intestatario",
                    v -> open("https://www.google.com/search?q=" + Uri.encode("site:paginebianche.it \"" + national + "\"")));
        }
        Ui.menuRow(this, col, R.drawable.ic_phone, Ui.SUB, "Copia il numero",
                "Per incollarlo in Hiya, Truecaller o WhatsApp", v -> {
                    getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("numero", raw));
                    Toast.makeText(this, "Numero copiato", Toast.LENGTH_SHORT).show();
                });
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Nessun browser disponibile", Toast.LENGTH_SHORT).show();
        }
    }

    /** Formato nazionale (senza +39), come lo usano i siti italiani. */
    static String national(String raw) {
        String n = raw.replaceAll("[^0-9+]", "");
        if (n.startsWith("0039")) n = n.substring(4);
        else if (n.startsWith("+39")) n = n.substring(3);
        return n;
    }

    /** Formato internazionale senza "+", come lo vogliono WhatsApp e Telegram (Italia se manca il prefisso). */
    static String international(String raw) {
        String n = raw.replaceAll("[^0-9+]", "");
        if (n.startsWith("+")) return n.substring(1);
        if (n.startsWith("00")) return n.substring(2);
        return "39" + n;
    }
}
