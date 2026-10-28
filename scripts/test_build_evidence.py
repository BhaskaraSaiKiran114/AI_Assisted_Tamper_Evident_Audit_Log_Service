"""Regression checks for false-positive validation evidence, using synthetic reports only."""

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("evidence", Path(__file__).with_name("build-evidence.py"))
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


class EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.reports = self.root / "target"
        (self.root / "src").mkdir()
        (self.root / "src/example.java").write_text("synthetic source", encoding="utf-8")
        (self.root / "pom.xml").write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0"><build><plugins><plugin>
          <artifactId>jacoco-maven-plugin</artifactId><configuration><rules><rule><element>BUNDLE</element><limits>
          <limit><counter>LINE</counter><minimum>0.85</minimum></limit></limits></rule></rules></configuration>
          </plugin></plugins></build></project>''', encoding="utf-8")
        self.git = patch.object(evidence, "git", side_effect=lambda root, *args:
                               "pom.xml\0src/example.java" if args[0] == "ls-files" else "synthetic-fixture")
        self.git.start()
        self.addCleanup(self.git.stop)
        (self.reports / "surefire-reports").mkdir(parents=True)
        (self.reports / "failsafe-reports").mkdir()
        for name, directory in [("ResourceAuthorizationIntegrationTest", "surefire-reports"),
                                ("ResourceScopeTest", "surefire-reports"), ("VerifierArtifactIT", "failsafe-reports")]:
            (self.reports / directory / f"TEST-{name}.xml").write_text(
                f'<testsuite name="synthetic.{name}" tests="1" failures="0" errors="0" skipped="0"/>', encoding="utf-8")
        (self.reports / "site/jacoco").mkdir(parents=True)
        self.coverage = self.reports / "site/jacoco/jacoco.xml"
        self.coverage.write_text('<report>' + ''.join(f'<counter type="{kind}" covered="90" missed="10"/>'
                                                      for kind in ("LINE", "BRANCH", "METHOD")) + '</report>', encoding="utf-8")
        self.run_evidence("--begin")

    def run_evidence(self, *args):
        with patch.object(sys, "argv", ["build-evidence.py", "--source-root", str(self.root), "--build-root", str(self.root),
                                      "--report-root", str(self.reports), *args]), \
                patch.object(evidence.subprocess, "run") as process:
            process.return_value.returncode = 1  # No remote URL in a synthetic fixture.
            evidence.main()

    def test_pass_binds_reports_and_does_not_forge_human_affirmation(self):
        self.run_evidence()
        result = json.loads((self.reports / "evidence/validation.json").read_text())
        self.assertEqual(result["tests"]["tests"], 3)
        self.assertTrue(result["dirty"])
        self.assertIn("candidate review", result["humanAffirmation"])
        self.assertIn("site/jacoco/jacoco.xml", result["artifacts"])

    def test_changed_source_cannot_reuse_previous_reports(self):
        (self.root / "src/example.java").write_text("changed", encoding="utf-8")
        with self.assertRaisesRegex(SystemExit, "Build inputs changed"):
            self.run_evidence()

    def test_skips_failures_and_errors_cannot_be_accepted(self):
        for key in ("skipped", "failures", "errors"):
            path = self.reports / "surefire-reports/TEST-ResourceScopeTest.xml"
            path.write_text(f'<testsuite name="synthetic.ResourceScopeTest" tests="1" {key}="1"/>', encoding="utf-8")
            with self.assertRaisesRegex(SystemExit, "Test evidence rejected"):
                self.run_evidence()

    def test_missing_reference_suites_cannot_be_described_as_postgresql_proof(self):
        with self.assertRaisesRegex(SystemExit, "Required suite missing"):
            self.run_evidence("--reference")

    def test_low_coverage_cannot_be_accepted(self):
        self.coverage.write_text(self.coverage.read_text().replace('covered="90"', 'covered="1"'), encoding="utf-8")
        with self.assertRaisesRegex(SystemExit, "Coverage gate failed"):
            self.run_evidence()

    def test_missing_reports_and_missing_coverage_cannot_be_accepted(self):
        self.coverage.unlink()
        with self.assertRaises(FileNotFoundError):
            self.run_evidence()
        (self.reports / "failsafe-reports/TEST-VerifierArtifactIT.xml").unlink()
        with self.assertRaisesRegex(SystemExit, "Missing failsafe"):
            self.run_evidence()


if __name__ == "__main__":
    unittest.main()
