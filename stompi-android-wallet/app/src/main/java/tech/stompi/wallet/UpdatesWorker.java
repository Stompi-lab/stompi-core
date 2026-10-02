package tech.stompi.wallet;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Periodic, opt-in checks. Wallet checks cover only the first receiving address, not a balance. */
public final class UpdatesWorker extends Worker {
    private static final String NEWS = "https://stompi.tech/api/news";
    private static final String ADDRESS = "https://stompi.tech/api/address/";
    private static final String CH_NEWS = "stompi_news";
    private static final String CH_RECEIVED = "stompi_received_first_address";
    private static final String PREFS = "stompi.notifications.v1";
    public UpdatesWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); NetworkConfig.init(context); }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest job = new PeriodicWorkRequest.Builder(UpdatesWorker.class, 30, TimeUnit.MINUTES)
                .setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("stompi-updates",
                ExistingPeriodicWorkPolicy.KEEP, job);
    }

    static JSONObject fetch(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(NetworkConfig.rewrite(url)).openConnection();
        con.setInstanceFollowRedirects(false);
        con.setConnectTimeout(6000);
        con.setReadTimeout(6000);
        try {
            if (con.getResponseCode() != 200) throw new IllegalStateException("HTTP " + con.getResponseCode());
            try (InputStream input = con.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, n);
                    if (bytes.size() > 65536) throw new IllegalStateException("Response too large");
                }
                return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { con.disconnect(); }
    }
    public static JSONObject fetchNews() throws Exception { return fetch(NEWS); }

    private boolean mayNotify() {
        return Build.VERSION.SDK_INT < 33 || getApplicationContext().checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    private void notifyUser(String channel, int id, String title, String body) {
        if (!mayNotify()) return;
        Context ctx = AppLocale.wrap(getApplicationContext());
        NotificationManager manager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CH_NEWS, ctx.getString(R.string.news_heading), NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel(CH_RECEIVED, ctx.getString(R.string.incoming_channel), NotificationManager.IMPORTANCE_DEFAULT));
        Intent launch = new Intent(ctx, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(ctx, id, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(ctx, channel)
                .setSmallIcon(R.drawable.stompi).setContentTitle(title).setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(pending).setAutoCancel(true).build();
        manager.notify(id, notification);
    }

    private void checkNews(SharedPreferences state) throws Exception {
        JSONArray items = fetchNews().getJSONArray("items");
        if (items.length() > 30) throw new IllegalStateException("News feed too large");
        Set<String> known = state.getStringSet("news_ids", null);
        Set<String> current = new HashSet<>();
        String newsLanguage = AppLocale.code(getApplicationContext());
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String id = item.getString("id");
            if (id.length() < 1 || id.length() > 80) continue;
            current.add(id);
            if (known != null && !known.contains(id)) {
                notifyUser(CH_NEWS, id.hashCode(), AppLocale.news(item, "title", newsLanguage),
                        AppLocale.news(item, "body", newsLanguage));
            }
        }
        if (known != null) current.addAll(known);
        if (!state.edit().putStringSet("news_ids", current).commit())
            throw new IllegalStateException("News checkpoint not stored");
    }

    private void checkWallets(SharedPreferences state) {
        WalletStore store = new WalletStore(getApplicationContext());
        for (WalletStore.Entry entry : store.list()) {
            try {
                String address = StompiWalletKeys.address(store.read(entry.id), 0, 0);
                long received = fetch(ADDRESS + address).getLong("received_sats");
                String key = "received." + entry.id;
                boolean known = state.contains(key);
                long old = state.getLong(key, 0);
                if (!state.edit().putLong(key, received).commit()) continue;
                if (known && received > old) {
                    notifyUser(CH_RECEIVED, entry.id.hashCode(),
                            AppLocale.wrap(getApplicationContext()).getString(R.string.incoming_title),
                            AppLocale.wrap(getApplicationContext()).getString(R.string.incoming_body, entry.name));
                }
            } catch (Exception ignored) {
                // Do not advance this wallet's checkpoint on network, key, or explorer errors.
            }
        }
    }
    @NonNull @Override public Result doWork() {
        SharedPreferences state = getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!state.getBoolean("enabled", false)) return Result.success();
        if (!mayNotify()) return Result.success();
        try { checkNews(state); } catch (Exception ignored) { /* Keep old checkpoint on feed failure. */ }
        checkWallets(state);
        return Result.success();
    }
}
