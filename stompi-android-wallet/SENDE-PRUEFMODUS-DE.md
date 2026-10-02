# Experimenteller Sende-Prüfmodus

Die App erstellt lokal eine signierte SegWit-v0-P2WPKH-Rohtransaktion aus bestätigten UTXOs der ersten Empfangsadresse. Wallet-Wörter bleiben auf dem Smartphone. Wechselgeld geht vorläufig an dieselbe erste Adresse, weil die App noch keine weitere Adresssuche unterstützt. Die Vorschau zeigt Empfänger, Betrag, Gebühr, Wechselgeld und TXID. Der Rohcode kann separat kopiert werden. Nach einer zweiten Bestätigung kann die App die Transaktion an den Stompi-Dienst senden, sofern der Betreiber den Endpunkt ausdrücklich aktiviert hat.

Der Sendeweg ist noch nicht auf Android und dem öffentlichen Server gemeinsam getestet. Nutze für den ersten Test ausschließlich einen kleinen Betrag an eine eigene Stompi-Adresse. Prüfe Empfänger, Betrag und Gebühr in beiden Bestätigungen.

1. Aktualisiertes Projekt in Android Studio öffnen, `testDebugUnitTest` und `assembleDebug` ausführen. Bei Buildfehlern die ersten relevanten Fehlermeldungen senden.
2. In der App auf der Startseite „Zahlungen und Kontakte“ öffnen. Eine **eigene** gültige stp1-Empfängeradresse und einen kleinen STP-Betrag eingeben. „Transaktion für Core-Prüfung erzeugen“ zeigt die Zusammenfassung. „Rohtransaktion kopieren“ kopiert den Code.
3. Roh-Hex privat auf die eigene Node übertragen. Nicht in Chat, Webseite oder öffentliches Log posten. Dort ausführen: `./linux-x86_64/stompi-cli testmempoolaccept '["ROHHEX"]'`.
4. Der bereits durchgeführte Node-Test hat `allowed: true` geliefert. Der neue Endpunkt prüft erneut mit `testmempoolaccept` und sendet danach über `sendrawtransaction`. Bei einer Zeitüberschreitung vor einem erneuten Versuch die TXID im Explorer oder auf der Node suchen: Eine Übertragung kann trotz ausbleibender Antwort erfolgt sein.

Der Endpunkt ist standardmäßig deaktiviert und muss auf dem Server mit `--enable-broadcast` gestartet werden. Es fehlen weiterhin Mempool-Abgleich vor der UTXO-Auswahl, dynamische Gebühren, weitere Empfangs- und Wechseladressen, robuste Reorg- und Doppelversuchbehandlung und Android-Gerätetests. Diese Version zunächst nur mit kleinem eigenem Testguthaben verwenden.
