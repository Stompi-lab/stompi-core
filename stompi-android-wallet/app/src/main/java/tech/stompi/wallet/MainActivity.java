package tech.stompi.wallet;

import android.app.Activity;
import android.Manifest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PersistableBundle;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;
import java.security.SecureRandom;
import java.util.Arrays;
import android.os.Bundle;
import android.view.WindowInsets;
import android.graphics.Insets;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.InputType;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Stompi wallet with local transaction signing and explicitly confirmed broadcast. */
public final class MainActivity extends androidx.fragment.app.FragmentActivity {

    private void chooseNetworkServer() {
        if(paymentSending) { Toast.makeText(this,R.string.network_busy,Toast.LENGTH_LONG).show();return; }
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(NetworkConfig.base());
        new AlertDialog.Builder(this).setTitle(R.string.network_heading).setView(input)
          .setNegativeButton(android.R.string.cancel,null)
          .setNeutralButton(R.string.network_default,(d,w)->validateNetworkServer(NetworkConfig.DEFAULT))
          .setPositiveButton(R.string.network_check,(d,w)->validateNetworkServer(input.getText().toString()))
          .show();
    }
    private void validateNetworkServer(String value) {
        if(paymentSending)return;
        Toast.makeText(this,R.string.network_checking,Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            try {
                String origin=NetworkConfig.validate(value);
                runOnUiThread(()-> new AlertDialog.Builder(this).setTitle(R.string.network_heading)
                  .setMessage(origin+"\n\n"+getString(R.string.network_warning))
                  .setNegativeButton(android.R.string.cancel,null)
                  .setPositiveButton(R.string.network_use,(d,w)->{
                      if(paymentSending)return;
                      // Same executor as signing/broadcast: no network change mid-payment.
                      worker.execute(()->{
                          try { NetworkConfig.save(origin); } catch(Exception ex) { runOnUiThread(()->Toast.makeText(this,R.string.network_failed,Toast.LENGTH_LONG).show()); return; }
                          runOnUiThread(()->{Toast.makeText(this,R.string.network_saved,Toast.LENGTH_LONG).show();recreate();});
                      });
                  }).show());
            } catch(Exception ex) {
                runOnUiThread(()->Toast.makeText(this,R.string.network_failed,Toast.LENGTH_LONG).show());
            }
        });
    }

    private static final String API = "https://stompi.tech/api/address/";
    private int BG, PANEL, MINT, TEXT, MUTED, INPUT, SECONDARY, BORDER;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean paymentSending;
    private View lockScreen, walletContent;
    private final java.util.Set<AlertDialog> sensitiveDialogs = new java.util.HashSet<>();
    private boolean unlocked, authenticating, resumed;
    private long backgroundAt;
    private static final long LOCK_AFTER_MS = 300000;
    private String balanceSuccessAt = "";
    private TextView balanceBreakdown;

    private boolean lockEnabled() {
        return getSharedPreferences("stompi.security.v1", MODE_PRIVATE).getBoolean("lock", false);
    }
    private void authenticate(Runnable success) {
        if (authenticating) return;
        int methods = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        if (androidx.biometric.BiometricManager.from(this).canAuthenticate(methods)
                != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, R.string.security_setup, Toast.LENGTH_LONG).show();
            return;
        }
        authenticating = true;
        androidx.biometric.BiometricPrompt prompt = new androidx.biometric.BiometricPrompt(this,
                command -> runOnUiThread(command), new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                authenticating = false;
                success.run();
            }
            @Override public void onAuthenticationError(int code, CharSequence message) {
                authenticating = false;
            }
        });
        prompt.authenticate(new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.security_unlock)).setAllowedAuthenticators(methods).build());
    }
    private void unlockApp() {
        authenticate(() -> { unlocked = true; backgroundAt = 0; lockScreen.setVisibility(View.GONE); walletContent.setVisibility(View.VISIBLE); });
    }
    private void scanPaymentQr() {
        if(paymentSending) return;
        com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions options =
                new com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).build();
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this, options).startScan()
                .addOnSuccessListener(barcode -> {
                    try {
                        PaymentRequest request = PaymentRequest.parse(barcode.getRawValue());
                        recipientAddress.setText(request.address);
                        paymentAmount.setText(request.amount);
                        recipientName.setText(request.label);
                    } catch (Exception e) { Toast.makeText(this, R.string.qr_invalid, Toast.LENGTH_LONG).show(); }
                }).addOnFailureListener(error -> Toast.makeText(this, R.string.qr_failed, Toast.LENGTH_LONG).show());
    }
    private void showReceiveQr() {
        if (!StompiAddress.isNativeSegwitV0(receiveAddress)) return;
        try {
            com.google.zxing.common.BitMatrix matrix = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(receiveAddress, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512);
            Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            for (int y=0;y<512;y++) for(int x=0;x<512;x++) bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
            ImageView image = new ImageView(this); image.setImageBitmap(bitmap);
            image.setAdjustViewBounds(true); image.setPadding(dp(16),dp(16),dp(16),dp(16));
            showSecureDialog(new AlertDialog.Builder(this).setTitle(R.string.qr_receive)
                    .setView(image).setPositiveButton(android.R.string.ok,null));
        } catch (Exception e) { Toast.makeText(this,R.string.qr_failed,Toast.LENGTH_LONG).show(); }
    }

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable periodicRefresh = new Runnable() {
        @Override public void run() {
            if (pendingNotice != null && (!lockEnabled() || unlocked)) checkPending(false);
            if (balance != null && page == 0) loadFirstAddressAmount();
            else if (result != null && page == 1 && refresh.isEnabled()) load();
            refreshHandler.postDelayed(this, 30000);
        }
    };
    private EditText address;
    private String receiveAddress = "";
    private TextView result;
    private LinearLayout activityDetails;
    private Button refresh;
    private Spinner walletDropdown;
    private java.util.List<WalletStore.Entry> dropdownEntries = new java.util.ArrayList<>();
    private TextView balance;
    private TextView balanceUpdated;
    private TextView pendingNotice;
    private View walletSummary, emptyState, receivePanel;
    private View paymentsButton;
    private LinearLayout homePage, activityPage, managePage, newsPage, settingsPage, paymentsPage;
    private LinearLayout paymentHistory;
    private EditText recipientName, recipientAddress, paymentAmount;
    private LinearLayout newsList;
    private Button notificationsButton;
    private int page = 0;
    private final WalletStore[] storeHolder = new WalletStore[1];

    private void loadPalette() {
        boolean light = getSharedPreferences("stompi.display.v1", MODE_PRIVATE).getBoolean("light", false);
        BG = Color.rgb(light ? 245 : 10, light ? 249 : 26, light ? 247 : 35);
        PANEL = Color.rgb(light ? 255 : 17, light ? 255 : 43, light ? 255 : 53);
        MINT = Color.rgb(light ? 26 : 81, light ? 148 : 220, light ? 111 : 177);
        TEXT = Color.rgb(light ? 18 : 231, light ? 40 : 248, light ? 49 : 242);
        MUTED = Color.rgb(light ? 70 : 151, light ? 89 : 183, light ? 96 : 185);
        INPUT = Color.rgb(light ? 239 : 10, light ? 246 : 34, light ? 244 : 43);
        SECONDARY = Color.rgb(light ? 225 : 28, light ? 238 : 62, light ? 234 : 71);
        BORDER = Color.rgb(light ? 178 : 45, light ? 208 : 91, light ? 202 : 94);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable box(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), BORDER);
        return shape;
    }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }
    private LinearLayout.LayoutParams spaced(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(bottom); return p;
    }

    private LinearLayout card() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackground(box(PANEL, 18));
        return panel;
    }
    private Button action(int label, boolean primary) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(14);
        button.setMinHeight(dp(48));
        button.setTextColor(primary ? Color.rgb(8, 26, 35) : TEXT);
        button.setBackground(box(primary ? MINT : SECONDARY, 12));
        return button;
    }
    private void heading(LinearLayout parent, int label) {
        TextView title = text(getString(label), 17, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(title, spaced(12));
    }

    private AlertDialog showSecureDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        if (!resumed || (lockEnabled() && !unlocked)) return dialog;
        sensitiveDialogs.add(dialog);
        dialog.setOnDismissListener(d -> sensitiveDialogs.remove(dialog));
        dialog.show();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        return dialog;
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLocale.wrap(base));
    }

    private void chooseLanguage() {
        if (paymentSending) {
            Toast.makeText(this, R.string.language_busy, Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = AppLocale.NAMES.clone();
        names[0] = getString(R.string.language_system);
        String saved = AppLocale.saved(this);
        int checked = java.util.Arrays.asList(AppLocale.CODES).indexOf(saved);
        showSecureDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.language_select)
                .setSingleChoiceItems(names, Math.max(0, checked), (dialog, which) -> {
                    if (paymentSending) {
                        Toast.makeText(this, R.string.language_busy, Toast.LENGTH_LONG).show();
                        return;
                    }
                    boolean ok = getSharedPreferences(AppLocale.PREFS, MODE_PRIVATE).edit()
                            .putString("code", AppLocale.CODES[which]).commit();
                    dialog.dismiss();
                    if (ok) recreate();
                    else Toast.makeText(this, R.string.wallet_error, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        NetworkConfig.init(this);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleWalletBack(); }
        });
        loadPalette();
        // Protect wallet views and Android's recent-app previews in every build.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (getSharedPreferences("stompi.display.v1", MODE_PRIVATE).getBoolean("light", false))
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        storeHolder[0] = new WalletStore(this);
        worker.execute(() -> { try { MobileAccess.register(); } catch(Exception ignored) {
            // Offline start remains usable; retry on next start or payment.
        } });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this);
        LinearLayout pages = new LinearLayout(this);
        pages.setOrientation(LinearLayout.VERTICAL);
        pages.setPadding(dp(18), dp(20), dp(18), dp(24));
        scroll.addView(pages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.stompi);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView brandName = text("STOMPI", 22, TEXT);
        brandName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams brandPad = new LinearLayout.LayoutParams(-2, -2);
        brandPad.leftMargin = dp(12);
        brand.addView(brandName, brandPad);
        brand.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        ImageButton settings = new ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setContentDescription(getString(R.string.settings_heading));
        settings.setColorFilter(TEXT);
        settings.setPadding(dp(10), dp(10), dp(10), dp(10));
        settings.setBackground(box(SECONDARY, 20));
        brand.addView(settings, new LinearLayout.LayoutParams(dp(40), dp(40)));
        settings.setOnClickListener(v -> showPage(4));
        pages.addView(brand, spaced(18));

        homePage = new LinearLayout(this);
        homePage.setOrientation(LinearLayout.VERTICAL);
        pages.addView(homePage);
        TextView homeTitle = text(getString(R.string.home_heading), 28, TEXT);
        homeTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        homePage.addView(homeTitle, spaced(16));

        LinearLayout empty = card();
        heading(empty, R.string.empty_wallet_heading);
        empty.addView(text(getString(R.string.empty_wallet_explainer), 14, MUTED), spaced(14));
        Button firstWallet = action(R.string.create_wallet, true);
        empty.addView(firstWallet);
        firstWallet.setOnClickListener(v -> createWallet());
        homePage.addView(empty, spaced(14));
        emptyState = empty;

        LinearLayout walletCard = card();
        walletCard.addView(text(getString(R.string.active_wallet_label), 12, MINT), spaced(6));
        walletDropdown = new Spinner(this);
        walletDropdown.setBackground(box(INPUT, 10));
        walletDropdown.setPadding(dp(12), dp(8), dp(12), dp(8));
        walletCard.addView(walletDropdown, spaced(16));
        walletDropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
                if (index < 0 || index >= dropdownEntries.size()) return;
                WalletStore.Entry chosen = dropdownEntries.get(index);
                WalletStore.Entry current = storeHolder[0].active();
                if (current == null || !current.id.equals(chosen.id)) selectWallet(chosen);
            }
        });
        walletCard.addView(text(getString(R.string.balance_label), 12, MUTED), spaced(6));
        balance = text(getString(R.string.balance_unavailable), 20, TEXT);
        balance.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        walletCard.addView(balance, spaced(8));
        balanceBreakdown = text(getString(R.string.balance_details_unknown),12,MUTED);
        walletCard.addView(balanceBreakdown,spaced(8));
        walletCard.addView(text(getString(R.string.balance_scope), 12, MUTED), spaced(12));
        balanceUpdated = text(getString(R.string.balance_not_checked), 12, MUTED);
        walletCard.addView(balanceUpdated, spaced(8));
        pendingNotice = text("", 13, MINT);
        walletCard.addView(pendingNotice, spaced(8));
        Button checkPendingButton = action(R.string.pending_check, false);
        checkPendingButton.setOnClickListener(v -> checkPending(true));
        walletCard.addView(checkPendingButton, spaced(8));
        Button refreshBalance = action(R.string.refresh_balance, false);
        refreshBalance.setOnClickListener(v -> loadFirstAddressAmount());
        walletCard.addView(refreshBalance);
        homePage.addView(walletCard, spaced(14));
        walletSummary = walletCard;
        Button payments = action(R.string.payments_heading, true);
        payments.setOnClickListener(v -> { showPage(5); refreshPayments(); });
        homePage.addView(payments, spaced(14));
        paymentsButton = payments;

        LinearLayout receiveCard = card();
        heading(receiveCard, R.string.receive_heading);
        receiveCard.addView(text(getString(R.string.receive_hint), 13, MUTED), spaced(10));
        address = new EditText(this);
        address.setTextColor(TEXT);
        address.setTextSize(14);
        address.setBackground(box(INPUT, 10));
        address.setPadding(dp(12), dp(13), dp(12), dp(13));
        address.setSingleLine(false);
        address.setTextIsSelectable(true);
        address.setKeyListener(null);
        try {
            WalletStore wallet = storeHolder[0];
            setReceiveAddress(wallet.active() != null ? StompiWalletKeys.address(wallet.readActive(), 0, 0) : "");
        } catch (Exception exc) { setReceiveAddress(""); }
        address.setHint(R.string.no_wallet_selected);
        receiveCard.addView(address, spaced(12));
        Button copyAddress = action(R.string.copy_address, false);
        receiveCard.addView(copyAddress);
        copyAddress.setOnClickListener(v -> {
            if (storeHolder[0].active() == null || !StompiAddress.isNativeSegwitV0(receiveAddress)) return;
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(
                    ClipData.newPlainText("Stompi address", receiveAddress));
            Toast.makeText(this, R.string.address_copied, Toast.LENGTH_SHORT).show();
        });
        Button receiveQr = action(R.string.qr_receive,false);
        receiveQr.setOnClickListener(v -> showReceiveQr()); receiveCard.addView(receiveQr,spaced(8));
        receivePanel = receiveCard;

        homePage.addView(text(getString(R.string.observer_notice), 12, MUTED));

        activityPage = new LinearLayout(this);
        activityPage.setOrientation(LinearLayout.VERTICAL);
        pages.addView(activityPage);
        heading(activityPage, R.string.activity_heading);
        LinearLayout chainCard = card();
        chainCard.addView(text(getString(R.string.chain_notice), 13, MUTED), spaced(14));
        refresh = action(R.string.check_address, true);
        chainCard.addView(refresh, spaced(12));
        result = text(getString(R.string.first_prompt), 14, TEXT);
        chainCard.addView(result);
        refresh.setOnClickListener(v -> load());
        activityPage.addView(chainCard, spaced(12));
        activityDetails = new LinearLayout(this);
        activityDetails.setOrientation(LinearLayout.VERTICAL);
        activityPage.addView(activityDetails);
        activityPage.addView(text(getString(R.string.privacy_notice), 12, MUTED));

        managePage = new LinearLayout(this);
        managePage.setOrientation(LinearLayout.VERTICAL);
        pages.addView(managePage);
        heading(managePage, R.string.manage_wallets);
        LinearLayout manageCard = card();
        manageCard.addView(text(getString(R.string.manage_explainer), 14, MUTED), spaced(14));
        Button create = action(R.string.create_wallet, true);
        manageCard.addView(create, spaced(10));
        create.setOnClickListener(v -> createWallet());
        Button restore = action(R.string.restore_wallet, false);
        manageCard.addView(restore, spaced(10));
        restore.setOnClickListener(v -> restoreWallet());
        Button choose = action(R.string.wallet_menu, false);
        manageCard.addView(choose, spaced(10));
        choose.setOnClickListener(v -> walletMenu());
        Button remove = action(R.string.delete_wallet, false);
        manageCard.addView(remove);
        remove.setOnClickListener(v -> confirmDelete());
        managePage.addView(manageCard);
        managePage.addView(receiveCard, spaced(14));

        newsPage = new LinearLayout(this);
        newsPage.setOrientation(LinearLayout.VERTICAL);
        pages.addView(newsPage);
        heading(newsPage, R.string.news_heading);
        LinearLayout newsCard = card();
        newsList = new LinearLayout(this);
        newsList.setOrientation(LinearLayout.VERTICAL);
        newsList.addView(text(getString(R.string.news_placeholder), 15, TEXT));
        newsCard.addView(newsList, spaced(12));
        Button newsRefresh = action(R.string.news_refresh, true);
        newsCard.addView(newsRefresh);
        newsRefresh.setOnClickListener(v -> loadNews());
        Button newsArchive = action(R.string.news_archive, false);
        newsArchive.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://stompi.tech/#/news"))));
        newsCard.addView(newsArchive, spaced(8));
        newsPage.addView(newsCard, spaced(14));
        settingsPage = new LinearLayout(this);
        settingsPage.setOrientation(LinearLayout.VERTICAL);
        pages.addView(settingsPage);
        heading(settingsPage, R.string.settings_heading);
        LinearLayout versionCard = card();
        heading(versionCard, R.string.app_version_heading);
        String versionText;
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(),0);
            long build = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            versionText = info.versionName + " · " + getString(R.string.app_build_heading) + " " + build;
        } catch(Exception e) { versionText = getString(R.string.value_unavailable); }
        TextView versionLabel = text(versionText,14,TEXT); versionLabel.setTextIsSelectable(true);
        versionCard.addView(versionLabel); settingsPage.addView(versionCard,spaced(14));
        LinearLayout securityCard = card();
        heading(securityCard,R.string.security_heading);
        securityCard.addView(text(getString(R.string.security_info),13,MUTED),spaced(12));
        Button securityToggle = action(lockEnabled()?R.string.security_disable:R.string.security_enable,false);
        securityToggle.setOnClickListener(v -> {
            if(paymentSending) return;
            authenticate(() -> {
                boolean next = !lockEnabled();
                if(getSharedPreferences("stompi.security.v1",MODE_PRIVATE).edit().putBoolean("lock",next).commit()) {
                    unlocked = true;
                    securityToggle.setText(next?R.string.security_disable:R.string.security_enable);
                }
            });
        });
        securityCard.addView(securityToggle); settingsPage.addView(securityCard,spaced(14));
        LinearLayout languageCard = card();
        heading(languageCard, R.string.language_heading);
        languageCard.addView(text(getString(R.string.language_scope), 13, MUTED), spaced(12));
        Button languageButton = action(R.string.language_select, false);
        String chosen = AppLocale.saved(this);
        int languageIndex = java.util.Arrays.asList(AppLocale.CODES).indexOf(chosen);
        languageButton.setText(getString(R.string.language_select) + " · "
                + (chosen.isEmpty() ? getString(R.string.language_system) : AppLocale.NAMES[languageIndex]));
        languageButton.setOnClickListener(v -> chooseLanguage());
        languageCard.addView(languageButton);
        settingsPage.addView(languageCard, spaced(14));
        LinearLayout networkCard = card();
        heading(networkCard, R.string.network_heading);
        networkCard.addView(text(NetworkConfig.base(),13,MUTED));
        Button networkButton=action(R.string.network_change,false);
        networkButton.setOnClickListener(v -> chooseNetworkServer());
        networkCard.addView(networkButton);
        networkCard.addView(text(getString(R.string.network_warning),13,MUTED),spaced(12));
        settingsPage.addView(networkCard,spaced(14));
        LinearLayout notificationCard = card();
        heading(notificationCard, R.string.notifications_heading);
        notificationCard.addView(text(getString(R.string.notifications_scope), 13, MUTED), spaced(12));
        notificationsButton = action(R.string.notifications_enable, false);
        notificationCard.addView(notificationsButton);
        notificationsButton.setOnClickListener(v -> toggleNotifications());
        updateNotificationButton();
        settingsPage.addView(notificationCard, spaced(14));
        LinearLayout displayCard = card();
        heading(displayCard, R.string.display_heading);
        displayCard.addView(text(getString(R.string.display_scope), 13, MUTED), spaced(10));
        Button lightButton = action(R.string.display_light, false);
        lightButton.setOnClickListener(v -> changeDisplay(true));
        displayCard.addView(lightButton, spaced(8));
        Button darkButton = action(R.string.display_dark, false);
        darkButton.setOnClickListener(v -> changeDisplay(false));
        displayCard.addView(darkButton);
        settingsPage.addView(displayCard);

        paymentsPage = new LinearLayout(this);
        paymentsPage.setOrientation(LinearLayout.VERTICAL);
        pages.addView(paymentsPage);
        heading(paymentsPage, R.string.payments_heading);
        LinearLayout paymentCard = card();
        paymentCard.addView(text(getString(R.string.payments_not_ready), 14, MUTED), spaced(12));
        recipientName = new EditText(this);
        recipientName.setSingleLine(true);
        recipientName.setHint(R.string.contact_name);
        recipientName.setTextColor(TEXT);
        recipientName.setHintTextColor(MUTED);
        recipientName.setBackground(box(INPUT, 10));
        paymentCard.addView(recipientName, spaced(8));
        recipientAddress = new EditText(this);
        recipientAddress.setSingleLine(true);
        recipientAddress.setHint(R.string.contact_address);
        recipientAddress.setTextColor(TEXT);
        recipientAddress.setHintTextColor(MUTED);
        recipientAddress.setBackground(box(INPUT, 10));
        paymentCard.addView(recipientAddress, spaced(8));
        Button scan = action(R.string.qr_scan,false);
        scan.setOnClickListener(v -> scanPaymentQr()); paymentCard.addView(scan,spaced(8));
        Button chooseContact = action(R.string.contacts_open, false);
        chooseContact.setOnClickListener(v -> openContacts());
        paymentCard.addView(chooseContact, spaced(8));
        paymentAmount = new EditText(this);
        paymentAmount.setSingleLine(true);
        paymentAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        paymentAmount.setHint(R.string.payment_amount);
        paymentAmount.setTextColor(TEXT);
        paymentAmount.setHintTextColor(MUTED);
        paymentAmount.setBackground(box(INPUT, 10));
        paymentCard.addView(paymentAmount, spaced(8));
        Button saveContact = action(R.string.contact_save, false);
        saveContact.setOnClickListener(v -> saveRecipient());
        paymentCard.addView(saveContact, spaced(8));
        Button prepare = action(R.string.payment_prepare, true);
        prepare.setOnClickListener(v -> preparePayment());
        paymentCard.addView(prepare);
        paymentsPage.addView(paymentCard, spaced(14));
        LinearLayout historyCard = card();
        heading(historyCard, R.string.payments_recent);
        paymentHistory = new LinearLayout(this);
        paymentHistory.setOrientation(LinearLayout.VERTICAL);
        paymentHistory.addView(text(getString(R.string.payments_no_history), 13, MUTED));
        historyCard.addView(paymentHistory);
        paymentsPage.addView(historyCard);

        LinearLayout navigation = new LinearLayout(this);
        boolean compact = getResources().getConfiguration().screenWidthDp < 480
                || getResources().getConfiguration().fontScale > 1.15f;
        navigation.setOrientation(LinearLayout.VERTICAL);
        navigation.setPadding(dp(8), dp(5), dp(8), dp(5));
        navigation.setBackgroundColor(PANEL);
        int[] labels = {R.string.nav_home, R.string.nav_activity, R.string.nav_wallets, R.string.nav_news};
        LinearLayout row = null;
        for (int i = 0; i < labels.length; i++) {
            if (row == null || (compact && i % 2 == 0)) {
                row = new LinearLayout(this);
                navigation.addView(row, new LinearLayout.LayoutParams(-1, dp(54)));
            }
            final int target = i;
            Button tab = action(labels[i], false);
            tab.setTextSize(compact ? 13 : 12);
            tab.setSingleLine(true);
            tab.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, dp(48), 1);
            tabParams.leftMargin = dp(3);
            tabParams.rightMargin = dp(3);
            row.addView(tab, tabParams);
            tab.setOnClickListener(v -> {
                showPage(target);
                scroll.scrollTo(0, 0);
                if (target == 0) loadFirstAddressAmount();
                if (target == 1) load();
                if (target == 3) loadNews();
            });
        }
        root.addView(navigation);
        // Android 15+ draws app content behind system bars. Keep navigation above the gesture area.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        android.widget.FrameLayout shell = new android.widget.FrameLayout(this);
        shell.addView(root);
        walletContent = root;
        root.setVisibility(lockEnabled()?View.INVISIBLE:View.VISIBLE);
        LinearLayout shield = new LinearLayout(this);
        shield.setOrientation(LinearLayout.VERTICAL); shield.setGravity(Gravity.CENTER);
        shield.setBackgroundColor(BG); shield.setClickable(true); shield.setFocusable(true);
        shield.setPadding(dp(24),dp(24),dp(24),dp(24));
        Button unlock = action(R.string.security_unlock,true);
        unlock.setOnClickListener(v -> unlockApp()); shield.addView(unlock);
        shell.addView(shield,new android.widget.FrameLayout.LayoutParams(-1,-1));
        lockScreen = shield;
        shield.setVisibility(lockEnabled()?View.VISIBLE:View.GONE);
        setContentView(shell);
        root.requestApplyInsets();
        updateSelectedLabel();
        showPage(0);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (lockEnabled() && (!unlocked || (backgroundAt > 0
                && android.os.SystemClock.elapsedRealtime()-backgroundAt >= LOCK_AFTER_MS))) {
            unlocked = false; lockScreen.setVisibility(View.VISIBLE); walletContent.setVisibility(View.INVISIBLE); unlockApp();
        } else if (lockScreen != null) { lockScreen.setVisibility(View.GONE); walletContent.setVisibility(View.VISIBLE); }
        refreshHandler.removeCallbacks(periodicRefresh);
        if (balance != null) loadFirstAddressAmount();
        if (pendingNotice != null) checkPending(false);
        refreshHandler.postDelayed(periodicRefresh, 30000);
    }

    @Override protected void onPause() {
        resumed = false;
        refreshHandler.removeCallbacks(periodicRefresh);
        backgroundAt = android.os.SystemClock.elapsedRealtime();
        // Conceal wallet contents immediately; the five-minute grace applies to authentication.
        if (lockEnabled() && lockScreen != null) { lockScreen.setVisibility(View.VISIBLE); walletContent.setVisibility(View.INVISIBLE); }
        for(AlertDialog dialog : new java.util.ArrayList<>(sensitiveDialogs)) dialog.dismiss();
        super.onPause();
    }

    private void showPage(int target) {
        page = target;
        homePage.setVisibility(target == 0 ? View.VISIBLE : View.GONE);
        activityPage.setVisibility(target == 1 ? View.VISIBLE : View.GONE);
        managePage.setVisibility(target == 2 ? View.VISIBLE : View.GONE);
        newsPage.setVisibility(target == 3 ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(target == 4 ? View.VISIBLE : View.GONE);
        paymentsPage.setVisibility(target == 5 ? View.VISIBLE : View.GONE);
    }

    private void saveRecipient() {
        WalletStore.Entry wallet = storeHolder[0].active();
        if (wallet == null) { Toast.makeText(this, R.string.no_wallet_selected, Toast.LENGTH_SHORT).show(); return; }
        try {
            new PaymentContacts(this).save(wallet.id, recipientName.getText().toString(), recipientAddress.getText().toString());
            recipientName.setText(""); recipientAddress.setText("");
            Toast.makeText(this, R.string.contact_saved, Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, R.string.contact_invalid, Toast.LENGTH_LONG).show(); }
    }

    private void preparePayment() {
        WalletStore.Entry wallet = storeHolder[0].active();
        if (wallet != null && !pendingTxid(wallet.id).isEmpty()) {
            checkPending(true);
            return;
        }
        String source = receiveAddress;
        String target = recipientAddress.getText().toString().trim();
        long amount;
        try {
            if (wallet == null || !StompiAddress.isNativeSegwitV0(source)
                    || !StompiAddress.isNativeSegwitV0(target)) throw new IllegalArgumentException();
            amount = new BigDecimal(paymentAmount.getText().toString().trim().replace(',', '.'))
                    .movePointRight(8).longValueExact();
            if (amount < 1000) throw new IllegalArgumentException();
        } catch (Exception e) {
            Toast.makeText(this, R.string.payment_invalid, Toast.LENGTH_LONG).show(); return;
        }
        worker.execute(() -> {
            try {
                JSONObject utxos = UpdatesWorker.fetch(API.replace("/address/", "/utxos/") + source);
                PaymentPreflight.Result preview = PaymentPreflight.create(
                        storeHolder[0].read(wallet.id), source, target, amount, utxos);
                runOnUiThread(() -> {
                    WalletStore.Entry active = storeHolder[0].active();
                    if (active == null || !active.id.equals(wallet.id) || !source.equals(receiveAddress) || !pendingTxid(wallet.id).isEmpty()) return;
                    ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                            .hideSoftInputFromWindow(paymentAmount.getWindowToken(), 0);
                    paymentAmount.clearFocus();
                    TextView details = text(getString(R.string.payment_preview, target, coins(amount),
                            coins(preview.feeSats), coins(preview.changeSats), preview.txid), 15,
                            Color.rgb(25, 39, 48));
                    details.setTextIsSelectable(true);
                    ScrollView scroll = new ScrollView(this);
                    scroll.setPadding(dp(24), dp(12), dp(24), dp(12));
                    scroll.addView(details);
                    showSecureDialog(new AlertDialog.Builder(this).setTitle(R.string.payment_preview_title).setView(scroll)
                            .setNegativeButton(android.R.string.cancel, (dialog, which) -> {})
                            .setNeutralButton(R.string.payment_copy_hex, (dialog, which) -> {
                                ClipData clip = ClipData.newPlainText("Stompi signed transaction", preview.hex);
                                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(clip);
                                Toast.makeText(this, R.string.payment_hex_copied, Toast.LENGTH_LONG).show();
                            })
                            .setPositiveButton(R.string.payment_send, (dialog, which) -> confirmPayment(
                                    wallet.id, source, target, amount, preview)));
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.payment_preflight_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmPayment(String walletId, String source, String target, long amount,
                                PaymentPreflight.Result preview) {
        showSecureDialog(new AlertDialog.Builder(this).setTitle(R.string.payment_confirm_title)
                .setMessage(getString(R.string.payment_confirm, target, coins(amount), coins(preview.feeSats)))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.payment_send, (d, which) -> {
                    if(lockEnabled()) authenticate(() -> broadcastPayment(walletId, source, preview));
                    else broadcastPayment(walletId, source, preview);
                }));
    }

    private void broadcastPayment(String walletId, String source, PaymentPreflight.Result preview) {
        if(!resumed || (lockEnabled() && !unlocked)) return;
        WalletStore.Entry active = storeHolder[0].active();
        if (paymentSending || active == null || !walletId.equals(active.id) || !source.equals(receiveAddress) || !pendingTxid(walletId).isEmpty()) return;
        paymentSending = true;
        Toast.makeText(this, R.string.payment_sending, Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            HttpURLConnection connection = null;
            String error = null;
            boolean success = false;
            boolean reserved = false;
            try {
                JSONObject authorization = MobileAccess.broadcastPayload(preview.hex);
                if (!getPreferences(MODE_PRIVATE).edit().putString("pending." + walletId, preview.txid).commit())
                    throw new IllegalStateException("Reservation storage failed");
                reserved = true;
                runOnUiThread(() -> updatePendingNotice());
                connection = (HttpURLConnection) new URL(NetworkConfig.base()+"/api/mobile/broadcast").openConnection();
                connection.setRequestMethod("POST");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                byte[] body = authorization.toString().getBytes(StandardCharsets.UTF_8);
                if (body.length > 20000) throw new IllegalStateException("Transaction too large");
                connection.setFixedLengthStreamingMode(body.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(body); }
                int status = connection.getResponseCode();
                if (status == 200) {
                    try (InputStream input = connection.getInputStream()) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buffer = new byte[512]; int n;
                        while ((n = input.read(buffer)) != -1) {
                            bytes.write(buffer, 0, n);
                            if (bytes.size() > 4096) throw new IllegalStateException("Response too large");
                        }
                        success = preview.txid.equals(new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8))
                                .getString("txid"));
                    }
                    if (!success) error = "Unexpected transaction ID";
                } else {
                    error = "HTTP " + status;
                    if (status == 400 || status == 403 || status == 413 || status == 415 || status == 422 || status == 429)
                        getPreferences(MODE_PRIVATE).edit().remove("pending." + walletId).commit();
                }
            } catch (Exception e) { error = "Network or node unavailable"; }
            finally { if (connection != null) connection.disconnect(); }
            final boolean sent = success;
            final boolean wasReserved = reserved;
            final String detail = error;
            runOnUiThread(() -> {
                paymentSending = false;
                updatePendingNotice();
                if (sent) {
                    paymentAmount.setText("");
                    showSecureDialog(new AlertDialog.Builder(this).setTitle(R.string.payment_sent)
                            .setMessage(preview.txid).setPositiveButton(android.R.string.ok, null));
                    loadFirstAddressAmount();
                } else {
                    showSecureDialog(new AlertDialog.Builder(this).setTitle(R.string.payment_failed)
                            .setMessage(wasReserved ? getString(R.string.payment_unknown, detail == null ? "" : detail,
                                    preview.txid) : getString(R.string.payment_preflight_failed)).setPositiveButton(android.R.string.ok, null));
                }
            });
        });
    }

    private String pendingTxid(String walletId) {
        return getPreferences(MODE_PRIVATE).getString("pending." + walletId, "");
    }

    private void updatePendingNotice() {
        WalletStore.Entry wallet = storeHolder[0].active();
        String txid = wallet == null ? "" : pendingTxid(wallet.id);
        String last = wallet == null ? "" : getPreferences(MODE_PRIVATE).getString("confirmed."+wallet.id, "");
        pendingNotice.setText(txid.isEmpty() ? (last.isEmpty()?"":getString(R.string.status_confirmed)+" · "+last)
                : getString(R.string.pending_notice,
                txid.substring(0, 8) + "…" + txid.substring(txid.length() - 6)));
    }

    private void checkPending(boolean interactive) {
        WalletStore.Entry wallet = storeHolder[0].active();
        if (wallet == null) return;
        String txid = pendingTxid(wallet.id);
        if (txid.isEmpty()) {
            updatePendingNotice();
            if (interactive) Toast.makeText(this, R.string.pending_none, Toast.LENGTH_SHORT).show();
            return;
        }
        worker.execute(() -> {
            String status;
            try {
                JSONObject data = UpdatesWorker.fetch("https://stompi.tech/api/transaction/status/" + txid);
                if (!txid.equals(data.getString("txid"))) throw new IllegalStateException("Transaction mismatch");
                status = data.getString("status");
            } catch (Exception e) { status = "unavailable"; }
            String shown = status;
            runOnUiThread(() -> {
                WalletStore.Entry current = storeHolder[0].active();
                if (current == null || !wallet.id.equals(current.id) || !txid.equals(pendingTxid(wallet.id))) return;
                if ("confirmed".equals(shown)) {
                    getPreferences(MODE_PRIVATE).edit().putString("confirmed."+wallet.id,txid).remove("pending." + wallet.id).commit();
                    updatePendingNotice();
                    if (interactive) Toast.makeText(this, R.string.pending_confirmed, Toast.LENGTH_SHORT).show();
                    loadFirstAddressAmount();
                } else {
                    pendingNotice.setText(getString("mempool".equals(shown)?R.string.status_unconfirmed:R.string.status_unclear)+" · "+txid);
                    if (!interactive) return;
                    AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(R.string.pending_title)
                            .setMessage(getString("mempool".equals(shown) ? R.string.pending_mempool
                                    : "unknown".equals(shown) ? R.string.pending_unknown : R.string.pending_unavailable,
                                    txid)).setNegativeButton(android.R.string.ok, null);
                    showSecureDialog(dialog);
                }
            });
        });
    }

    private void openContacts() {
        WalletStore.Entry wallet = storeHolder[0].active();
        if (wallet == null) return;
        java.util.List<PaymentContacts.Contact> contacts = new PaymentContacts(this).list(wallet.id);
        if (contacts.isEmpty()) {
            showSecureDialog(new AlertDialog.Builder(this).setTitle(R.string.contacts_heading)
                    .setMessage(R.string.contacts_empty).setPositiveButton(android.R.string.ok, null));
            return;
        }
        String[] labels = new String[contacts.size()];
        for (int i = 0; i < contacts.size(); i++) {
            PaymentContacts.Contact contact = contacts.get(i);
            labels[i] = contact.name + "\n" + contact.address;
        }
        final int[] selected = {-1};
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.contacts_heading)
                .setSingleChoiceItems(labels, -1, (d, index) -> {
                    selected[0] = index;
                    ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.contact_use, (d, which) -> {
                    WalletStore.Entry active = storeHolder[0].active();
                    if (selected[0] < 0 || active == null || !wallet.id.equals(active.id)) return;
                    PaymentContacts.Contact contact = contacts.get(selected[0]);
                    recipientName.setText(contact.name);
                    recipientAddress.setText(contact.address);
                    paymentAmount.requestFocus();
                    Toast.makeText(this, R.string.contact_selected, Toast.LENGTH_SHORT).show();
                }).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false));
        dialog.show();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void paymentHistoryMessage(int label) {
        paymentHistory.removeAllViews();
        paymentHistory.addView(text(getString(label), 13, MUTED));
    }

    private void showPaymentHistory(JSONArray history) throws Exception {
        paymentHistory.removeAllViews();
        if (history.length() == 0) {
            paymentHistoryMessage(R.string.payments_no_history);
            return;
        }
        for (int i = 0; i < Math.min(5, history.length()); i++) {
            JSONObject tx = history.getJSONObject(i);
            long received = tx.getLong("received"), sent = tx.getLong("sent");
            long net = Math.subtractExact(received, sent);
            int label = net > 0 ? R.string.activity_incoming : net < 0
                    ? R.string.activity_outgoing : R.string.activity_self;
            String id = tx.getString("txid");
            int height = tx.getInt("height");
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(box(INPUT, 12));
            TextView title = text(getString(label) + "  " + (net > 0 ? "+" : "") + coins(net), 16, TEXT);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            row.addView(title, spaced(5));
            row.addView(text(getString(R.string.activity_block, height) + " · "
                    + id.substring(0, 8) + "…" + id.substring(id.length() - 6), 12, MUTED));
            row.setOnClickListener(v -> showSecureDialog(new AlertDialog.Builder(this)
                    .setTitle(R.string.activity_transaction)
                    .setMessage(getString(R.string.activity_details, id, height, coins(received), coins(sent)))
                    .setNeutralButton(R.string.activity_copy_txid, (dialog, which) -> {
                        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(
                                ClipData.newPlainText("Stompi transaction ID", id));
                        Toast.makeText(this, R.string.activity_txid_copied, Toast.LENGTH_SHORT).show();
                    })
                    .setPositiveButton(android.R.string.ok, null)));
            paymentHistory.addView(row, spaced(8));
        }
    }

    private void refreshPayments() {
        WalletStore.Entry wallet = storeHolder[0].active();
        String requested = receiveAddress;
        if (wallet == null || !StompiAddress.isNativeSegwitV0(requested)) {
            paymentHistoryMessage(R.string.payments_no_history); return;
        }
        paymentHistoryMessage(R.string.loading);
        worker.execute(() -> {
            JSONArray history = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(NetworkConfig.rewrite(API + requested)).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(6000); connection.setReadTimeout(6000);
                if (connection.getResponseCode() != 200) throw new IllegalStateException("Explorer unavailable");
                try (InputStream input = connection.getInputStream()) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] chunk = new byte[4096]; int n;
                    while ((n = input.read(chunk)) != -1) {
                        bytes.write(chunk, 0, n);
                        if (bytes.size() > 262144) throw new IllegalStateException("Response too large");
                    }
                    JSONObject data = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                    if (!requested.equals(data.getString("address"))) throw new IllegalStateException("Address mismatch");
                    history = data.getJSONArray("history");
                }
            } catch (Exception ignored) { /* Show a localized error below. */ }
            finally { if (connection != null) connection.disconnect(); }
            JSONArray shown = history;
            runOnUiThread(() -> {
                WalletStore.Entry current = storeHolder[0].active();
                if (current == null || !current.id.equals(wallet.id) || !requested.equals(receiveAddress)) return;
                if (shown == null) { paymentHistoryMessage(R.string.balance_check_failed); return; }
                try { showPaymentHistory(shown); }
                catch (Exception ignored) { paymentHistoryMessage(R.string.balance_check_failed); }
            });
        });
    }

    private void changeDisplay(boolean light) {
        if(paymentSending) { Toast.makeText(this,R.string.language_busy,Toast.LENGTH_LONG).show(); return; }
        if (getSharedPreferences("stompi.display.v1", MODE_PRIVATE).edit().putBoolean("light", light).commit())
            recreate();
        else Toast.makeText(this, R.string.wallet_error, Toast.LENGTH_SHORT).show();
    }

    private void handleWalletBack() {
        if ((!lockEnabled() || unlocked) && page != 0) { showPage(0); return; }
        if (paymentSending) {
            Toast.makeText(this, R.string.payment_sending, Toast.LENGTH_LONG).show();
            return;
        }
        AlertDialog exitDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.exit_title)
                .setMessage(R.string.exit_message)
                .setNegativeButton(R.string.exit_stay, null)
                .setPositiveButton(R.string.exit_confirm, (dialog, which) -> {
                    if (!paymentSending) finish();
                }).create();
        // Non-sensitive exit confirmation is also available on the locked screen.
        sensitiveDialogs.add(exitDialog);
        exitDialog.setOnDismissListener(dialog -> sensitiveDialogs.remove(exitDialog));
        exitDialog.show();
        exitDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void newsMessage(int message) {
        newsList.removeAllViews();
        newsList.addView(text(getString(message), 15, MUTED));
    }

    private void loadNews() {
        newsMessage(R.string.loading);
        worker.execute(() -> {
            try {
                JSONArray items = UpdatesWorker.fetchNews().getJSONArray("items");
                if (items.length() > 30) throw new IllegalStateException("News feed too large");
                String newsLanguage = AppLocale.code(this);
                java.util.List<Bitmap> pictures = new java.util.ArrayList<>();
                for (int i = 0; i < Math.min(items.length(), 2); i++) {
                    String path = items.getJSONObject(i).optString("image");
                    Bitmap picture = null;
                    if (path.matches("/news-images/[0-9a-f]{32}\\.jpg")) {
                        HttpURLConnection connection = null;
                        try {
                            connection = (HttpURLConnection) new URL(NetworkConfig.base() + path).openConnection();
                            connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
                            if (connection.getResponseCode() == 200 && connection.getContentLengthLong() <= 700000) {
                                try (InputStream stream = connection.getInputStream()) {
                                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                                    byte[] chunk = new byte[8192]; int n;
                                    while ((n = stream.read(chunk)) != -1) {
                                        output.write(chunk, 0, n);
                                        if (output.size() > 700000) break;
                                    }
                                    if (output.size() <= 700000) {
                                        byte[] data = output.toByteArray();
                                        BitmapFactory.Options options = new BitmapFactory.Options();
                                        options.inJustDecodeBounds = true;
                                        BitmapFactory.decodeByteArray(data, 0, data.length, options);
                                        if (options.outWidth > 0 && options.outHeight > 0 && options.outWidth <= 1200 && options.outHeight <= 1200) {
                                            options.inJustDecodeBounds = false;
                                            picture = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) { /* The news text remains available. */ }
                        finally { if (connection != null) connection.disconnect(); }
                    }
                    pictures.add(picture);
                }
                runOnUiThread(() -> {
                    newsList.removeAllViews();
                    if (items.length() == 0) { newsMessage(R.string.news_empty); return; }
                    for (int i = 0; i < pictures.size(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;
                        LinearLayout row = card();
                        Bitmap picture = pictures.get(i);
                        if (picture != null) {
                            ImageView view = new ImageView(this);
                            view.setImageBitmap(picture);
                            view.setAdjustViewBounds(true);
                            view.setMaxHeight(dp(220));
                            row.addView(view, spaced(8));
                        }
                        TextView title = text(AppLocale.news(item, "title", newsLanguage), 17, TEXT);
                        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                        row.addView(title, spaced(8));
                        row.addView(text(AppLocale.news(item, "body", newsLanguage), 14, TEXT));
                        newsList.addView(row, spaced(10));
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> newsMessage(R.string.news_unavailable)); }
        });
    }

    private void updateNotificationButton() {
        boolean enabled = getSharedPreferences("stompi.notifications.v1", MODE_PRIVATE)
                .getBoolean("enabled", false);
        notificationsButton.setText(enabled ? R.string.notifications_disable : R.string.notifications_enable);
    }

    private void toggleNotifications() {
        boolean enabled = getSharedPreferences("stompi.notifications.v1", MODE_PRIVATE)
                .getBoolean("enabled", false);
        boolean saved = getSharedPreferences("stompi.notifications.v1", MODE_PRIVATE)
                .edit().putBoolean("enabled", !enabled).commit();
        if (!saved) { newsMessage(R.string.wallet_error); return; }
        updateNotificationButton();
        if (enabled) {
            androidx.work.WorkManager.getInstance(this).cancelUniqueWork("stompi-updates");
            Toast.makeText(this, R.string.notifications_disabled, Toast.LENGTH_SHORT).show();
            return;
        }
        UpdatesWorker.schedule(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 102);
        Toast.makeText(this, R.string.notifications_requested, Toast.LENGTH_LONG).show();
    }

    private void createWallet() {
        promptName(name -> worker.execute(() -> {
            try {
                String mnemonic = StompiWalletKeys.newMnemonic();
                String derived = StompiWalletKeys.address(mnemonic, 0, 0);
                runOnUiThread(() -> showBackup(mnemonic, derived, storeHolder[0], name));
            } catch (Exception e) { runOnUiThread(() -> result.setText(getString(R.string.wallet_error))); }
        }));
    }

    private interface NameSelected { void accept(String name); }
    private void promptName(NameSelected next) {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        int number = 1;
        java.util.List<WalletStore.Entry> existing = storeHolder[0].list();
        boolean used;
        do {
            used = false;
            String candidate = getString(R.string.default_wallet_name, number);
            for (WalletStore.Entry entry : existing) if (entry.name.equalsIgnoreCase(candidate)) used = true;
            if (used) number++;
        } while (used);
        name.setText(getString(R.string.default_wallet_name, number));
        showSecureDialog(new AlertDialog.Builder(this).setTitle(R.string.wallet_name_title).setView(name)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {})
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String value = name.getText().toString().trim();
                    boolean duplicate = false;
                    for (WalletStore.Entry entry : storeHolder[0].list())
                        if (entry.name.equalsIgnoreCase(value)) duplicate = true;
                    if (value.isEmpty() || value.length() > 40 || duplicate) {
                        result.setText(getString(R.string.wallet_name_invalid)); return;
                    }
                    next.accept(value);
                }));
    }

    private void updateSelectedLabel() {
        WalletStore.Entry active = storeHolder[0].active();
        dropdownEntries = storeHolder[0].list();
        java.util.List<String> names = new java.util.ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < dropdownEntries.size(); i++) {
            names.add(dropdownEntries.get(i).name);
            if (active != null && active.id.equals(dropdownEntries.get(i).id)) selectedIndex = i;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, names) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView label = (TextView) super.getView(position, convertView, parent);
                label.setTextColor(TEXT);
                label.setTextSize(18);
                return label;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        walletDropdown.setAdapter(adapter);
        if (active != null) walletDropdown.setSelection(selectedIndex);
        walletSummary.setVisibility(active == null ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(active == null ? View.VISIBLE : View.GONE);
        receivePanel.setVisibility(active == null ? View.GONE : View.VISIBLE);
        paymentsButton.setVisibility(active == null ? View.GONE : View.VISIBLE);
        updatePendingNotice();
        balance.setText(R.string.balance_unavailable);
    }

    private void setReceiveAddress(String value) {
        receiveAddress = value;
        address.setText(value);
    }

    private void loadFirstAddressAmount() {
        WalletStore.Entry active = storeHolder[0].active();
        String requestedAddress = receiveAddress;
        if (active == null || !StompiAddress.isNativeSegwitV0(requestedAddress)) {
            balance.setText(R.string.balance_unavailable);
            balanceUpdated.setText(R.string.balance_not_checked);
            return;
        }
        balance.setText(R.string.loading);
        worker.execute(() -> {
            String display;
            String breakdown = getString(R.string.balance_details_unknown);
            boolean checked = false;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(NetworkConfig.rewrite(API.replace("/address/", "/utxos/")
                        + requestedAddress)).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                if (connection.getResponseCode() != 200) throw new IllegalStateException("Explorer unavailable");
                try (InputStream stream = connection.getInputStream()) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] chunk = new byte[4096]; int size;
                    while ((size = stream.read(chunk)) != -1) {
                        bytes.write(chunk, 0, size);
                        if (bytes.size() > 262144) throw new IllegalStateException("Response too large");
                    }
                    JSONObject data = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                    if (!requestedAddress.equals(data.getString("address"))) throw new IllegalStateException("Address mismatch");
                    if (!data.getBoolean("synced")) throw new IllegalStateException("Index behind node");
                    boolean complete = data.getBoolean("complete");
                    long sats = data.getLong(complete ? "spendable_sats" : "sample_sats");
                    if (sats < 0) throw new IllegalStateException("Invalid amount");
                    display = complete ? coins(sats) : getString(R.string.balance_at_least, coins(sats));
                    checked = true;
                    breakdown = getString(R.string.balance_pending)+": "
                            +(data.has("unconfirmed_sats")&&!data.isNull("unconfirmed_sats")
                            ? coins(data.getLong("unconfirmed_sats")) : getString(R.string.value_unavailable));
                }
            } catch (Exception e) { display = getString(R.string.balance_unavailable); }
            finally { if (connection != null) connection.disconnect(); }
            String shown = display;
            String details = breakdown;
            boolean success = checked;
            runOnUiThread(() -> {
                WalletStore.Entry current = storeHolder[0].active();
                if (current != null && current.id.equals(active.id) && requestedAddress.equals(receiveAddress)) {
                    balance.setText(shown);
                    balanceBreakdown.setText(details);
                    if(success) {
                        balanceSuccessAt = java.text.DateFormat.getDateTimeInstance().format(new java.util.Date());
                        getPreferences(MODE_PRIVATE).edit().putString("balance.checked."+active.id,balanceSuccessAt).apply();
                    } else balanceSuccessAt = getPreferences(MODE_PRIVATE).getString("balance.checked."+active.id,"");
                    balanceUpdated.setText(shown.equals(getString(R.string.balance_unavailable))
                            ? getString(R.string.balance_check_failed)+" · "+getString(R.string.balance_last_checked,balanceSuccessAt.isEmpty()?getString(R.string.value_unavailable):balanceSuccessAt)
                            : getString(R.string.balance_last_checked,
                                    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date())));
                }
            });
        });
    }

    private void walletMenu() {
        java.util.List<WalletStore.Entry> entries = storeHolder[0].list();
        if (entries.isEmpty()) { result.setText(getString(R.string.no_wallet_selected)); return; }
        String[] names = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) names[i] = entries.get(i).name;
        showSecureDialog(new AlertDialog.Builder(this).setTitle(R.string.wallet_menu)
                .setItems(names, (d, position) -> selectWallet(entries.get(position))));
    }

    private void selectWallet(WalletStore.Entry selected) {
        if(paymentSending) return;
        try {
            WalletStore.Entry active = storeHolder[0].active();
            if (active != null && active.id.equals(selected.id)) { showPage(0); return; }
            storeHolder[0].select(selected.id);
            setReceiveAddress("");
            result.setText(getString(R.string.loading_wallet));
            updateSelectedLabel();
            showPage(0);
            worker.execute(() -> {
                try {
                    String derived = StompiWalletKeys.address(storeHolder[0].read(selected.id), 0, 0);
                    runOnUiThread(() -> {
                        WalletStore.Entry current = storeHolder[0].active();
                        if (current != null && current.id.equals(selected.id)) {
                            setReceiveAddress(derived);
                            loadFirstAddressAmount();
                            result.setText(getString(R.string.selected_wallet, selected.name));
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> result.setText(getString(R.string.wallet_error)));
                }
            });
        } catch (Exception e) { result.setText(getString(R.string.wallet_error)); }
    }

    private void confirmDelete() {
        if(paymentSending) return;
        WalletStore.Entry selected = storeHolder[0].active();
        if (selected == null) return;
        EditText answer = new EditText(this);
        answer.setSingleLine(true);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_wallet_named, selected.name))
                .setMessage(getString(R.string.delete_wallet_warning, selected.name))
                .setView(answer)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {})
                .setPositiveButton(R.string.delete_wallet, null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            if (!selected.name.equals(answer.getText().toString().trim())) {
                answer.setError(getString(R.string.delete_name_mismatch)); return;
            }
            try {
                storeHolder[0].delete(selected.id);
                new PaymentContacts(this).deleteWallet(selected.id);
                WalletStore.Entry active = storeHolder[0].active();
                setReceiveAddress("");
                updateSelectedLabel();
                showPage(0);
                if (active != null) {
                    worker.execute(() -> {
                        try {
                            String derived = StompiWalletKeys.address(storeHolder[0].read(active.id), 0, 0);
                            runOnUiThread(() -> {
                                if (storeHolder[0].active() != null && storeHolder[0].active().id.equals(active.id))
                                    setReceiveAddress(derived);
                                    loadFirstAddressAmount();
                            });
                        } catch (Exception e) { runOnUiThread(() -> result.setText(getString(R.string.wallet_error))); }
                    });
                }
                result.setText(getString(R.string.wallet_deleted));
                dialog.dismiss();
            } catch (Exception e) { result.setText(getString(R.string.wallet_error)); }
        }));
        dialog.show();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void showBackup(String mnemonic, String derived, WalletStore store, String name) {
        String[] words = mnemonic.split(" ");
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(10), dp(12), dp(10));
        page.addView(text(getString(R.string.backup_instructions), 14, TEXT), spaced(14));
        GridLayout grid = new GridLayout(this);
        int columns = getResources().getConfiguration().screenWidthDp < 480
                || getResources().getConfiguration().fontScale > 1.15f ? 2 : 4;
        grid.setColumnCount(columns);
        for (int i = 0; i < words.length; i++) {
            TextView cell = text((i + 1) + ". " + words[i], 12, TEXT);
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setPadding(dp(5), dp(10), dp(3), dp(10));
            cell.setBackground(box(PANEL, 6));
            GridLayout.LayoutParams cellParams = new GridLayout.LayoutParams(
                    GridLayout.spec(i / columns), GridLayout.spec(i % columns, 1f));
            cellParams.width = 0;
            cellParams.bottomMargin = dp(4);
            cellParams.rightMargin = dp(4);
            grid.addView(cell, cellParams);
        }
        page.addView(grid, spaced(10));
        Button copy = new Button(this);
        copy.setText(R.string.copy_words);
        page.addView(copy, spaced(8));
        copy.setOnClickListener(v -> {
            ClipData clip = ClipData.newPlainText("Stompi recovery words", mnemonic);
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(clip);
            Toast.makeText(this, R.string.clipboard_warning, Toast.LENGTH_LONG).show();
        });
        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        AlertDialog backupDialog = new AlertDialog.Builder(this).setTitle(R.string.backup_title).setView(scroll)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {})
                .setPositiveButton(R.string.backup_confirm, (d, w) -> verifyBackup(words, mnemonic, derived, store, name))
                .create();
        backupDialog.show();
        backupDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void verifyBackup(String[] words, String mnemonic, String derived, WalletStore store, String name) {
        int[] positions = new int[3];
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < positions.length; i++) {
            int candidate;
            do { candidate = random.nextInt(words.length); }
            while (contains(positions, i, candidate));
            positions[i] = candidate;
        }
        Arrays.sort(positions);
        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.VERTICAL);
        inputs.setPadding(dp(20), dp(8), dp(20), 0);
        inputs.addView(text(getString(R.string.verify_instructions), 14, TEXT), spaced(10));
        EditText[] answers = new EditText[positions.length];
        for (int i = 0; i < positions.length; i++) {
            answers[i] = new EditText(this);
            answers[i].setHint(getString(R.string.word_position, positions[i] + 1));
            answers[i].setSingleLine(true);
            answers[i].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            inputs.addView(answers[i], spaced(5));
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.verify_title)
                .setView(inputs)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {})
                .setPositiveButton(R.string.verify_confirm, null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            for (int i = 0; i < positions.length; i++) {
                if (!words[positions[i]].equals(answers[i].getText().toString().trim()
                        .toLowerCase(java.util.Locale.ROOT))) {
                    answers[i].setError(getString(R.string.verify_failed));
                    return;
                }
            }
            try {
                store.saveNew(mnemonic, name);
                updateSelectedLabel();
                setReceiveAddress(derived);
                loadFirstAddressAmount();
                showPage(0);
                result.setText(getString(R.string.created_result) + derived);
                dialog.dismiss();
            } catch (Exception e) { result.setText(getString(R.string.wallet_error)); }
        }));
        dialog.show();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private static boolean contains(int[] values, int length, int value) {
        for (int i = 0; i < length; i++) if (values[i] == value) return true;
        return false;
    }

    private void restoreWallet() {
        promptName(name -> {
            EditText input = new EditText(this);
            input.setMinLines(3);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            AlertDialog restoreDialog = new AlertDialog.Builder(this).setTitle(R.string.restore_wallet)
                    .setMessage(R.string.restore_instructions).setView(input)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> {})
                    .setPositiveButton(R.string.restore_confirm, (d, w) -> {
                        String phrase = input.getText().toString().trim().toLowerCase(java.util.Locale.ROOT)
                                .replaceAll("\\s+", " ");
                        worker.execute(() -> {
                            try {
                                String derived = StompiWalletKeys.address(phrase, 0, 0);
                                storeHolder[0].saveNew(phrase, name);
                                runOnUiThread(() -> { setReceiveAddress(derived); updateSelectedLabel(); showPage(0);
                                    loadFirstAddressAmount();
                                    result.setText(getString(R.string.restored_result) + derived); });
                            } catch (Exception e) {
                                runOnUiThread(() -> result.setText(getString(R.string.invalid_mnemonic)));
                            }
                        });
                    }).create();
            restoreDialog.show();
        restoreDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        });
    }

    private static String coins(long sats) {
        return BigDecimal.valueOf(sats, 8).stripTrailingZeros().toPlainString() + " STP";
    }
    private void load() {
        activityDetails.removeAllViews();
        result.setVisibility(View.VISIBLE);
        if (storeHolder[0].active() == null) {
            result.setText(getString(R.string.no_wallet_selected));
            return;
        }
        String value = receiveAddress;
        if (!StompiAddress.isNativeSegwitV0(value)) {
            result.setText(getString(R.string.invalid_address));
            return;
        }
        getPreferences(0).edit().putString("address", value).apply();
        refresh.setEnabled(false);
        result.setText(getString(R.string.loading));
        worker.execute(() -> {
            JSONObject data = null;
            String error = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(NetworkConfig.rewrite(API + value)).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                if (connection.getResponseCode() != 200) throw new Exception(getString(R.string.explorer_error) + connection.getResponseCode());
                try (InputStream stream = connection.getInputStream()) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] chunk = new byte[4096]; int count;
                    while ((count = stream.read(chunk)) != -1) {
                        buffer.write(chunk, 0, count);
                        if (buffer.size() > 262144) throw new Exception(getString(R.string.too_large));
                    }
                    data = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                    if (!value.equals(data.getString("address"))) throw new IllegalStateException("Address mismatch");
                }
            } catch (Exception exc) { error = getString(R.string.request_failed) + exc.getMessage(); }
            finally { if (connection != null) connection.disconnect(); }
            JSONObject shown = data;
            String failure = error;
            runOnUiThread(() -> {
                if (!value.equals(receiveAddress)) return;
                refresh.setEnabled(true);
                if (shown == null) result.setText(failure);
                else {
                    try { showActivityData(shown); }
                    catch (Exception exc) {
                        activityDetails.removeAllViews();
                        result.setVisibility(View.VISIBLE);
                        result.setText(R.string.request_failed);
                    }
                }
            });
        });
    }

    private void showActivityData(JSONObject data) throws Exception {
        activityDetails.removeAllViews();
        result.setVisibility(View.GONE);
        LinearLayout overview = card();
        heading(overview, R.string.activity_overview);
        TextView available = text(coins(data.getLong("unspent_sats")), 25, MINT);
        available.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        overview.addView(available, spaced(6));
        overview.addView(text(getString(R.string.activity_unspent_scope), 12, MUTED), spaced(14));
        overview.addView(text(getString(R.string.activity_received_total, coins(data.getLong("received_sats"))), 14, TEXT), spaced(6));
        overview.addView(text(getString(R.string.activity_sent_total, coins(data.getLong("sent_sats"))), 14, TEXT));
        activityDetails.addView(overview, spaced(14));

        JSONArray history = data.getJSONArray("history");
        LinearLayout list = card();
        heading(list, R.string.recent_transactions);
        if (history.length() == 0) list.addView(text(getString(R.string.activity_empty), 14, MUTED));
        for (int i = 0; i < Math.min(history.length(), 20); i++) {
            JSONObject tx = history.getJSONObject(i);
            long received = tx.getLong("received"), sent = tx.getLong("sent");
            long net = Math.subtractExact(received, sent);
            int label = net > 0 ? R.string.activity_incoming : net < 0
                    ? R.string.activity_outgoing : R.string.activity_self;
            String id = tx.getString("txid");
            int height = tx.getInt("height");
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(box(INPUT, 12));
            TextView title = text(getString(label) + "  " + (net > 0 ? "+" : "") + coins(net), 16, TEXT);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            row.addView(title, spaced(5));
            row.addView(text(getString(R.string.activity_block, height) + " · "
                    + id.substring(0, 8) + "…" + id.substring(id.length() - 6), 12, MUTED));
            row.setOnClickListener(v -> showSecureDialog(new AlertDialog.Builder(this)
                    .setTitle(R.string.activity_transaction)
                    .setMessage(getString(R.string.activity_details, id, height, coins(received), coins(sent)))
                    .setNeutralButton(R.string.activity_copy_txid, (dialog, which) -> {
                        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(
                                ClipData.newPlainText("Stompi transaction ID", id));
                        Toast.makeText(this, R.string.activity_txid_copied, Toast.LENGTH_SHORT).show();
                    })
                    .setPositiveButton(android.R.string.ok, null)));
            list.addView(row, spaced(8));
        }
        activityDetails.addView(list, spaced(14));
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
