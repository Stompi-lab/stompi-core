package tech.stompi.wallet;

/** Checks Stompi native SegWit v0 addresses without involving the explorer. */
public final class StompiAddress {
    private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int[] GENERATOR = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};
    private StompiAddress() {}

    public static boolean isNativeSegwitV0(String address) {
        if (address == null || address.length() < 14 || address.length() > 90 || !address.startsWith("stp1")) return false;
        int[] values = new int[address.length() - 4];
        for (int i = 0; i < values.length; i++) {
            char c = address.charAt(i + 4);
            if (c > 127) return false;
            int n = CHARSET.indexOf(c);
            if (n < 0) return false;
            values[i] = n;
        }
        int chk = 1;
        for (int i = 0; i < 3; i++) chk = step(chk, 's' >> 5);
        chk = step(chk, 0);
        for (char c : new char[]{'s','t','p'}) chk = step(chk, c & 31);
        for (int value : values) chk = step(chk, value);
        if (chk != 1 || values.length < 7 || values[0] != 0) return false;
        // Witness v0 needs exactly 20 or 32 decoded program bytes.
        int bits = 0, acc = 0, bytes = 0;
        for (int i = 1; i < values.length - 6; i++) {
            acc = ((acc << 5) | values[i]) & 4095;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                bytes++;
            }
        }
        return (bytes == 20 || bytes == 32) && bits < 5 && (acc & ((1 << bits) - 1)) == 0;
    }


    public static String encodeP2wpkh(byte[] hash160) {
        if (hash160 == null || hash160.length != 20) throw new IllegalArgumentException("P2WPKH requires 20 bytes");
        StringBuilder encoded = new StringBuilder("stp1q");
        int acc = 0, bits = 0;
        for (byte b : hash160) {
            acc = ((acc << 8) | (b & 255)) & 4095;
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                encoded.append(CHARSET.charAt((acc >>> bits) & 31));
            }
        }
        if (bits != 0) encoded.append(CHARSET.charAt((acc << (5 - bits)) & 31));
        int check = 1;
        for (int i = 0; i < 3; i++) check = step(check, 's' >> 5);
        check = step(check, 0);
        for (char c : new char[]{'s', 't', 'p'}) check = step(check, c & 31);
        for (int i = 4; i < encoded.length(); i++) check = step(check, CHARSET.indexOf(encoded.charAt(i)));
        for (int i = 0; i < 6; i++) check = step(check, 0);
        check ^= 1;
        for (int i = 0; i < 6; i++) encoded.append(CHARSET.charAt((check >>> (5 * (5 - i))) & 31));
        String result = encoded.toString();
        if (!isNativeSegwitV0(result)) throw new IllegalStateException("Address checksum mismatch");
        return result;
    }

    /** Decode witness v0 output program after verifying the Stompi Bech32 checksum. */
    public static byte[] witnessProgram(String address) {
        if (!isNativeSegwitV0(address)) throw new IllegalArgumentException("Invalid Stompi address");
        int chars = address.length() - 4 - 1 - 6;
        byte[] result = new byte[chars * 5 / 8];
        int acc = 0, bits = 0, position = 0;
        for (int i = 5; i < address.length() - 6; i++) {
            acc = (acc << 5) | CHARSET.indexOf(address.charAt(i));
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                result[position++] = (byte) ((acc >>> bits) & 255);
            }
        }
        return result;
    }

    private static int step(int chk, int value) {
        int top = chk >>> 25;
        chk = ((chk & 0x1ffffff) << 5) ^ value;
        for (int i = 0; i < 5; i++) if (((top >>> i) & 1) != 0) chk ^= GENERATOR[i];
        return chk;
    }
}
