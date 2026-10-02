# Stompi Core

Source code for the independent Stompi proof-of-work network: daemon `stompid`, command-line client `stompi-cli` and desktop GUI `stompi-qt`.

## Source snapshot / Quellstand

This repository package was prepared from the uploaded **Stompi-v0.8.1-Quellcode.zip**. It has **not** been established as identical to the later production binaries or the build-server source that passed 153 registered tests. Reconcile the current build-server source before tagging a production release. No build or full test run was performed while preparing this package.

Dieses Paket basiert auf dem hochgeladenen v0.8.1-Archiv. Der Abgleich mit dem später getesteten Buildserver-Stand steht aus. Bitte noch keinen aktuellen Release daraus kennzeichnen.

## Network

- Currency: STP; native SegWit address prefix: `stp1`
- P2P: TCP 28333; RPC: TCP 28332, keep local
- Genesis: `000000004ce46ead4546e107fc01668e9f3dafa15452a0766d9a03bc2167917b`
- Separate blockchain; not a Bitcoin wallet.

See `README-STOMPI.md` for the archive's network details; its Pool and beta instructions are historical material. Pool, homepage and server administration are not included in this repository.

## Build

```sh
cmake -S . -B build -DBUILD_GUI=OFF -DBUILD_TESTS=ON
cmake --build build -j2
ctest --test-dir build --output-on-failure
```

Install platform dependencies first; see `doc/build-unix.md` and `doc/build-windows.md`. For GUI builds use `-DBUILD_GUI=ON` and install the documented Qt dependencies. The `depends` directory supports cross-compilation. Internal upstream target names may still refer to Bitcoin.

## License and origin

Bitcoin Core derived code; existing MIT license and copyright notices are preserved in `COPYING`. See `STOMPI-UPSTREAM.txt`. Upstream CI is retained as a reference outside the workflows directory; it has not been adapted or validated for Stompi.

Contributions: `CONTRIBUTING-STOMPI.md`. Security contact: `SECURITY.md`.
