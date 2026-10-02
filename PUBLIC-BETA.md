# Stompi Core – öffentliche Beta

Diese Beta ist ein installierbares Paket für eigene Stompi-Nodes und Solo-Pools. Sie verwendet denselben Genesis-Block und dieselben Konsensregeln wie Stompi Core v0.7.3. Die Wallet- und Kettendaten bleiben lokal. Es handelt sich nicht um ein unabhängig geprüftes, endgültiges Mainnet; bestehende und neue STP-Guthaben auf dieser Kette dürfen nicht als garantierter Wert betrachtet werden.

## Eigene Node betreiben

Jeder kann eine Node starten. Die vier eingebauten Start-Peers sind nur Einstiegspunkte; nach der Synchronisierung verbreiten Nodes weitere Peers über das P2P-Protokoll.

Unter Linux im entpackten Ordner:

```bash
./stompid -daemon -server=1 -listen=1
./stompi-cli getblockchaininfo
./stompi-cli getpeerinfo
./stompi-cli createwallet miner
./stompi-cli -rpcwallet=miner getnewaddress
```

Unter Windows in PowerShell:

Für die grafische Wallet `stompi-qt.exe` per Doppelklick starten, eine Wallet anlegen und nach der Synchronisierung unter **Empfangen** eine Adresse erzeugen. Die GUI betreibt selbst eine Node. Die folgenden Befehle sind für den alternativen Betrieb ohne Oberfläche; `stompid.exe` und `stompi-qt.exe` nicht zugleich mit demselben Datenverzeichnis starten.

```powershell
.\stompid.exe -server=1 -listen=1
.\stompi-cli.exe getblockchaininfo
.\stompi-cli.exe createwallet miner
.\stompi-cli.exe -rpcwallet=miner getnewaddress
```

Der Node braucht eingehend TCP 28333. RPC 28332 bleibt auf localhost. Wenn die Start-Peers ausfallen, kann man beim Start `-addnode=89.163.148.47:28333` ergänzen oder einen anderen Stompi-Peer angeben. Alle Nodes müssen dieselben Konsensregeln verwenden. Bestehende Wallets vor einem Upgrade sichern.

## Eigenen Solo-Pool betreiben

Der Pool ist eine eigenständige Python-3-Datei und kann neben jeder Stompi-Node laufen. Unter Linux:

```bash
python3 stompi-stratum.py --bind 0.0.0.0 --port 3333 \
  --cookie "$HOME/.stompi/.cookie" --web-bind 127.0.0.1 --web-port 8080
```

Unter Windows mit installiertem Python 3 (PowerShell):

```powershell
python .\stompi-stratum.py --bind 0.0.0.0 --port 3333 `
  --cookie "$env:LOCALAPPDATA\Stompi\.cookie" --web-bind 127.0.0.1 --web-port 8080
```

Eine beliebige gültige `stp1...`-Adresse kann als Miner-Benutzername verwendet werden. Der Pool zahlt den kompletten Block an die im jeweiligen Job angegebene Adresse aus; er verwahrt keine Nutzer-Wallets. Der Betreiber des Stratum-Servers erzeugt jedoch die Blockvorlage: Miner sollten dem Betreiber vertrauen und ihre Auszahlungsadresse im Job überprüfen. Passwort `x`, Stratum V1 ohne TLS. Port 3333/TCP nur gezielt öffnen und Verbindungszahlen überwachen. `--max-miners 0` entfernt das Programmlimit, aber keine Betriebssystemlimits.

Die Pool-Webseite ist auf dem Pool-Rechner unter `http://127.0.0.1:8080/` erreichbar. Für öffentliche Besucher einen HTTPS-Reverse-Proxy oder eine andere gesicherte Webbereitstellung verwenden; RPC-Cookie und Wallet-Dateien dürfen niemals über HTTP ausgeliefert werden. Die Webseite zeigt nur flüchtige Pool-Statistik; der Pool muss für eine öffentliche Betreiberinstanz zusätzlich überwacht werden.

## Status und Grenzen

- Linux x86-64 und Windows x86-64 werden separat gebaut. Die Windows-GUI wurde per MinGW mit Qt gebaut; eine Ausführung auf einem echten Windows-Rechner erfolgte in dieser Umgebung nicht.
- Die Stompi-Webseite ist eine lokale Pool-Ansicht, keine bereits veröffentlichte Domain. Ein öffentlicher Hostname und HTTPS müssen vom Betreiber bereitgestellt werden.
- Ein unabhängiges Konsens- und Sicherheitsreview sowie ein öffentlich nachprüfbarer End-to-End-Test mit Bitaxe, zwei Nodes, Chain-Reorg und längerer Last stehen aus.
- Startschwierigkeit 1 und ASICs können anfangs Blöcke schneller als alle zehn Minuten erzeugen. Die Anpassung erfolgt nach jeweils 2016 Blöcken.
- Stompi Core basiert auf Bitcoin Core v31.1 (MIT). Urheberhinweise, Quellenreferenzen und Lizenzdateien bleiben aus rechtlichen und technischen Gründen erhalten. Die Software verbindet sich nicht mit dem Bitcoin-Netz.
