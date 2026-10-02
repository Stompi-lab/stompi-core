#!/usr/bin/env python3
"""Public BIP84 vectors adapted to Stompi's Bech32 HRP; no private keys."""
import hashlib

CHARS = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
GEN = (0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

PUBLIC_KEYS = (
    ("receive 0", "0330d54fd0dd420a6e5f8d3624f5f3482cae350f79d5f0753bf5beef9c2d91af3c", "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"),
    ("receive 1", "03e775fd51f0dfb8cd865d9ff1cca2a158cf651fe997fdc9fee9c1d3b5e995ea77", "bc1qnjg0jd8228aq7egyzacy8cys3knf9xvrerkf9g"),
    ("change 0", "03025324888e429ab8e3dbaf1f7802648b9cd01e9b418485c5fa4c1b9b5700e1a6", "bc1q8c6fshw2dlwun7ekn9qwf37cu2rn755upcp6el"),
)


def encode(hrp, program):
    values = [0]
    acc, bits = 0, 0
    for b in program:
        acc = (acc << 8) | b
        bits += 8
        while bits >= 5:
            bits -= 5
            values.append((acc >> bits) & 31)
    if bits:
        values.append((acc << (5 - bits)) & 31)
    expansion = [ord(c) >> 5 for c in hrp] + [0] + [ord(c) & 31 for c in hrp]
    chk = 1
    for value in expansion + values + [0] * 6:
        top = chk >> 25
        chk = ((chk & 0x1ffffff) << 5) ^ value
        for i, g in enumerate(GEN):
            if (top >> i) & 1:
                chk ^= g
    chk ^= 1
    checksum = [(chk >> (5 * (5 - i))) & 31 for i in range(6)]
    return hrp + "1" + "".join(CHARS[i] for i in values + checksum)


if __name__ == "__main__":
    for label, pubkey, expected_btc in PUBLIC_KEYS:
        h160 = hashlib.new("ripemd160", hashlib.sha256(bytes.fromhex(pubkey)).digest()).digest()
        assert encode("bc", h160) == expected_btc, label
        print(f"{label}: {encode('stp', h160)}  scriptPubKey=0014{h160.hex()}")
