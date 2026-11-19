package it.scudo.chiamate;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ricava tutto quello che si può sapere da un numero senza servizi esterni. */
final class NumberInfo {

    private static final Map<String, String> CITY = new LinkedHashMap<>();
    private static final Map<String, String> COUNTRY = new LinkedHashMap<>();

    static {
        // Prefissi a 4 cifre prima di quelli a 3 e 2 (si prova il più lungo).
        String[][] c = {
                {"0961", "Catanzaro"}, {"0965", "Reggio Calabria"}, {"0984", "Cosenza"}, {"0432", "Udine"},
                {"0461", "Trento"}, {"0471", "Bolzano"}, {"0521", "Parma"}, {"0522", "Reggio Emilia"},
                {"0544", "Ravenna"}, {"0541", "Rimini"}, {"0574", "Prato"}, {"0577", "Siena"},
                {"0583", "Lucca"}, {"0862", "L'Aquila"}, {"0874", "Campobasso"}, {"0971", "Potenza"},
                {"0831", "Brindisi"}, {"0832", "Lecce"}, {"0881", "Foggia"}, {"0784", "Nuoro"},
                {"0165", "Aosta"}, {"0187", "La Spezia"}, {"0422", "Treviso"}, {"0444", "Vicenza"},
                {"0425", "Rovigo"}, {"0532", "Ferrara"}, {"0721", "Pesaro"}, {"0733", "Macerata"},
                {"0823", "Caserta"}, {"0824", "Benevento"}, {"0825", "Avellino"}, {"0921", "Cefalù"},
                {"0922", "Agrigento"}, {"0931", "Siracusa"}, {"0932", "Ragusa"}, {"0934", "Caltanissetta"},
                {"011", "Torino"}, {"010", "Genova"}, {"019", "Savona"}, {"030", "Brescia"},
                {"031", "Como"}, {"035", "Bergamo"}, {"039", "Monza"}, {"040", "Trieste"},
                {"041", "Venezia"}, {"045", "Verona"}, {"049", "Padova"}, {"050", "Pisa"},
                {"051", "Bologna"}, {"055", "Firenze"}, {"059", "Modena"}, {"070", "Cagliari"},
                {"071", "Ancona"}, {"075", "Perugia"}, {"079", "Sassari"}, {"080", "Bari"},
                {"081", "Napoli"}, {"085", "Pescara"}, {"089", "Salerno"}, {"090", "Messina"},
                {"091", "Palermo"}, {"095", "Catania"}, {"099", "Taranto"},
                {"02", "Milano"}, {"06", "Roma"},
        };
        for (String[] e : c) CITY.put(e[0], e[1]);

        String[][] n = {
                {"355", "Albania"}, {"380", "Ucraina"}, {"381", "Serbia"}, {"385", "Croazia"},
                {"386", "Slovenia"}, {"351", "Portogallo"}, {"212", "Marocco"}, {"216", "Tunisia"},
                {"234", "Nigeria"}, {"221", "Senegal"}, {"222", "Mauritania"}, {"225", "Costa d'Avorio"},
                {"880", "Bangladesh"}, {"971", "Emirati Arabi"}, {"972", "Israele"}, {"870", "rete satellitare"},
                {"881", "rete satellitare"}, {"882", "rete internazionale"}, {"883", "rete internazionale"},
                {"44", "Regno Unito"}, {"33", "Francia"}, {"49", "Germania"}, {"34", "Spagna"},
                {"41", "Svizzera"}, {"43", "Austria"}, {"40", "Romania"}, {"48", "Polonia"},
                {"31", "Paesi Bassi"}, {"32", "Belgio"}, {"30", "Grecia"}, {"90", "Turchia"},
                {"20", "Egitto"}, {"92", "Pakistan"}, {"91", "India"}, {"86", "Cina"},
                {"63", "Filippine"}, {"55", "Brasile"}, {"54", "Argentina"}, {"52", "Messico"},
                {"1", "USA / Canada"}, {"7", "Russia / Kazakistan"},
        };
        for (String[] e : n) COUNTRY.put(e[0], e[1]);
    }

    private NumberInfo() {}

    /** Descrizione leggibile, es. "Mario Rossi · cellulare italiano" o "sconosciuto · fisso, Roma". */
    static String describe(Context ctx, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "Numero nascosto (solo l'operatore può risalire al chiamante, su denuncia)";
        }
        String name = contactName(ctx, raw);
        String who = name != null ? name + " (in rubrica)" : "Non in rubrica";
        return raw + " · " + who + " · " + kind(raw);
    }

    static String kind(String raw) {
        String n = raw.replaceAll("[^0-9+]", "");
        if (n.startsWith("00")) n = "+" + n.substring(2);
        if (n.startsWith("+39")) n = n.substring(3);
        else if (n.startsWith("+")) {
            String rest = n.substring(1);
            for (int len = 3; len >= 1; len--) {
                if (rest.length() < len) continue;
                String country = COUNTRY.get(rest.substring(0, len));
                if (country != null) {
                    String extra = ("44".equals(rest.substring(0, len)) || "355".equals(rest.substring(0, len)))
                            ? " (prefisso molto usato da call center e truffe)" : "";
                    return "estero, " + country + extra;
                }
            }
            return "numero estero";
        }
        if (n.startsWith("3")) return "cellulare italiano (l'operatore non si può sapere: portabilità)";
        if (n.startsWith("800") || n.startsWith("803")) return "numero verde";
        if (n.startsWith("89") || n.startsWith("199") || n.startsWith("144") || n.startsWith("166")) {
            return "numero a PAGAMENTO";
        }
        if (n.startsWith("0")) {
            for (int len = 4; len >= 2; len--) {
                if (n.length() < len) continue;
                String city = CITY.get(n.substring(0, len));
                if (city != null) return "fisso, zona di " + city;
            }
            return "fisso italiano";
        }
        if (n.startsWith("1") && n.length() <= 5) return "numero di servizio / emergenza";
        return "tipo non riconosciuto";
    }

    static String contactName(Context ctx, String number) {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor c = ctx.getContentResolver().query(uri,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }
}
