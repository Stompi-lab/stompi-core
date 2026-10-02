package tech.stompi.wallet;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local, per-wallet recipient book. No recovery words or private keys are stored here. */
public final class PaymentContacts {
    private final SharedPreferences preferences;
    public PaymentContacts(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("stompi.contacts.v1", Context.MODE_PRIVATE);
    }
    public static final class Contact {
        public final String name, address;
        Contact(String name, String address) { this.name = name; this.address = address; }
    }
    public synchronized List<Contact> list(String walletId) {
        try {
            JSONArray data = new JSONArray(preferences.getString(walletId, "[]"));
            List<Contact> contacts = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                contacts.add(new Contact(item.getString("name"), item.getString("address")));
            }
            return contacts;
        } catch (Exception e) { throw new IllegalStateException("Contacts unreadable", e); }
    }
    public synchronized void save(String walletId, String name, String address) {
        String label = name == null ? "" : name.trim();
        String target = address == null ? "" : address.trim();
        if (label.isEmpty() || label.length() > 40 || !StompiAddress.isNativeSegwitV0(target))
            throw new IllegalArgumentException("Invalid contact");
        try {
            JSONArray data = new JSONArray();
            data.put(new JSONObject().put("name", label).put("address", target));
            for (Contact contact : list(walletId)) {
                if (!contact.address.equals(target) && data.length() < 20)
                    data.put(new JSONObject().put("name", contact.name).put("address", contact.address));
            }
            if (!preferences.edit().putString(walletId, data.toString()).commit())
                throw new IllegalStateException("Contacts not saved");
        } catch (org.json.JSONException e) { throw new IllegalStateException("Contacts not saved", e); }
    }
    public synchronized void deleteWallet(String walletId) {
        if (!preferences.edit().remove(walletId).commit()) throw new IllegalStateException("Contacts not removed");
    }
}
