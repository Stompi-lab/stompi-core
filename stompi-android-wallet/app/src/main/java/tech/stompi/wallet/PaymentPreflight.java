package tech.stompi.wallet;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.bitcoinj.base.Sha256Hash;
import org.bitcoinj.crypto.DeterministicKey;
import org.json.JSONArray;
import org.json.JSONObject;

/** Experimental BIP143 P2WPKH signer for Core preflight. Not a broadcast implementation. */
public final class PaymentPreflight {
    private PaymentPreflight() {}
    public static final class Result {
        public final String hex, txid;
        public final long feeSats, changeSats;
        private Result(String hex, String txid, long feeSats, long changeSats) {
            this.hex = hex; this.txid = txid; this.feeSats = feeSats; this.changeSats = changeSats;
        }
    }
    private static final class Input {
        byte[] txid; long vout, value;
        Input(byte[] txid, long vout, long value) { this.txid = txid; this.vout = vout; this.value = value; }
    }
    private static byte[] sha(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
    private static byte[] hash256(byte[] bytes) throws Exception { return sha(sha(bytes)); }
    private static byte[] reverse(byte[] bytes) {
        byte[] out = bytes.clone();
        for (int i = 0; i < out.length / 2; i++) {
            byte b = out[i]; out[i] = out[out.length - 1 - i]; out[out.length - 1 - i] = b;
        }
        return out;
    }
    private static byte[] unhex(String hex) {
        if (hex.length() % 2 != 0) throw new IllegalArgumentException("Invalid hex");
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int a = Character.digit(hex.charAt(2*i), 16), b = Character.digit(hex.charAt(2*i+1), 16);
            if (a < 0 || b < 0) throw new IllegalArgumentException("Invalid hex");
            result[i] = (byte) ((a << 4) | b);
        }
        return result;
    }
    private static String hex(byte[] bytes) {
        char[] chars = "0123456789abcdef".toCharArray();
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) { value.append(chars[(b >>> 4) & 15]).append(chars[b & 15]); }
        return value.toString();
    }
    private static void write(ByteArrayOutputStream stream, byte[] bytes) { stream.write(bytes, 0, bytes.length); }
    private static void le(ByteArrayOutputStream stream, long value, int count) {
        for (int i = 0; i < count; i++) stream.write((int) (value >>> (8 * i)) & 255);
    }
    private static void compact(ByteArrayOutputStream stream, int count) {
        if (count < 253) stream.write(count);
        else { stream.write(253); le(stream, count, 2); }
    }
    private static byte[] script(String address) {
        byte[] program = StompiAddress.witnessProgram(address);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0); out.write(program.length); write(out, program);
        return out.toByteArray();
    }
    private static void output(ByteArrayOutputStream stream, long value, String address) {
        byte[] lockingScript = script(address);
        le(stream, value, 8); compact(stream, lockingScript.length); write(stream, lockingScript);
    }
    private static void input(ByteArrayOutputStream stream, Input input) {
        write(stream, reverse(input.txid)); le(stream, input.vout, 4);
        stream.write(0); le(stream, 0xfffffffeL, 4);
    }

    public static Result create(String mnemonic, String source, String target, long amountSats, JSONObject api) throws Exception {
        if (amountSats < 1000 || amountSats > 21_000_000L * 100_000_000L || !StompiAddress.isNativeSegwitV0(target))
            throw new IllegalArgumentException("Invalid payment amount or address");
        if (!source.equals(StompiWalletKeys.address(mnemonic, 0, 0)) || !source.equals(api.getString("address")))
            throw new IllegalArgumentException("Source wallet mismatch");
        if (!api.getBoolean("synced") || api.getInt("index_height") < api.getInt("node_height"))
            throw new IllegalStateException("Explorer is behind the node");
        JSONArray available = api.getJSONArray("utxos");
        List<Input> selected = new ArrayList<>();
        long total = 0, fee = 0;
        for (int i = 0; i < available.length(); i++) {
            JSONObject row = available.getJSONObject(i);
            long value = row.getLong("value_sats");
            long vout = row.getLong("vout");
            int confirmations = row.getInt("confirmations");
            if (value <= 0 || value > 21_000_000L * 100_000_000L || vout < 0 || vout > 0xffffffffL
                    || confirmations < 1 || (row.getBoolean("coinbase") && confirmations < 100))
                throw new IllegalArgumentException("Invalid UTXO");
            byte[] txid = unhex(row.getString("txid"));
            if (txid.length != 32) throw new IllegalArgumentException("Invalid transaction ID");
            selected.add(new Input(txid, vout, value));
            if (selected.size() > 20) throw new IllegalArgumentException("Too many inputs");
            total = Math.addExact(total, value);
            // Conservative 2 sat/vB estimate for native SegWit input and two outputs.
            fee = Math.max(1000, 2L * (10 + 69L * selected.size() + 62));
            if (total >= Math.addExact(amountSats, fee)) break;
        }
        if (total < Math.addExact(amountSats, fee)) throw new IllegalArgumentException("Insufficient confirmed inputs");
        long change = total - amountSats - fee;
        if (change < 546) { fee += change; change = 0; }
        if (fee > Math.max(10000L, amountSats / 100)) throw new IllegalArgumentException("Fee is too high");
        DeterministicKey key = StompiWalletKeys.deriveKey(mnemonic, 0, 0);
        byte[] pub = key.getPubKey(), pubHash = key.getPubKeyHash();
        if (pub.length != 33) throw new IllegalStateException("Uncompressed key");
        ByteArrayOutputStream prevouts = new ByteArrayOutputStream(), sequences = new ByteArrayOutputStream();
        for (Input input : selected) {
            write(prevouts, reverse(input.txid)); le(prevouts, input.vout, 4);
            le(sequences, 0xfffffffeL, 4);
        }
        ByteArrayOutputStream outputs = new ByteArrayOutputStream();
        output(outputs, amountSats, target);
        if (change > 0) output(outputs, change, source); // Reuse first address until change scanning exists.
        ByteArrayOutputStream base = new ByteArrayOutputStream();
        le(base, 2, 4); compact(base, selected.size());
        for (Input input : selected) input(base, input);
        compact(base, change > 0 ? 2 : 1); write(base, outputs.toByteArray()); le(base, 0, 4);
        byte[] txid = reverse(hash256(base.toByteArray()));
        ByteArrayOutputStream signed = new ByteArrayOutputStream();
        le(signed, 2, 4); signed.write(0); signed.write(1); compact(signed, selected.size());
        for (Input input : selected) input(signed, input);
        compact(signed, change > 0 ? 2 : 1); write(signed, outputs.toByteArray());
        byte[] prevHash = hash256(prevouts.toByteArray()), sequenceHash = hash256(sequences.toByteArray());
        byte[] outputHash = hash256(outputs.toByteArray());
        for (Input input : selected) {
            ByteArrayOutputStream preimage = new ByteArrayOutputStream();
            le(preimage, 2, 4); write(preimage, prevHash); write(preimage, sequenceHash);
            write(preimage, reverse(input.txid)); le(preimage, input.vout, 4);
            preimage.write(25); preimage.write(0x76); preimage.write(0xa9); preimage.write(20);
            write(preimage, pubHash); preimage.write(0x88); preimage.write(0xac);
            le(preimage, input.value, 8); le(preimage, 0xfffffffeL, 4);
            write(preimage, outputHash); le(preimage, 0, 4); le(preimage, 1, 4);
            byte[] der = key.sign(Sha256Hash.wrap(hash256(preimage.toByteArray()))).encodeToDER();
            compact(signed, 2); compact(signed, der.length + 1); write(signed, der); signed.write(1);
            compact(signed, pub.length); write(signed, pub);
        }
        le(signed, 0, 4);
        return new Result(hex(signed.toByteArray()), hex(txid), fee, change);
    }
}
