"""Independently reproduce Stompi's Genesis merkle root and block hash."""
import hashlib
import struct

stamp = b'Stompi 24/Sep/2026: fair launch, no premine'
pubkey = bytes.fromhex('04678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5f')
script = bytes.fromhex('04ffff001d0104') + bytes([len(stamp)]) + stamp
output = bytes([len(pubkey)]) + pubkey + b'\xac'
transaction = (struct.pack('<I', 1) + b'\x01' + bytes(32) + b'\xff' * 4 +
               bytes([len(script)]) + script + b'\xff' * 4 + b'\x01' +
               struct.pack('<Q', 50 * 100_000_000) + bytes([len(output)]) + output + bytes(4))
def sha256d(data):
    return hashlib.sha256(hashlib.sha256(data).digest()).digest()
merkle = sha256d(transaction)
header = struct.pack('<I', 1) + bytes(32) + merkle + struct.pack('<III', 1790244000, 0x1d00ffff, 1883639016)
assert merkle[::-1].hex() == '012077b96060ce2381ae403b5b085e1daedd856bca656d15dab3e0e609a81799'
assert sha256d(header)[::-1].hex() == '000000004ce46ead4546e107fc01668e9f3dafa15452a0766d9a03bc2167917b'
print('Stompi Genesis verified:', sha256d(header)[::-1].hex())
