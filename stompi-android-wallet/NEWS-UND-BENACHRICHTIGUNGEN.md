# News und Benachrichtigungen · Entwicklungsstand

Die Android-App besitzt nun einen vierten Reiter „News“. Sie lädt `https://stompi.tech/api/news`. Bei einem leeren Feed wird „Noch keine Neuigkeiten“ angezeigt. Neue, bisher unbekannte News-IDs erzeugen nach Aktivierung eine lokale Benachrichtigung. Der erste Abruf setzt nur den Vergleichsstand und löst keine Meldungen für ältere Beiträge aus.

Der öffentliche News-Endpunkt des Homepage-Projekts liest `news-data/news.json` (maximal 64 KiB). Beiträge werden über die geschützte Redaktion `https://stompi.tech/news-admin/` angelegt; der lokale Dienst bindet an `127.0.0.1:8090`. Jeder Beitrag enthält eine dauerhaft eindeutige ID sowie Titel und Text in DE und EN. Die öffentliche API ist nur lesbar. Details stehen in `NEWS-REDAKTION-DE.md` des Homepage-Pakets.

Optionale Hintergrundmeldungen verwenden WorkManager mit einem Intervall von 30 Minuten und Internetverbindung. Android kann die Ausführung verzögern. Ab Android 13 ist zusätzlich die Benachrichtigungsberechtigung nötig. Auf der News-Seite gibt es Ein- und Ausschalten. Neuigkeiten und Zahlungseingänge haben getrennte Android-Kanäle.

**Wichtige Grenze:** Eingangsmeldungen prüfen derzeit ausschließlich den bestätigten Gesamtzugang an Adresse `m/84'/0'/0'/0/0` jeder lokal gespeicherten Wallet über die öffentliche Explorer-API. Der erste erfolgreiche Abruf setzt nur den Vergleichsstand. Andere Empfangsadressen, Wechselgeld, unbestätigte Transaktionen und Reorganisationen werden nicht zuverlässig als Wallet-Ereignis erkannt. Eine Meldung ist keine Aussage über den verfügbaren Saldo und ersetzt weder vollständige Adresssuche noch UTXO-Abgleich. Beim Abruf werden diese Adressen an den öffentlichen Server übertragen. Für eine walletweite Benachrichtigung muss zuerst die vollständige Wallet-Synchronisierung entwickelt werden.

Android-Build, Gerätetest, Permission-Flow und Benachrichtigungen über längere Laufzeit konnten in dieser Umgebung nicht ausgeführt werden. Vor produktiver Nutzung oder Werbung für „Wallet-Eingangsbenachrichtigungen“ ist eine unabhängige Prüfung nötig.
