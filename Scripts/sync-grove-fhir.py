#!/usr/bin/env python3
#
# This source file is part of the Grove open-source project
#
# SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
#
# SPDX-License-Identifier: MIT
#

"""Brings the FHIR modules in line with the pinned grove-fhir release.

Copies the grove-fhir files the FHIR modules' tests read, and writes the Kotlin the generators derive
from grove-fhir's catalogs. The checkout must be at the commit pinned in gradle.properties.

    python3 Scripts/sync-grove-fhir.py --grove-fhir ../grove-fhir           # write
    python3 Scripts/sync-grove-fhir.py --grove-fhir ../grove-fhir --check   # fail on drift (CI)

--allow-unpinned skips the commit check, to try a newer grove-fhir without moving the pin.
"""

import argparse
import filecmp
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# grove-fhir path -> path in this repository. A directory is copied whole.
SYNCED_PATHS = {
    "catalog/exchange-protocol.json": "fhir-contract/src/test/resources/grove-fhir/exchange-protocol.json",
    "Conformance/corpora/mobile-exchange": "fhir-contract/src/test/resources/grove-fhir/corpora/mobile-exchange",
    "Conformance/corpora/mobile-semantics": "health-fhir/src/test/resources/grove-fhir/corpora/mobile-semantics",
}

# Each generator writes Kotlin from the grove-fhir checkout: (script, arguments, output file).
CONTRACT_GENERATED = "fhir-contract/src/generated/kotlin/org/grovealliance/fhir/contract"
GENERATORS = [
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "resource-types"], f"{CONTRACT_GENERATED}/ResourceType.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "reference-policy"], f"{CONTRACT_GENERATED}/ReferencePolicy.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "workout-activities"], f"{CONTRACT_GENERATED}/WorkoutActivity.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "workout-segment-types"], f"{CONTRACT_GENERATED}/WorkoutSegmentType.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "code-system-urls"], f"{CONTRACT_GENERATED}/CodeSystemUrl.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "extension-urls"], f"{CONTRACT_GENERATED}/ExtensionUrl.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "profiles"], f"{CONTRACT_GENERATED}/Profile.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "rules"], f"{CONTRACT_GENERATED}/ExchangeGraphRule.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "identity-kinds"], f"{CONTRACT_GENERATED}/OpaqueIdentityKind.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "contract"], f"{CONTRACT_GENERATED}/ExchangeContract.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "profile-claims"], f"{CONTRACT_GENERATED}/ProfileClaims.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "adapter-contracts"], f"{CONTRACT_GENERATED}/AdapterContracts.kt"),
    ("Scripts/generate-grove-fhir-contract.py", ["--part", "measurements"], f"{CONTRACT_GENERATED}/Measurements.kt"),
    (
        "Scripts/generate-grove-fhir-contract.py",
        ["--part", "health-connect"],
        "health-fhir/src/generated/kotlin/org/grovealliance/health/fhir/internal/HealthConnectCatalog.kt",
    ),
]


def pin():
    properties = {}
    for line in (ROOT / "gradle.properties").read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    return properties["groveFhir.version"], properties["groveFhir.commit"]


def checkout_commit(grove_fhir):
    return subprocess.run(
        ["git", "-C", str(grove_fhir), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def same(source, target):
    if source.is_dir():
        if not target.is_dir():
            return False
        comparison = filecmp.dircmp(source, target)
        return not (comparison.left_only or comparison.right_only or comparison.diff_files or comparison.funny_files) and all(
            same(source / name, target / name) for name in comparison.common_dirs
        )
    return target.is_file() and filecmp.cmp(source, target, shallow=False)


def copy(source, target):
    if target.is_dir():
        shutil.rmtree(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    if source.is_dir():
        shutil.copytree(source, target)
    else:
        shutil.copyfile(source, target)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--grove-fhir", type=Path, required=True, help="a grove-fhir checkout")
    parser.add_argument("--check", action="store_true", help="fail instead of writing when anything differs")
    parser.add_argument("--allow-unpinned", action="store_true", help="accept a checkout at another commit")
    arguments = parser.parse_args()

    version, commit = pin()
    actual = checkout_commit(arguments.grove_fhir)
    if actual != commit and not arguments.allow_unpinned:
        print(f"error: grove-fhir is at {actual}, but {version} is pinned at {commit}", file=sys.stderr)
        return 1

    drift = []
    for source_path, target_path in SYNCED_PATHS.items():
        source = arguments.grove_fhir / source_path
        target = ROOT / target_path
        if same(source, target):
            continue
        drift.append(target_path)
        if not arguments.check:
            copy(source, target)

    for script, script_arguments, output_path in GENERATORS:
        generated = subprocess.run(
            [sys.executable, str(ROOT / script), "--grove-fhir", str(arguments.grove_fhir), *script_arguments],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
        output = ROOT / output_path
        if output.is_file() and output.read_text() == generated:
            continue
        drift.append(output_path)
        if not arguments.check:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(generated)

    if drift and arguments.check:
        print(f"error: out of date with grove-fhir {version}; run Scripts/sync-grove-fhir.py:", file=sys.stderr)
        for path in drift:
            print(f"  {path}", file=sys.stderr)
        return 1
    for path in drift:
        print(f"updated {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
