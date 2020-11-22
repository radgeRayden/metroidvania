#!/usr/bin/env bash
set -euo pipefail

pushd ./src
../bin/game boot.sc -amalgamated
popd
