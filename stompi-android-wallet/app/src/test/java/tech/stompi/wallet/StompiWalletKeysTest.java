package tech.stompi.wallet;

import static org.junit.Assert.*;
import org.junit.Test;

public class StompiWalletKeysTest {
    private static final String PUBLIC_VECTOR =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";

    @Test public void corePathAndStompiEncoding() {
        assertEquals("stp1qcr8te4kr609gcawutmrza0j4xv80jy8z7trklp",
                StompiWalletKeys.address(PUBLIC_VECTOR, 0, 0));
        assertEquals("stp1qnjg0jd8228aq7egyzacy8cys3knf9xvrk80k74",
                StompiWalletKeys.address(PUBLIC_VECTOR, 0, 1));
        assertEquals("stp1q8c6fshw2dlwun7ekn9qwf37cu2rn755uwuc9zz",
                StompiWalletKeys.address(PUBLIC_VECTOR, 1, 0));
    }

    @Test public void rejectInvalidPhrase() {
        assertThrows(Exception.class, () -> StompiWalletKeys.address("invalid recovery words", 0, 0));
    }
}
