# Stompi Wallet: acht Sprachen

Neu: Französisch, Spanisch, Italienisch, Portugiesisch, Niederländisch und Polnisch. Deutsch und Englisch bleiben enthalten. Alle 155 App-Texte liegen in allen acht Sprachfassungen vor.

## Auf Windows öffnen – kein Python notwendig

1. Das ZIP vollständig entpacken.
2. In Android Studio **Open** wählen und den Ordner **StompiWallet-AndroidStudio** öffnen.
3. Gradle synchronisieren lassen.
4. Unter **Build → Generate Signed App Bundle / APK** mit dem bisherigen Upload-Schlüssel einen Release-Build erstellen.

Die Änderungen sind bereits eingetragen. Kein Patch und kein Python-Skript auf Windows nötig. R8 und Ressourcenoptimierung bleiben für Release aktiviert. Screenshotsperre, Hochkantmodus, Wallet-Schlüssel und vorhandene Funktionen bleiben erhalten.

Im Paket: `versionCode 5`, `versionName 0.5.0`. Falls in Google Play bereits ein höherer oder gleicher Versionscode hochgeladen wurde, vor dem neuen Upload in `app/build.gradle` einen höheren Code verwenden. Die Application-ID bleibt `tech.stompi.wallet`; mit derselben Signierung aktualisieren, nicht die vorhandene App mit echten Wallets deinstallieren.

## Sprachwahl

**Zahnrad → Sprache wählen**. Die App speichert die Auswahl lokal. „Gerätesprache“ verwendet die Android-Konfiguration; nicht unterstützte Sprachen verwenden die englischen Standardtexte. Ab Android 13 werden die acht Sprachen außerdem in der System-Sprachauswahl der App angeboten. Eine ausdrücklich in der App gewählte Sprache hat Vorrang; für die Android-Auswahl in der App „Gerätesprache“ einstellen.

Benachrichtigungen berücksichtigen die gespeicherte App-Sprache. Nachrichten verwenden vorhandene `title_fr`/`body_fr` usw., ansonsten Englisch und dann Deutsch. Der vorhandene DE/EN-News-Editor wird durch dieses Paket nicht erweitert. Es wird kein externer Übersetzungsdienst eingebunden.

**Wiederherstellungswörter werden niemals übersetzt.** Adresse, Ableitung, Schlüssel, Zahlungen und TXID ändern sich durch die Sprachwahl nicht. Das Zahlenformat für Zahlungen bleibt eindeutig; wie bisher akzeptiert die Betragseingabe Punkt oder Komma als Dezimaltrennzeichen.

## Prüfung vor Veröffentlichung

Lokal geprüft: Vollständigkeit aller Sprachressourcen, gültiges XML, identische Formatparameter in Zahlungs- und Sicherungstexten, Sprachkonfiguration und erhaltene Sicherheits-/Release-Einstellungen. Hier wurde kein Android-Build erstellt, da SDK und Gradle fehlen.

Den **signierten optimierten Release-Build** testen: alle acht Sprachen auswählen, Neustart, dunkles/helles Design, vorhandene Wallet öffnen, auf separatem Testgerät Wiederherstellung prüfen, kleine Testzahlung, TXID/Bestätigung und Benachrichtigungen. Bei größerer Schrift und kleinem Display die Navigation prüfen. Bestehende Wallets nicht für einen Sprachtest löschen. Danach AAB in den internen Play-Test laden. Die erzeugte `mapping.txt` privat sichern.
