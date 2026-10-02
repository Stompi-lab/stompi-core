# Stompi Core – Node, Wallet und Solo-Pool (öffentliche Beta)

Stompi basiert technisch auf Bitcoin Core **v31.1** (Commit `9be056a8a72b624dae9623b2f7bded92c2a21c91`, MIT-Lizenz; siehe `COPYING`). Es verwendet dessen Transaktionsformat, secp256k1-Schlüssel, UTXOs, P2P- und RPC-Protokoll, Blockserialisierung und doppeltes SHA-256. Die anfängliche Blocksubvention beträgt 50 STP, das Halving erfolgt alle 210.000 Blöcke, die maximale theoretische Ausgabe beträgt ungefähr 21 Millionen STP, die Zielzeit 600 Sekunden und die Schwierigkeitsanpassung erfolgt alle 2.016 Blöcke. SegWit gilt ab Höhe 1; Taproot ist vorerst deaktiviert. Dies ist eine öffentliche **Beta**, keine unabhängig geprüfte endgültige Mainnet-Freigabe.

Stompi ist **eine neue Chain**, keine BTC-Wallet und keine Fortsetzung von Stompi v0.1–v0.3. Alte STP-Guthaben und Schlüssel der Python-Version sind nicht übertragbar. Der Genesis-Block wurde mit der Bitcoin-Startschwierigkeit 1 erzeugt:

- Zeit: 24. September 2026, 10:00 UTC (`1790244000`)
- Nachricht: `Stompi 24/Sep/2026: fair launch, no premine`
- `nBits`: `1d00ffff`; Nonce: `1883639016`
- Hash: `000000004ce46ead4546e107fc01668e9f3dafa15452a0766d9a03bc2167917b`
- Genesis-Belohnung: 50 STP, wie bei Bitcoin nicht ausgebbar; keine Entwicklerzuteilung

## Getrennt vom Bitcoin-Netz

| Merkmal | Stompi |
| --- | --- |
| P2P-Port | 28333 |
| RPC-Port | 28332 (nur localhost) |
| Message Magic | `f3 c6 b7 a1` |
| Standard-Datenverzeichnis | `~/.stompi` unter Linux; `Stompi` im lokalen AppData unter Windows |
| Konfigurationsdatei | `stompi.conf` |
| Legacy-P2PKH-Präfix | `01 78 8e 54` (Adressen beginnen immer mit `stpA`) |
| P2SH-Präfix | `01 78 8e 7b` (Adressen beginnen immer mit `stpS`) |
| Private-Key-WIF-Präfix | 191 |
| Bech32-HRP | `stp` |
| DNS-Seeds | Keine; andere Betreiber können ihre eigene Node mit `-addnode` anbinden |
| Feste Start-Peers | `89.163.148.47`, `89.163.242.248`, `91.194.84.84`, `89.163.251.132`, jeweils Port 28333 |

Verwende keine Bitcoin-Wallet- oder Blockchain-Dateien mit diesem Fork. Einige interne Quelldateinamen und BIP-Quellenverweise stammen weiterhin vom Ursprungsprojekt; die ausgelieferten Programme heißen `stompid` und `stompi-cli`, die PID-Datei `stompid.pid`. Standard-Wallet-Adressen beginnen mit `stp1` und funktionieren auch im Bitaxe. Ältere `S...`-Adressen der ersten Stompi-Core-Version bleiben gültig. Alle Nodes müssen dieselben Konsensregeln verwenden; frühere Versionen mit SegWit-Aktivierung erst bei Höhe 481824 sind für einen gemeinsamen Betrieb ungeeignet.

## Windows mit grafischer Wallet starten

Im Windows-Paket `stompi-qt.exe` doppelklicken. Beim ersten Start ein Datenverzeichnis wählen, die Synchronisierung abwarten und im Wallet-Menü eine Wallet anlegen. Unter **Empfangen** lässt sich eine `stp1...`-Adresse erzeugen. Die Oberfläche zeigt Guthaben in STP an und verwendet `stompi:` für Zahlungslinks. Der integrierte Node benötigt für eingehende Peers TCP-Port 28333; Wallet- und Blockchain-Dateien liegen standardmäßig im lokalen AppData-Verzeichnis `Stompi`.

`stompi-qt.exe` und `stompid.exe` verwenden dasselbe Datenverzeichnis. Starte sie für dieselbe Chain nicht gleichzeitig, weil nur ein Prozess die Blockchain-Daten sperren darf. Wenn du die laufende GUI-Node per `stompi-cli.exe getblockchaininfo` oder mit dem Stratum-Server abfragen möchtest, starte die GUI mit `stompi-qt.exe -server=1`. Der optionale Stratum-Server benötigt weiterhin Python 3 und läuft als eigener Prozess.

## Linux starten

Das Paket enthält Linux-x86-64-Programme, gebaut auf Ubuntu 24.04. Installiere dort `libevent-2.1-7t64 libevent-extra-2.1-7t64 libevent-pthreads-2.1-7t64 libsqlite3-0`. **Ubuntu 22.04 benötigt einen lokalen Build**, da die unter 24.04 gebauten Programme neuere glibc-Symbole benötigen. Installiere unter Ubuntu 22.04 `build-essential cmake pkgconf python3 libevent-dev libboost-dev libsqlite3-dev` und baue dann aus dem enthaltenen Quellcode:

