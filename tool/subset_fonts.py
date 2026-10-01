#!/usr/bin/env python3
"""Builds the shipped font assets for core/design-system (NATIVE_PLAN.md §3.3).

The Flutter build bundles 18 MB of fonts, of which `noto_serif_tc_variable.ttf` alone is 16.8 MB.
Everything that font is used for is text we already have on disk — the scripture JSON and the ARB
strings — so the shipped font only needs those characters plus ASCII and CJK punctuation. Devotion
articles come from the network and are not known ahead of time, but an unknown CJK character falls
back to the device's own CJK font exactly the way the Flutter build's `fontFamilyFallback` chain
did, so nothing is lost by subsetting.

The variable axes are preserved, because both `exposure_variable.otf` and the Noto Serif TC subset
are rendered with `FontVariation` and minSdk is 26 for that reason (NATIVE_PLAN.md §6 R3).

Like `tool/build_bible_db.mjs` and `tool/arb_to_strings.mjs` this is NOT wired into Gradle: CI must
never regenerate shipped resources. Re-run by hand and commit the result.

Usage:
    python3 tool/subset_fonts.py [--check]
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LEGACY_FONTS = os.path.join(ROOT, "legacy", "flutter", "assets", "fonts")
BIBLE_ASSETS = os.path.join(ROOT, "legacy", "flutter", "assets", "bible")
L10N = os.path.join(ROOT, "legacy", "flutter", "lib", "l10n")
FONT_DIR = os.path.join(ROOT, "core", "design-system", "src", "main", "res", "font")

# Copied verbatim: already small enough, and every code point they can render may still show up in
# a devotion article or an AI answer. Subsetting them would buy under a megabyte at the cost of
# invisible holes in text the app does not own.
VERBATIM = ("openrunde_regular.otf", "openrunde_medium.otf", "exposure_variable.otf")

# Subsetted, because it is 16.8 MB of CJK and the character set is knowable up front.
SUBSET = "noto_serif_tc_variable.ttf"

# Latin-1 plus the typographic characters the AI answers and devotion articles are full of. Kept
# explicit rather than as a range so a device font is never the only source of a curly quote or an
# em dash. Duplicates are harmless; the set is deduplicated before use.
PUNCTUATION = (
    "…—–‘’“”·°±×÷≈≠≤≥"
    "‐‑‒―"
    "‚‛„‟"
    "′″‹›«»¡¿"
    "−"
    "€£¥₩¢"
    "→←↑↓⇒⇐"
    "✓✔✕✖★☆♥"
    "①②③④⑤⑥⑦⑧⑨⑩"
    "、。，．：；！？「」『』（）【】〔〕《》〈〉・～〜"
    "﻿"
)


def bible_characters() -> set[str]:
    """Every character in the scripture assets, i.e. exactly what the reader will render."""
    characters: set[str] = set()
    for path in glob.glob(os.path.join(BIBLE_ASSETS, "*", "*.json")):
        with open(path, encoding="utf-8") as handle:
            characters.update(handle.read())
    return characters


def arb_characters() -> set[str]:
    """Every translated string, so no UI label can render as a missing glyph."""
    characters: set[str] = set()
    for path in glob.glob(os.path.join(L10N, "*.arb")):
        with open(path, encoding="utf-8") as handle:
            for key, value in json.load(handle).items():
                if not key.startswith("@") and isinstance(value, str):
                    characters.update(value)
    return characters


def subset_characters() -> str:
    characters = set(chr(code) for code in range(0x20, 0x7F))
    characters.update(PUNCTUATION)
    characters.update(bible_characters())
    characters.update(arb_characters())
    # The character list is passed to fontTools as text, and a font must not carry the whitespace
    # and control characters that happen to appear inside the JSON. Unassigned code points have no
    # glyph to keep, so dropping them costs nothing.
    return "".join(sorted(c for c in characters if c.isprintable() or c == " "))


def run_fonttools(arguments: list[str]) -> None:
    try:
        subprocess.run([sys.executable, "-m", "fontTools.subset", *arguments], check=True)
    except FileNotFoundError as error:  # pragma: no cover - developer environment problem
        raise SystemExit(f"fontTools is not installed: {error}") from error


def build_subset(target: str) -> None:
    """Writes the subsetted Noto Serif TC, staging it so a partial run cannot leave a bad font."""
    source = os.path.join(LEGACY_FONTS, SUBSET)
    with tempfile.TemporaryDirectory() as workdir:
        staged = os.path.join(workdir, SUBSET)
        run_fonttools(
            [
                source,
                f"--text={subset_characters()}",
                # Layout features and the variable axes are what make the rendered text match the
                # Flutter build; only unused tables and TrueType hinting go.
                "--layout-features=*",
                "--no-hinting",
                "--desubroutinize",
                "--drop-tables+=DSIG",
                f"--output-file={staged}",
            ],
        )
        with open(staged, "rb") as staged_file, open(target, "wb") as destination:
            destination.write(staged_file.read())


def report() -> None:
    total = 0
    for name in sorted(os.listdir(FONT_DIR)):
        size = os.path.getsize(os.path.join(FONT_DIR, name))
        total += size
        print(f"{size / 1024 / 1024:6.2f} MB  {name}")
    print(f"{total / 1024 / 1024:6.2f} MB  total")


def build(check: bool) -> int:
    if not check:
        os.makedirs(FONT_DIR, exist_ok=True)
    for name in VERBATIM:
        source = os.path.join(LEGACY_FONTS, name)
        target = os.path.join(FONT_DIR, name)
        if not check:
            with open(source, "rb") as src, open(target, "wb") as dst:
                dst.write(src.read())
        elif not os.path.exists(target):
            print(f"missing: {target}")
            return 1

    target = os.path.join(FONT_DIR, SUBSET)
    if check:
        if not os.path.exists(target):
            print(f"missing: {target}")
            return 1
        with tempfile.TemporaryDirectory() as workdir:
            staged = os.path.join(workdir, SUBSET)
            build_subset(staged)
            with open(staged, "rb") as staged_file, open(target, "rb") as committed:
                if staged_file.read() != committed.read():
                    print(f"out of date: {target}")
                    return 1
    else:
        build_subset(target)

    report()
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Build the shipped design-system fonts.")
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify the committed fonts match what this script would produce",
    )
    raise SystemExit(build(parser.parse_args().check))
