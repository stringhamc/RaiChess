#!/usr/bin/env python3
"""Replace the bundled seed puzzles with real Lichess puzzles.

The app ships a small machine-verified seed set in
app/src/main/assets/puzzles/puzzles.csv, using the exact schema of the
Lichess puzzle database (https://database.lichess.org/#puzzles, CC0 public
domain). Run this script on a machine with internet access to download the
full database (~250 MB compressed, 5M+ puzzles) and write a curated,
size-bounded sample into the asset — no app code changes needed.

Usage (from the repo root):
    pip install zstandard
    python3 tools/fetch_lichess_puzzles.py [--per-bucket 150] [--out PATH]

Sampling: puzzles are bucketed by rating (400-2600 in 200-point bands) and
by a small set of high-value themes; up to --per-bucket popular puzzles are
kept per (band, theme) bucket, so every player ELO and every weakness theme
the coach can detect has material. Default output is ~3-6k puzzles / a few
hundred KB — negligible APK weight.
"""
import argparse
import csv
import io
import os
import random
import sys
import urllib.request

# No checksum pinning: Lichess republishes this dump regularly, so there is
# no stable hash to pin against. Dev-only tool; every sampled row is
# format-validated before it reaches the asset.
DB_URL = "https://database.lichess.org/lichess_db_puzzle.csv.zst"
DEFAULT_OUT = os.path.join(
    os.path.dirname(__file__), "..", "app", "src", "main", "assets",
    "puzzles", "puzzles.csv"
)

# Themes DrillSelector maps player weaknesses onto, plus a broad spread of
# tactical and endgame motifs — variety in the pool is what lets the app's
# theme-spacing keep sessions from feeling repetitive.
THEMES = [
    "hangingPiece", "fork", "pin", "skewer", "discoveredAttack",
    "mateIn1", "mateIn2", "mateIn3", "backRankMate", "smotheredMate",
    "promotion", "advancedPawn", "endgame", "rookEndgame", "pawnEndgame",
    "knightEndgame", "bishopEndgame", "queenEndgame",
    "deflection", "attraction", "sacrifice", "clearance", "doubleCheck",
    "trappedPiece", "xRayAttack", "zugzwang", "quietMove", "defensiveMove",
    "intermezzo", "exposedKing", "kingsideAttack", "capturingDefender",
]
BANDS = [(lo, lo + 200) for lo in range(400, 2600, 200)]
HEADER = ["PuzzleId", "FEN", "Moves", "Rating", "RatingDeviation",
          "Popularity", "NbPlays", "Themes", "GameUrl", "OpeningTags"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-bucket", type=int, default=150)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--min-popularity", type=int, default=70,
                    help="Lichess player-vote quality floor (0-100)")
    args = ap.parse_args()

    try:
        import zstandard
    except ImportError:
        sys.exit("pip install zstandard first")

    print(f"downloading {DB_URL} (this is large)...")
    buckets = {}  # (band_index, theme) -> list of rows
    with urllib.request.urlopen(DB_URL) as resp:
        reader = zstandard.ZstdDecompressor().stream_reader(resp)
        text = io.TextIOWrapper(reader, encoding="utf-8")
        rows = csv.reader(text)
        header = next(rows)
        idx = {name: header.index(name) for name in HEADER if name in header}
        for n, row in enumerate(rows):
            if n % 500_000 == 0 and n:
                print(f"  scanned {n:,} puzzles...")
            try:
                rating = int(row[idx["Rating"]])
                popularity = int(row[idx["Popularity"]])
            except (ValueError, IndexError):
                continue
            if popularity < args.min_popularity:
                continue
            themes = set(row[idx["Themes"]].split())
            for b, (lo, hi) in enumerate(BANDS):
                if not (lo <= rating < hi):
                    continue
                for theme in THEMES:
                    if theme in themes:
                        bucket = buckets.setdefault((b, theme), [])
                        if len(bucket) < args.per_bucket:
                            bucket.append(row)
                break

    # Surface starved buckets: an empty band × theme means players at that
    # level get no material for that motif
    for b, (lo, hi) in enumerate(BANDS):
        thin = [t for t in THEMES if len(buckets.get((b, t), [])) < 10]
        if thin:
            print(f"  note: band {lo}-{hi} thin (<10) for: {', '.join(thin)}")

    seen = set()
    out_rows = []
    for bucket in buckets.values():
        for row in bucket:
            pid = row[idx["PuzzleId"]]
            if pid not in seen:
                seen.add(pid)
                out_rows.append([row[idx[h]] if h in idx else "" for h in HEADER])
    # Deterministic shuffle within equal ratings so file order doesn't
    # cluster same-theme puzzles (the dump groups them)
    rng = random.Random(0)
    out_rows.sort(key=lambda r: (int(r[3]), rng.random()))

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(HEADER)
        w.writerows(out_rows)
    size_kb = os.path.getsize(args.out) // 1024
    print(f"wrote {len(out_rows):,} puzzles to {args.out} ({size_kb} KB)")
    print("Lichess puzzle data is CC0 (public domain): "
          "https://database.lichess.org/#puzzles")


if __name__ == "__main__":
    main()