```bash
cmake -S . -B build -DBUILD_GUI=OFF -DBUILD_TESTS=OFF -DENABLE_IPC=OFF -DWITH_ZMQ=OFF
cmake --build build --target bitcoind bitcoin-cli -j 2
```

Die lokal gebauten Programme liegen unter `build/bin/bitcoind` und `build/bin/bitcoin-cli`. Kopiere sie bei Bedarf als `stompid` und `stompi-cli`; die Stompi-Chain- und Wallet-Einstellungen sind bereits im Code enthalten.

Nach dem Entpacken im Projektordner (gegebenenfalls `chmod +x linux-x86_64/*`):

```bash
./linux-x86_64/stompid -daemon -server=1 -listen=1
./linux-x86_64/stompi-cli createwallet miner
./linux-x86_64/stompi-cli -rpcwallet=miner getnewaddress
./linux-x86_64/stompi-cli getblockchaininfo
```

Kopiere die ausgegebene Standardadresse (`stp1...`). Alternativ akzeptiert die Brücke `stpA...`-Legacy-Adressen. Der Bitaxe-Solo-Server bezahlt jeden gültigen Block direkt an diese Adresse. Wallet-Dateien und RPC-Cookie bleiben privat.

Die vier festen Start-Peers werden automatisch verwendet, wenn ein neuer Node noch keine bekannten Peers hat. **Jeder Betreiber kann eine eigene Node starten** und den P2P-Port **28333/TCP** für eingehende Verbindungen öffnen; RPC **28332/TCP** bleibt lokal. Nach dem Start prüfst du `./linux-x86_64/stompi-cli getpeerinfo` und `./linux-x86_64/stompi-cli getblockchaininfo`. Sind Start-Peers nicht erreichbar, kann man mit `-addnode=89.163.148.47:28333` einen Peer manuell angeben. Wenn mehrere Nodes auf demselben Rechner laufen, brauchen sie getrennte `-datadir`-, `-port`- und `-rpcport`-Werte.

## Bitaxe über Stratum V1

Der Core-Node selbst spricht kein Stratum. Der enthaltene Python-3-Server `stompi-stratum.py` übersetzt Stratum-V1-Jobs in Core-Blockvorlagen. Starte ihn auf dem Node-Rechner mit dessen LAN-IP:

```bash
python3 stompi-stratum.py --bind 192.168.1.50 --port 3333 --cookie "$HOME/.stompi/.cookie"
```

Ersetze `192.168.1.50` durch die tatsächliche IP. Im Bitaxe: Protokoll **SV1**, Host `192.168.1.50`, Port `3333`, Benutzer die eigene `stp1...`-Adresse, Passwort `x`, TLS aus. Jeder Betreiber kann eine eigene Stratum-Brücke mit seiner Node starten. Die Pool-Webseite mit Miner- und Blockübersicht läuft standardmäßig auf `http://127.0.0.1:8080/`. Für Zugriff aus dem LAN oder Internet kann `--web-bind` gesetzt werden; für eine öffentliche Webseite verwende einen Reverse-Proxy mit HTTPS. Die Webseite enthält keine Wallet-Schlüssel oder RPC-Zugangsdaten, die Miner-IP wird nicht ausgegeben. Die Statistik lebt nur im Speicher und wird beim Neustart zurückgesetzt. Der Pool setzt standardmäßig Share-Schwierigkeit 1000; sie lässt sich mit `--share-difficulty` ändern. Es sind standardmäßig 32 Verbindungen erlaubt; `--max-miners 0` entfernt nur das Programmlimit, nicht die Ressourcenlimits des Betriebssystems. Nicht angemeldete Verbindungen laufen nach 10 Sekunden ab. RPC **28332/TCP** darf nicht öffentlich erreichbar sein.

Die Startschwierigkeit 1 kann bei modernen ASICs anfangs zu sehr schnellen Blöcken führen. Das 10-Minuten-Ziel ist statistisch und wird erst über Retargets alle 2.016 Blöcke angenähert. Die Pool-Share-Schwierigkeit ändert nicht die Konsensschwierigkeit.

## Grenzen und Prüfung

Der Core-Node wurde unter Linux mit eigenem Genesis, RPC, Wallet, `stp1`-Adresse und SegWit-Blockvorlage gestartet. Der Stratum-Server erzeugt eine Coinbase mit Witness Commitment und einen Job. Ein simulierter Miner hat einen serialisierten Block an eine RPC-Testinstanz übergeben. **Eine unabhängige Bestätigung eines echten Bitaxe-Blocks durch mehrere Nodes liegt für diese Beta noch nicht vor.** Die Brücke unterstützt P2PKH- und P2WPKH-Auszahlungen (`stpA` und `stp1`), Stratum V1 mit Version Rolling sowie eine eigene HTTP-Ansicht. Vor einer endgültigen Mainnet-Freigabe sind Konsens- und Sicherheitsprüfung und längere Netzwerkbelastungstests nötig.

Windows-x86-64-Programme wurden per MinGW Cross-Build erzeugt; eine Ausführung auf einem echten Windows-Rechner wurde in dieser Umgebung nicht geprüft. Der vollständige Quellcode und die Upstream-Build-Anleitungen `doc/build-windows.md` sowie `doc/build-windows-msvc.md` sind enthalten. Der Stratum-Server verwendet nur die Python-Standardbibliothek. Siehe [PUBLIC-BETA.md](PUBLIC-BETA.md) für den öffentlichen Testbetrieb.
