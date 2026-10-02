# Stompi Wallet: Weg in Google Play (Stand 26.09.2026)

## Technischer Stand

- Android-App `tech.stompi.wallet`, Version 0.4-test, `targetSdk 36`, `minSdk 26`. Wallet-Wörter werden lokal verschlüsselt und nicht an den Server gesendet. Screenshot-Schutz ist aktiviert.
- Für den Betrag der **ersten** Empfangsadresse fragt die App jetzt die synchronisierte UTXO-Liste ab; der Server prüft jeden bestätigten Ausgang zusätzlich mit `gettxout` gegen den Mempool und schließt unreife Mining-Ausgänge aus.
- Ein gestarteter Sendeversuch wird lokal per TXID vorgemerkt. Ein weiterer Versuch derselben Wallet bleibt blockiert, bis die TXID bestätigt oder nach eigener Prüfung ausdrücklich freigegeben wurde. Der Serverstatus unterscheidet `mempool`, `confirmed` und `unknown`.
- Die App ist noch keine vollständige HD-Wallet: Weitere Empfangs-/Wechseladressen werden nicht abgeleitet und überwacht; Wechselgeld geht vorläufig zur ersten Adresse. Eingehende unbestätigte Transaktionen werden nicht vollständig angezeigt. Gebühren sind statisch. Vor einer allgemeinen Freigabe sind unabhängige Sicherheitstests und Tests mit Reorg, Mempool-Verlust, konkurrierenden Geräten und vielen UTXOs nötig.

## Build-Gate in Android Studio

1. Projektordner öffnen. Lokales JDK 17, Android SDK 36 und Gradle-Synchronisierung prüfen. Im Projekt `testDebugUnitTest` und `assembleDebug` ausführen; alle Fehler beheben.
2. Für einen **geschlossenen Test** in Android Studio `Build > Generate Signed Bundle / APK > Android App Bundle` wählen. Einen dauerhaften Upload-Schlüssel im Besitz des Betreibers erzeugen und außerhalb des Projekts sichern. Keine Keystore-Datei, Passwörter oder Wallet-Wörter in ZIP, Repository, Chat oder App einfügen.
3. `bundleRelease` bauen und Signatur, `applicationId`, `versionCode` und `targetSdk` des AAB prüfen. Bei jedem Update `versionCode` erhöhen. Der vorhandene Quellordner enthält absichtlich keinen Upload-Schlüssel. Hier war kein Android SDK/Gradle vorhanden; ein AAB und ein Android-Build wurden deshalb noch nicht erzeugt.
4. Interner Test, dann geschlossener Test. Für persönliche Google-Play-Entwicklerkonten, die nach dem 13.11.2023 erstellt wurden, verlangt Google derzeit mindestens 12 durchgehend angemeldete Tester über 14 Tage vor dem Antrag auf Produktionszugang. Kontotyp in der Play Console prüfen.

## Play Console und Betrieb

- Unter App-Inhalte die **Financial Features Declaration** als Krypto-Software-Wallet ausfüllen. Zielregionen und dort erforderliche Nachweise anhand der aktuellen Play-Console-Fragen festlegen; bei Unsicherheit fachlich prüfen lassen. Die Veröffentlichung in Deutschland/EU nicht voraussetzen, bevor die entsprechende regionale Einstufung geklärt ist.
- Datenschutzerklärung auf einer dauerhaft erreichbaren HTTPS-URL und in der App verlinken; Angaben zum Serverbetrieb, Aufbewahrungsfristen und Ansprechpartner vor Veröffentlichung mit der tatsächlichen KeyHelp-/Proxy-Konfiguration abgleichen. Danach Data-Safety-Erklärung entsprechend den tatsächlichen Datenflüssen ausfüllen. Ein Entwurf liegt in `DATENSCHUTZ-ENTWURF-DE.md`.
- Store-Eintrag, Kontaktadresse, Alterszielgruppe, Inhaltsbewertung und Testzugang in der Play Console eintragen. Screenshots für den Store nur mit Test-Wallet und ohne Wiederherstellungswörter erstellen; `FLAG_SECURE` für Testaufnahmen gezielt in einem separaten Build ändern, nicht in der Release-App.
- Server und App müssen gemeinsam ausgerollt werden: die neue `stompi-explorer.py` liefert `spendable_sats` und `/api/transaction/status/<txid>`. Vor dem App-Update Backend ausrollen, Status und UTXO-API prüfen und mit kleinen eigenen Beträgen testen. Die SQLite-Explorer-Datenbank und Wallet-Dateien nicht ersetzen.
- Für eine öffentliche Produktionsfreigabe zusätzlich unabhängige Sicherheitsprüfung, dokumentierte Wiederherstellungstests, Stresstest des öffentlichen RPC-Relays und verbindliche Datenschutzerklärung abschließen.

## Beschreibungsvorschlag

**DE:** „Stompi Wallet erstellt und verwaltet Stompi-Adressen auf deinem Gerät. Empfange STP, prüfe bestätigte Vorgänge und sende Zahlungen nach ausdrücklicher Bestätigung. Wiederherstellungswörter bleiben auf deinem Gerät. Die derzeitige Testversion überwacht die erste Empfangsadresse.“

**EN:** “Stompi Wallet creates and manages Stompi addresses on your device. Receive STP, review confirmed activity, and send payments after explicit confirmation. Recovery words stay on your device. This test version monitors the first receiving address.”
