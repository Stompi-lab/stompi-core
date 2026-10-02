# Stompi Android-Wallet: Wiederherstellung und Kompatibilität

Stand: 26. September 2026. Dies ist eine technische Grundlage, **noch keine freigegebene Wallet**.

Der aktuelle Stompi-Core-Code baut für native SegWit-Adressen Deskriptoren mit `m/84'/0'/0'/0/*` (Empfangen) und `m/84'/0'/0'/1/*` (Wechselgeld). Das Adresspräfix lautet `stp`, das Script für P2WPKH `0014` gefolgt von einem 20-Byte-Public-Key-Hash. Siehe `src/wallet/walletutil.cpp`, `src/kernel/chainparams.cpp` und `src/key_io.cpp` im veröffentlichten Core-Quellcode.

Der Android-Plan verwendet eine neue, ausschließlich für Stompi erstellte BIP39-Wiederherstellungsphrase, BIP32-Schlüsselableitung, BIP84-P2WPKH und das Stompi-HRP. Stompi Core erzeugt selbst keine BIP39-Phrase aus seiner Wallet-Datei. Eine Core-Wallet lässt sich folglich nicht einfach durch Eingabe einer vermeintlichen Stompi-Core-Seed-Phrase auf Android wiederherstellen. Für den Import einer Android-Wallet in Core sind zugehörige private Deskriptoren samt Stompi-kompatiblen Extended-Key-Präfixen und ein geprüfter Rescan-Workflow erforderlich. Das muss vor der Freigabe praktisch getestet werden.

`tools/address_vectors.py` prüft zunächst die drei **öffentlichen** BIP84-Public-Key-Vektoren gegen ihre bekannten `bc1`-Adressen und bildet anschließend aus genau denselben Public Keys `stp1`-Adressen. Diese Daten enthalten keine privaten Schlüssel. Es handelt sich um Testvektoren, nicht um eine Empfehlung, Bitcoin-Seeds für Stompi zu verwenden. Gleicher Pfad und gleiche Phrase würden dieselben Schlüssel auf zwei Chains nutzen; echte Stompi-Phrasen müssen eigens erzeugt werden und dürfen nicht wiederverwendet werden.

Vor einer Schlüssel- oder Senden-Funktion fehlen: unabhängige BIP39/BIP32/BIP84-Implementierung mit Testvektoren, verschlüsseltes lokales Backup und Restore, Abgleich einer importierten Test-Wallet mit Stompi Core, vollständige UTXO- und Coinbase-Reife-Prüfung, lokale P2WPKH-Signatur, Gebührenberechnung, sichere Broadcast-Schnittstelle und Tests auf echten Android-Geräten. Die öffentliche Explorer-API liefert derzeit keine geeignete vollständige Wallet-UTXO-Quelle.
