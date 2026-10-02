package tech.stompi.wallet;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class PaymentPreflightTest {
    private static final String WORDS =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
    private static final String ADDRESS = "stp1qcr8te4kr609gcawutmrza0j4xv80jy8z7trklp";

    @Test public void signedSegwitPreviewHasStableTxidAndFee() throws Exception {
        JSONObject api = new JSONObject().put("address", ADDRESS).put("index_height", 200)
                .put("node_height", 200).put("synced", true)
                .put("utxos", new JSONArray().put(new JSONObject()
                        .put("txid", "11".repeat(32)).put("vout", 1)
                        .put("value_sats", 200000).put("confirmations", 12).put("coinbase", false)));
        PaymentPreflight.Result payment = PaymentPreflight.create(WORDS, ADDRESS, ADDRESS, 100000, api);
        assertTrue(payment.hex.startsWith("02000000000101"));
        assertEquals(64, payment.txid.length());
        assertEquals(200000L - 100000L - payment.feeSats, payment.changeSats);
        assertTrue(payment.hex.endsWith("00000000"));
    }
    @Test public void staleIndexCannotProducePayment() throws Exception {
        JSONObject api = new JSONObject().put("address", ADDRESS).put("index_height", 199)
                .put("node_height", 200).put("synced", false).put("utxos", new JSONArray());
        assertThrows(IllegalStateException.class,
                () -> PaymentPreflight.create(WORDS, ADDRESS, ADDRESS, 100000, api));
    }
}
