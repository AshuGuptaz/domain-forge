#!/usr/bin/env bash
# Downloads the Tranco top-1M domain ranking (https://tranco-list.eu) used by the evaluation harness.
set -euo pipefail
mkdir -p data && cd data
curl -fsSL -o tranco.zip https://tranco-list.eu/top-1m.csv.zip
unzip -o -q tranco.zip
echo "ok: $(wc -l < top-1m.csv) domains"
