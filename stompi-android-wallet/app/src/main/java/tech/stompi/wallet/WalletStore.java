package tech.stompi.wallet;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

/** Separate encrypted seeds, with an ordered wallet list and one active wallet. */
public final class WalletStore {
    private static final String ALIAS = "stompi.wallet.seed.v1";
    private static final String PREF = "stompi.wallet.storage.v1";
    private final SharedPreferences prefs;
    public WalletStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF, 0);
        migrateLegacy();
    }
    public static final class Entry {
        public final String id, name;
        Entry(String id, String name) { this.id = id; this.name = name; }
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        SecretKey existing = (SecretKey) ks.getKey(ALIAS, null);
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    private void migrateLegacy() {
        if (!prefs.contains("ciphertext")) return;
        String id = UUID.randomUUID().toString();
        try {
            JSONArray list = new JSONArray(prefs.getString("wallets", "[]"));
            list.put(new JSONObject().put("id", id).put("name", "Wallet 1"));
            if (!prefs.edit().putString("wallets", list.toString()).putString("active", id)
                    .putString(id + ".iv", prefs.getString("iv", ""))
                    .putString(id + ".ciphertext", prefs.getString("ciphertext", ""))
                    .remove("iv").remove("ciphertext").commit())
                throw new IllegalStateException("Migration could not be saved");
        } catch (Exception e) { throw new IllegalStateException("Wallet migration failed", e); }
    }

    public synchronized List<Entry> list() {
        try {
            JSONArray data = new JSONArray(prefs.getString("wallets", "[]"));
            List<Entry> found = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                found.add(new Entry(item.getString("id"), item.getString("name")));
            }
            return found;
        } catch (Exception e) { throw new IllegalStateException("Wallet list corrupted", e); }
    }
    public synchronized Entry active() {
        String id = prefs.getString("active", "");
        for (Entry entry : list()) if (entry.id.equals(id)) return entry;
        return null;
    }
    public synchronized void select(String id) {
        require(id);
        if (!prefs.edit().putString("active", id).commit())
            throw new IllegalStateException("Selection could not be saved");
    }
    private Entry require(String id) {
        for (Entry entry : list()) if (entry.id.equals(id)) return entry;
        throw new IllegalArgumentException("Unknown wallet");
    }
    public synchronized Entry saveNew(String mnemonic, String name) throws Exception {
        if (name == null || name.trim().isEmpty() || name.length() > 40)
            throw new IllegalArgumentException("Invalid wallet name");
        String normalized = name.trim();
        for (Entry entry : list()) if (entry.name.equalsIgnoreCase(normalized))
            throw new IllegalArgumentException("Wallet name exists");
        String id = UUID.randomUUID().toString();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(mnemonic.getBytes(StandardCharsets.UTF_8));
        JSONArray data = new JSONArray(prefs.getString("wallets", "[]"));
        data.put(new JSONObject().put("id", id).put("name", normalized));
        boolean saved = prefs.edit()
                .putString(id + ".iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(id + ".ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("wallets", data.toString()).putString("active", id).commit();
        if (!saved) throw new IllegalStateException("Wallet storage failed");
        return new Entry(id, normalized);
    }
    public synchronized String readActive() throws Exception {
        Entry entry = active();
        if (entry == null) throw new IllegalStateException("No active wallet");
        return read(entry.id);
    }
    public synchronized String read(String id) throws Exception {
        require(id);
        byte[] iv = Base64.decode(prefs.getString(id + ".iv", ""), Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(prefs.getString(id + ".ciphertext", ""), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
    public synchronized void delete(String id) throws Exception {
        require(id);
        JSONArray remaining = new JSONArray();
        for (Entry entry : list()) if (!entry.id.equals(id))
            remaining.put(new JSONObject().put("id", entry.id).put("name", entry.name));
        String next = remaining.length() == 0 ? "" : remaining.getJSONObject(0).getString("id");
        // Metadata and encrypted bytes are removed together. Keystore key remains for other wallets.
        if (!prefs.edit().putString("wallets", remaining.toString())
                .putString("active", active() != null && active().id.equals(id) ? next : prefs.getString("active", next))
                .remove(id + ".iv").remove(id + ".ciphertext").commit())
            throw new IllegalStateException("Deletion could not be saved");
    }
}
