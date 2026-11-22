#!/usr/bin/env python3
"""Fails if a translation misses a string key or uses different format placeholders than the default (English) file."""
import glob
import os
import re
import sys

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
ENTRY = re.compile(r'<(string|plurals)\s+name="([^"]+)"[^>]*>(.*?)</\1>', re.S)
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sd]")


def load(path):
    with open(path, encoding="utf-8") as f:
        return {m.group(2): sorted(PLACEHOLDER.findall(m.group(3))) for m in ENTRY.finditer(f.read())}


def main():
    base = load(os.path.join(RES, "values", "strings.xml"))
    errors = []
    for path in sorted(glob.glob(os.path.join(RES, "values-*", "strings.xml"))):
        lang = os.path.basename(os.path.dirname(path))
        other = load(path)
        for key in sorted(base.keys() - other.keys()):
            errors.append(f"{lang}: missing key '{key}'")
        for key in sorted(other.keys() - base.keys()):
            errors.append(f"{lang}: extra key '{key}' (not in values/strings.xml)")
        for key in sorted(base.keys() & other.keys()):
            if base[key] != other[key]:
                errors.append(f"{lang}: placeholders of '{key}' differ: {other[key]} != {base[key]}")
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    print(f"OK: {len(base)} strings, translations in sync")


if __name__ == "__main__":
    main()
