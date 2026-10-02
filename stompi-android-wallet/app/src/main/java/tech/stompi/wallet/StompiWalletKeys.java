package tech.stompi.wallet;

import java.security.SecureRandom;
import java.util.Arrays;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.wallet.DeterministicSeed;

/** BIP39 -> m/84'/0'/0'/branch/index -> Stompi P2WPKH. Never log a seed. */
public final class StompiWalletKeys {
    private StompiWalletKeys() {}

    public static String newMnemonic() {
        return DeterministicSeed.ofRandom(new SecureRandom(), 256, "").getMnemonicString();
    }

    public static String address(String mnemonic, int branch, int index) {
        return StompiAddress.encodeP2wpkh(deriveKey(mnemonic, branch, index).getPubKeyHash());
    }

    static DeterministicKey deriveKey(String mnemonic, int branch, int index) {
        if (branch < 0 || branch > 1 || index < 0)
            throw new IllegalArgumentException("Invalid derivation path");
        DeterministicSeed seed = DeterministicSeed.ofMnemonic(mnemonic.trim(), "");
        try {
            MnemonicCode.INSTANCE.check(seed.getMnemonicCode());
        } catch (org.bitcoinj.crypto.MnemonicException ex) {
            throw new IllegalArgumentException("Invalid recovery phrase", ex);
        }
        byte[] seedBytes = seed.getSeedBytes();
        try {
            DeterministicKey key = HDKeyDerivation.createMasterPrivateKey(seedBytes);
            for (ChildNumber child : new ChildNumber[]{new ChildNumber(84, true),
                    new ChildNumber(0, true), new ChildNumber(0, true),
                    new ChildNumber(branch, false), new ChildNumber(index, false)}) {
                key = HDKeyDerivation.deriveChildKey(key, child);
            }
            return key;
        } finally {
            Arrays.fill(seedBytes, (byte) 0);
        }
    }
}
