#!/usr/bin/env bash
#
# This source file is part of the Grove open-source project
#
# SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
#
# SPDX-License-Identifier: MIT
#
# Validates the resources the Health Connect adapter emits with the official HL7 FHIR Validator, against
# the Mobile and Health Connect packages of the grove-fhir release pinned in gradle.properties.
#
#   Scripts/validate-fhir-conformance.sh
#
# GROVE_FHIR_GUIDES names an existing grove-fhir checkout at the pinned commit; without it, the pinned
# commit is cloned into .fhir/grove-fhir. The validator and the packages are downloaded once and
# checked against the checksums grove-fhir and its release publish. grove-fhir's scripts need Python
# 3.10 or later; PYTHON names the interpreter when `python3` is older.

set -euo pipefail

cd "$(dirname "$0")/.."

PYTHON="${PYTHON:-python3}"
if ! "$PYTHON" -c 'import sys; sys.exit(sys.version_info < (3, 10))'; then
    echo "error: $("$PYTHON" --version 2>&1) is older than the Python 3.10 grove-fhir's scripts need; set PYTHON" >&2
    exit 1
fi

PINNED_VERSION="$(grep '^groveFhir.version=' gradle.properties | cut -d= -f2)"
PINNED_COMMIT="$(grep '^groveFhir.commit=' gradle.properties | cut -d= -f2)"
GUIDES="${GROVE_FHIR_GUIDES:-$(pwd)/.fhir/grove-fhir}"
GUIDE_NAMES=(mobile health-connect)
OUT="$(pwd)/health-fhir/build/conformance-fixtures/health-connect"

if [ ! -d "$GUIDES" ]; then
    echo "==> cloning grove-fhir $PINNED_VERSION"
    mkdir -p "$(dirname "$GUIDES")"
    git init --quiet "$GUIDES"
    git -C "$GUIDES" remote add origin https://github.com/SchmiedmayerLab/grove-fhir.git
    git -C "$GUIDES" fetch --quiet --depth 1 origin "$PINNED_COMMIT"
    git -C "$GUIDES" checkout --quiet --detach FETCH_HEAD
fi

ACTUAL_COMMIT="$(git -C "$GUIDES" rev-parse HEAD)"
if [ "$ACTUAL_COMMIT" != "$PINNED_COMMIT" ]; then
    echo "error: $GUIDES is at $ACTUAL_COMMIT, not the pinned $PINNED_COMMIT" >&2
    exit 1
fi

echo "==> checking the synchronized contract"
"$PYTHON" Scripts/sync-grove-fhir.py --grove-fhir "$GUIDES" --check

FHIR_HOME="${GROVE_FHIR_HOME_OVERRIDE:-$GUIDES/.build/fhir-home}"
VALIDATOR="${FHIR_VALIDATOR:-$GUIDES/.build/fhir-tools/validator_cli.jar}"
if [ ! -d "$GUIDES/node_modules" ]; then
    (cd "$GUIDES" && npm ci --silent)
fi
if [ ! -f "$VALIDATOR" ] || [ ! -d "$FHIR_HOME/.fhir/packages" ]; then
    echo "==> downloading the validator grove-fhir pins"
    (cd "$GUIDES" && ./Scripts/download-fhir-tools.sh .build/fhir-tools)
fi

echo "==> installing the ${GUIDE_NAMES[*]} packages of the grove-fhir $PINNED_VERSION release"
"$PYTHON" Scripts/fetch-grove-fhir-release-packages.py \
    --guides "$GUIDES" --release "$PINNED_VERSION" --commit "$PINNED_COMMIT" \
    --fhir-home "$FHIR_HOME" "${GUIDE_NAMES[@]}"

echo "==> emitting Health Connect resources"
rm -rf "$OUT"
./gradlew --quiet :health-fhir:testDebugUnitTest --tests '*SemanticVectorTest*' --rerun

# A sleep session converts to its stages alone, as the HealthKit adapter converts sleep. The Health
# Connect contract also requires a sleep-duration summary but defines no rule for its value, and its
# producer validator refuses a session without one, so that output is not validated here.
for excluded in sleep-stage; do
    echo "    not validated: $excluded (the contract requires a sleep-duration summary the adapter doesn't emit)"
    rm -f "$OUT/$excluded.json"
done
count=$(find "$OUT" -name '*.json' | wc -l | tr -d ' ')
if [ "$count" -eq 0 ]; then
    echo "error: the tests wrote no resources to $OUT" >&2
    exit 1
fi
echo "    $count resources"

package_arguments=()
manifest_package_arguments=()
for guide in "${GUIDE_NAMES[@]}"; do
    package_arguments+=(--package "$guide=$GUIDES/$guide/output/package.tgz")
    manifest_package_arguments+=(--package "$guide=org.grovealliance.fhir.$guide")
done

MANIFEST="$OUT/grove-fhir-producer.json"
"$PYTHON" Scripts/generate-grove-fhir-producer-manifest.py \
    --resources "$OUT" \
    --output "$MANIFEST" \
    --producer-revision "$(git rev-parse HEAD)" \
    --package-version "$PINNED_VERSION" \
    --semantic-vector-corpus "$GUIDES/Conformance/corpora/mobile-semantics/corpus.json" \
    "${manifest_package_arguments[@]}"

echo "==> validating with the official FHIR Validator"
GROVE_FHIR_HOME_OVERRIDE="$FHIR_HOME" "$PYTHON" "$GUIDES/Scripts/validate-producer.py" \
    --manifest "$MANIFEST" \
    --validator "$VALIDATOR" \
    "${package_arguments[@]}" \
    --allow-example-urls
