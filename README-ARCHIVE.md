# Stompi Core

Stompi ist eine eigenständige Proof-of-Work-Blockchain mit einer Node, einer Wallet und einem Solo-Stratum-Server für Bitaxe und andere Stratum-V1-Miner. Die Node prüft Blöcke und Transaktionen; der optionale Pool weist Mining-Arbeit zu und zahlt gefundene Blöcke direkt an die vom Miner angegebene Stompi-Adresse aus.

**Entwicklungsstand:** Diese Software ist noch keine geprüfte Mainnet-Freigabe. Nutze sie zum Testen und prüfe vor einem öffentlichen Start die Konsensregeln, die Windows-Dateien und einen echten End-to-End-Block mit mehreren unabhängigen Nodes.

## Schnellstart unter Linux

```bash
./linux-x86_64/stompid -daemon -server=1 -listen=1
./linux-x86_64/stompi-cli createwallet miner
./linux-x86_64/stompi-cli -rpcwallet=miner getnewaddress
```

## Grafische Wallet unter Windows

Im Windows-Paket `stompi-qt.exe` starten und eine Wallet anlegen. Für RPC-Zugriff durch `stompi-cli.exe` oder den Solo-Stratum-Server die GUI mit `stompi-qt.exe -server=1` starten. `stompid.exe` nicht gleichzeitig mit der GUI im selben Datenverzeichnis ausführen.

In [README-STOMPI.md](README-STOMPI.md) stehen die Parameter, Server-Einrichtung und Build-Befehle. Der Stratum-Server und die Pool-Webseite sind in `stompi-stratum.py` und `pool-web/` enthalten.

## Eigenen Node oder Pool betreiben

Jeder kann einen Stompi-Node starten und über Port 28333/TCP mit anderen Nodes verbinden. Für einen eigenen Solo-Pool wird zusätzlich `stompi-stratum.py` betrieben. RPC bleibt auf localhost; die eigene Wallet und ihre privaten Schlüssel werden nicht zwischen Nodes verteilt.

## Herkunft und Lizenz

Stompi Core basiert auf Bitcoin Core v31.1. Der ursprüngliche README-Text liegt in [README-UPSTREAM.md](README-UPSTREAM.md); Urheberhinweise und die MIT-Lizenz bleiben erhalten. Stompi ist nicht mit dem Bitcoin-Netz verbunden. Siehe [COPYING](COPYING) und [STOMPI-UPSTREAM.txt](STOMPI-UPSTREAM.txt).
