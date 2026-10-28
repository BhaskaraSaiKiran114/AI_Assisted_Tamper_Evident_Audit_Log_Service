"""Bind actual test/coverage reports to Git identity and the exact build inputs; never sign an attestation."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def inputs(root):
    names = set(git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z").split("\0"))
    return {name: sha(root / name) for name in sorted(names) if name and (root / name).is_file()
            and (name.startswith(("src/", "config/", ".mvn/", "scripts/", ".github/"))
                 or name in ("pom.xml", "mvnw", "mvnw.cmd"))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--build-root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--report-root", type=Path, default=Path("target"))
    parser.add_argument("--begin", action="store_true")
    parser.add_argument("--reference", action="store_true", help="Require PostgreSQL runtime and readiness suites")
    args = parser.parse_args()
    root = args.source_root.resolve()
    reports = args.report_root.resolve()
    output = reports / "evidence"
    output.mkdir(parents=True, exist_ok=True)
    manifest = output / "build-inputs.json"
    current = inputs(root)
    if args.build_root.resolve() != root:
        for name, digest in current.items():
            copied = args.build_root / name
            if not copied.is_file() or sha(copied) != digest:
                raise SystemExit(f"Isolated build does not match source: {name}")
    if args.begin:
        manifest.write_text(json.dumps(current, indent=2) + "\n", encoding="utf-8")
        print("Recorded exact build-input manifest (no token, key or payload files)")
        return
    if not manifest.exists() or json.loads(manifest.read_text(encoding="utf-8")) != current:
        raise SystemExit("Build inputs changed or begin manifest missing; run a fresh build")
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites = {}
    artifacts = {}
    for directory in ("surefire-reports", "failsafe-reports"):
        files = sorted((reports / directory).glob("TEST-*.xml"))
        if not files:
            raise SystemExit(f"Missing {directory} XML reports")
        for path in files:
            suite = ET.parse(path).getroot()
            counts = {key: int(suite.get(key, "0")) for key in totals}
            suites[suite.get("name")] = counts
            for key in totals:
                totals[key] += counts[key]
            artifacts[str(path.relative_to(reports)).replace("\\", "/")] = sha(path)
    required = ["ResourceAuthorizationIntegrationTest", "ResourceScopeTest", "VerifierArtifactIT"]
    if args.reference:
        required += ["PostgresResourceAuthorizationIT", "PostgresReadinessExercise", "PostgresRuntimeSecurityIT",
                     "PostgresAppendIT", "PostgresReadVerificationIT", "PostgresLifecycleIT", "PostgresLifecycleRuntimeIT",
                     "PostgresExportIT", "PostgresExportRuntimeIT", "PostgresAccessReportIT", "PostgresAccessReportRuntimeIT"]
    for name in required:
        if not any(suite.endswith("." + name) and count["tests"] > 0 for suite, count in suites.items()):
            raise SystemExit(f"Required suite missing/empty: {name}")
    if totals["tests"] == 0 or any(totals[key] for key in ("failures", "errors", "skipped")):
        raise SystemExit(f"Test evidence rejected: {totals}")
    coverage_file = reports / "site/jacoco/jacoco.xml"
    coverage = {}
    tree = ET.parse(coverage_file).getroot()
    for counter in tree.findall("counter"):
        covered, missed = int(counter.get("covered")), int(counter.get("missed"))
        coverage[counter.get("type")] = dict(covered=covered, missed=missed,
                                            percent=round(100 * covered / (covered + missed), 4) if covered + missed else 100)
    if not {"LINE", "BRANCH", "METHOD"}.issubset(coverage):
        raise SystemExit("Missing coverage counters")
    # Read the actual Maven gate configuration rather than maintaining a second set of thresholds.
    from decimal import Decimal
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom = ET.parse(root / "pom.xml")
    for plugin in pom.findall(".//m:build/m:plugins/m:plugin", ns):
        if plugin.findtext("m:artifactId", namespaces=ns) != "jacoco-maven-plugin":
            continue
        for rule in plugin.findall(".//m:rule", ns):
            element = rule.findtext("m:element", namespaces=ns)
            subject = tree if element == "BUNDLE" else tree.find("package[@name='com/example/auditlog/security']")
            if subject is None:
                raise SystemExit("Required coverage package missing")
            for limit in rule.findall("m:limits/m:limit", ns):
                counter = subject.find(f"counter[@type='{limit.findtext('m:counter', namespaces=ns)}']")
                covered, missed = int(counter.get("covered")), int(counter.get("missed"))
                minimum = Decimal(limit.findtext("m:minimum", namespaces=ns))
                if covered + missed == 0 or Decimal(covered) / (covered + missed) < minimum:
                    raise SystemExit(f"Coverage gate failed: {element} {counter.get('type')}")
    for path in [coverage_file, *sorted((reports / "readiness").glob("*.json"))]:
        artifacts[str(path.relative_to(reports)).replace("\\", "/")] = sha(path)
    origin = git(root, "config", "--get", "remote.origin.url") if subprocess.run(
        ["git", "-C", str(root), "config", "--get", "remote.origin.url"], capture_output=True).returncode == 0 else None
    if origin and "://" in origin:
        from urllib.parse import urlsplit, urlunsplit
        url = urlsplit(origin)
        origin = urlunsplit((url.scheme, url.netloc.split("@")[-1], url.path, "", ""))
    result = dict(generatedAt=datetime.now(timezone.utc).isoformat(), repository=origin,
                  baseCommit=git(root, "rev-parse", "HEAD"), branch=git(root, "branch", "--show-current"),
                  dirty=bool(git(root, "status", "--porcelain")), buildInputsSha256=sha(manifest),
                  referenceProfile=args.reference, tests=totals, suites=suites, coverage=coverage, artifacts=artifacts,
                  ciRunUrl=(f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
                            if os.environ.get("GITHUB_RUN_ID") else None),
                  humanAffirmation="Not provided by this script; candidate review and affirmation required")
    (output / "validation.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(tests=totals, coverage=coverage), indent=2))


if __name__ == "__main__":
    main()
