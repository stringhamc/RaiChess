# Stockfish.js (bundled engine)

RaiChess uses **Stockfish.js** — a WebAssembly/JavaScript build of the
Stockfish chess engine — as its strong opponent, run in a headless WebView.

- Engine: Stockfish (synced with Stockfish 10 in this build)
- WASM/JS port: Stockfish.js by Nathan Rugg — https://github.com/nmrugg/stockfish.js
- Upstream engine: https://github.com/official-stockfish/Stockfish
- License: GNU General Public License v3.0 (see `LICENSE.txt` in this folder)

RaiChess is itself distributed under the GPL-3.0, which is compatible.
The bundled files `stockfish.js` and `stockfish.wasm` are unmodified from the
`stockfish@10.0.2` npm release.
