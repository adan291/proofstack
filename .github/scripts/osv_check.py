#!/usr/bin/env python3
"""Fails when a runtime dependency has a known vulnerability in OSV (https://osv.dev).

Usage: osv_check.py FILE...   where each FILE is the output of `mvn dependency:list -DoutputFile=...`.
Only public data is sent to OSV: Maven coordinates and versions, no credentials.
"""
import json
import re
import sys
import urllib.request

LINE = re.compile(r"^\s+([\w.\-]+):([\w.\-]+):[\w\-]+(?::[\w.\-]+)?:([\w.\-]+):(compile|runtime)\b")
OSV = "https://api.osv.dev/v1/querybatch"


def dependencies(paths):
    found = set()
    for path in paths:
        with open(path, encoding="utf-8") as f:
            for line in f:
                m = LINE.match(line)
                if m and m.group(1) != "dev.proofstack":
                    found.add((f"{m.group(1)}:{m.group(2)}", m.group(3)))
    return sorted(found)


def main(paths):
    deps = dependencies(paths)
    if not deps:
        print("osv: no dependencies found in " + ", ".join(paths), file=sys.stderr)
        return 2
    body = json.dumps({"queries": [{"package": {"ecosystem": "Maven", "name": n}, "version": v} for n, v in deps]})
    request = urllib.request.Request(OSV, data=body.encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=60) as response:
        results = json.load(response)["results"]
    vulnerable = [(dep, [v["id"] for v in r.get("vulns", [])]) for dep, r in zip(deps, results) if r.get("vulns")]
    for (name, version), ids in vulnerable:
        print(f"VULNERABLE {name}:{version} -> {', '.join(ids)}")
    print(f"osv: {len(deps)} runtime dependencies checked, {len(vulnerable)} with known vulnerabilities")
    return 1 if vulnerable else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
