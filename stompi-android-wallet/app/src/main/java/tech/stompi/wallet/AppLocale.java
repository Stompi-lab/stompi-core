package tech.stompi.wallet;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Arrays;
import java.util.Locale;
import org.json.JSONObject;

/** UI locale only: does not modify recovery words, keys or transaction data. */
final class AppLocale {
    static final String PREFS = "stompi.language.v1";
    static final String[] CODES = {"", "de", "en", "fr", "es", "it", "pt", "nl", "pl"};
    static final String[] NAMES = {"", "Deutsch", "English", "Français", "Español", "Italiano", "Português", "Nederlands", "Polski"};

    static String saved(Context context) {
        String code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("code", "");
        return Arrays.asList(CODES).contains(code) ? code : "";
    }

    static Context wrap(Context context) {
        String code = saved(context);
        if (code.isEmpty()) return context;
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocales(new LocaleList(Locale.forLanguageTag(code)));
        return context.createConfigurationContext(config);
    }

    static String code(Context context) {
        String code = saved(context);
        if (!code.isEmpty()) return code;
        String system = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        return Arrays.asList(CODES).contains(system) ? system : "en";
    }

    static String news(JSONObject item, String field, String code) {
        // Each field has an explicit English, then German fallback.
        for (String candidate : new String[]{code, "en", "de"}) {
            String value = item.optString(field + "_" + candidate, "");
            if (!value.trim().isEmpty()) return value;
        }
        return "";
    }
}
