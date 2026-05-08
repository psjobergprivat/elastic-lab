#!/usr/bin/env python3
"""
Fetches public-domain texts from Project Gutenberg (EN/FR/DE/ES) and
Wikipedia (RU/AR/HE, CC BY-SA), extracts clean sentences, and writes
per-language CSV files plus a combined sentences.csv into
backend/src/main/resources/testdata/values/.

Run once from the repo root:
    python3 fetch_sentences.py
"""

import json
import random
import re
import sys
import time
import urllib.request
from pathlib import Path

OUT_DIR = Path(__file__).parent / "backend/src/main/resources/testdata/values"
SENTENCES_PER_LANG = 150
MIN_LEN = 40
MAX_LEN = 200

GUTENBERG_SOURCES = [
    ("en", "https://www.gutenberg.org/files/1342/1342-0.txt"),  # Pride and Prejudice
    ("de", "https://www.gutenberg.org/files/2229/2229-0.txt"),  # Faust
    ("es", "https://www.gutenberg.org/files/2000/2000-0.txt"),  # Don Quijote
]

# Wikipedia extracts API — CC BY-SA, clean prose, consistent across all scripts
WIKIPEDIA_SOURCES = [
    ("fr", "https://fr.wikipedia.org/w/api.php?action=query&titles=France&prop=extracts&exsectionformat=plain&format=json"),
    ("ru", "https://ru.wikipedia.org/w/api.php?action=query&titles=%D0%A0%D0%BE%D1%81%D1%81%D0%B8%D1%8F&prop=extracts&exsectionformat=plain&format=json"),
    ("ar", "https://ar.wikipedia.org/w/api.php?action=query&titles=%D9%85%D8%B5%D8%B1&prop=extracts&exsectionformat=plain&format=json"),
    ("he", "https://he.wikipedia.org/w/api.php?action=query&titles=%D7%99%D7%A9%D7%A8%D7%90%D7%9C&prop=extracts&exsectionformat=plain&format=json"),
    ("zh", "https://zh.wikipedia.org/w/api.php?action=query&titles=%E4%B8%AD%E5%8D%8E%E4%BA%BA%E6%B0%91%E5%85%B1%E5%92%8C%E5%9B%BD&prop=extracts&exsectionformat=plain&format=json&redirects=1"),
]


def fetch_bytes(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "elastic-lab-testdata/1.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def fetch_text(url: str) -> str:
    raw = fetch_bytes(url)
    for enc in ("utf-8", "utf-8-sig", "latin-1"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def fetch_wikipedia_text(url: str) -> str:
    data = json.loads(fetch_bytes(url))
    pages = data.get("query", {}).get("pages", {})
    for page in pages.values():
        return page.get("extract", "")
    return ""


def strip_gutenberg_header_footer(text: str) -> str:
    start = re.search(r"\*\*\* ?START OF (THE|THIS) PROJECT GUTENBERG", text, re.IGNORECASE)
    end = re.search(r"\*\*\* ?END OF (THE|THIS) PROJECT GUTENBERG", text, re.IGNORECASE)
    if start:
        text = text[start.end():]
    if end:
        text = text[:end.start()]
    return text


def strip_html(text: str) -> str:
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"\s{2,}", " ", text)
    return text


def extract_sentences(text: str) -> list[str]:
    # Western punctuation requires trailing space; CJK full-width punctuation does not
    parts = re.split(r"(?<=[.!?؟।])\s+|(?<=[。！？])", text)
    sentences = []
    for part in parts:
        s = " ".join(part.split())
        if MIN_LEN <= len(s) <= MAX_LEN and not re.search(r"https?://", s) and not s.isupper():
            sentences.append(s)
    return sentences


def write_csv(lang: str, sentences: list[str]) -> None:
    path = OUT_DIR / f"sentences_{lang}.csv"
    path.write_text("\n".join(sentences[:SENTENCES_PER_LANG]), encoding="utf-8")
    print(f"  wrote {min(len(sentences), SENTENCES_PER_LANG)} sentences -> {path}")


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_sentences: list[str] = []

    for lang, url in GUTENBERG_SOURCES:
        print(f"Fetching {lang} from Gutenberg …")
        try:
            text = strip_gutenberg_header_footer(fetch_text(url))
            sentences = extract_sentences(text)
            if len(sentences) < 20:
                print(f"  WARNING: only {len(sentences)} sentences for {lang}")
            write_csv(lang, sentences)
            all_sentences.extend(sentences[:SENTENCES_PER_LANG])
            time.sleep(1)
        except Exception as e:
            print(f"  ERROR fetching {lang}: {e}", file=sys.stderr)

    for lang, url in WIKIPEDIA_SOURCES:
        print(f"Fetching {lang} from Wikipedia …")
        try:
            text = strip_html(fetch_wikipedia_text(url))
            sentences = extract_sentences(text)
            if len(sentences) < 20:
                print(f"  WARNING: only {len(sentences)} sentences for {lang}")
            write_csv(lang, sentences)
            all_sentences.extend(sentences[:SENTENCES_PER_LANG])
            time.sleep(1)
        except Exception as e:
            print(f"  ERROR fetching {lang}: {e}", file=sys.stderr)

    random.shuffle(all_sentences)
    combined_path = OUT_DIR / "sentences_multilang.csv"
    combined_path.write_text("\n".join(all_sentences), encoding="utf-8")
    print(f"\nCombined: {len(all_sentences)} sentences -> {combined_path}")
    print("Done. Add fields in field_catalog.csv referencing file:sentences_multilang.csv")


if __name__ == "__main__":
    main()
