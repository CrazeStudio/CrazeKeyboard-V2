#!/usr/bin/env python3
"""Build Craze Word Prediction (CWP1) from a plain-text sentence corpus.

Input: UTF-8 text, one sentence/document per line.
Output: a compact memory-mappable model for Android.

The binary index is sorted by unsigned FNV-1a hash. Each record stores the
actual context string as well, so hash collisions are checked at lookup time.
"""
from __future__ import annotations
import argparse
import collections
import pathlib
import struct
import re

TOKEN_RE = re.compile(r"[a-z']+")
MAGIC = b"CWP1"
VERSION = 1
MAX_CANDIDATES = 12
MIN_COUNT = 2

def fnv1a(text: str) -> int:
    h = 0x811C9DC5
    for b in text.encode("utf-8"):
        h ^= b
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("corpus", type=pathlib.Path)
    parser.add_argument("output", type=pathlib.Path)
    parser.add_argument("--min-count", type=int, default=MIN_COUNT)
    parser.add_argument("--max-candidates", type=int, default=MAX_CANDIDATES)
    args = parser.parse_args()

    counts: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
    with args.corpus.open("r", encoding="utf-8", errors="ignore") as source:
        for line in source:
            words = [w.lower() for w in TOKEN_RE.findall(line)]
            for i, target in enumerate(words):
                for n in (1, 2, 3):
                    if i >= n:
                        context = " ".join(words[i-n:i])
                        counts[context][target] += 1

    records = []
    for context, candidates in counts.items():
        ranked = [(word, count) for word, count in candidates.most_common(args.max_candidates)
                  if count >= args.min_count and len(word.encode("utf-8")) <= 255]
        if not ranked:
            continue
        key = context.encode("utf-8")
        if len(key) > 65535:
            continue
        body = bytearray(struct.pack("<H", len(key)))
        body.extend(key)
        body.append(len(ranked))
        for word, count in ranked:
            token = word.encode("utf-8")
            if len(token) > 255:
                continue
            body.append(len(token))
            body.extend(token)
            body.extend(struct.pack("<H", min(count, 65535)))
        records.append((fnv1a(context), bytes(body)))

    records.sort(key=lambda item: (item[0], item[1]))
    index_size = len(records) * 8
    data_start = 12 + index_size
    offsets = []
    cursor = data_start
    for h, body in records:
        offsets.append((h, cursor))
        cursor += len(body)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("wb") as out:
        out.write(MAGIC)
        out.write(struct.pack("<II", VERSION, len(records)))
        for h, offset in offsets:
            out.write(struct.pack("<II", h, offset))
        for _, body in records:
            out.write(body)

    size = args.output.stat().st_size
    print(f"Built {args.output}: {len(records):,} contexts, {size / 1024 / 1024:.2f} MiB")
    if not 20 * 1024 * 1024 <= size <= 30 * 1024 * 1024:
        print("NOTE: output is outside the requested 20–30 MiB pack target; use a larger licensed corpus or adjust pruning.")
if __name__ == "__main__":
    main()
