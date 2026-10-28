"""Optional, dependency-free Python 3 hygiene check; prints locations, never matched secret values."""

from pathlib import Path
import re
import subprocess
import sys


def main():
    root = Path(__file__).resolve().parents[1]
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=root, capture_output=True, check=True,
    )
    names = sorted(set(result.stdout.decode("utf-8").split("\0")) - {""})
    suffixes = {".md", ".java", ".xml", ".yml", ".yaml", ".ps1", ".py", ".sql", ".properties", ".example", ".json"}
    patterns = {
        "private-key block": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
        "AWS access-key pattern": re.compile(r"AKIA[0-9A-Z]{16}"),
        "literal JWT pattern": re.compile(r"eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}"),
    }
    failures = []
    checked = 0
    for name in names:
        path = root / name
        if not path.is_file():  # A tracked deletion is handled by the diff, not scanned as content.
            continue
        if "interview_assignment_audit_log_service" in path.name.lower():
            failures.append(f"{name}: confidential source-assignment filename")
        if path.suffix not in suffixes:
            continue
        checked += 1
        text = path.read_text(encoding="utf-8-sig")
        for number, line in enumerate(text.splitlines(), 1):
            if line.rstrip() != line:
                failures.append(f"{name}:{number}: trailing whitespace")
            for label, pattern in patterns.items():
                if pattern.search(line):
                    failures.append(f"{name}:{number}: {label}")
        if path.suffix == ".md":
            for match in re.finditer(r"\]\(([^)]+)\)", text):
                link = match.group(1).strip("<>").split("#", 1)[0]
                if not link or ":" in link:
                    continue
                if not (path.parent / link).exists():
                    failures.append(f"{name}: missing local Markdown target")
    whitespace = subprocess.run(["git", "diff", "--check"], cwd=root, capture_output=True, text=True)
    if whitespace.returncode:
        failures.append("git diff --check failed; inspect the diff locally")
    for failure in failures:
        print(failure)
    print(f"Hygiene checked {checked} text files from {len(names)} non-ignored Git entries; findings={len(failures)}")
    print("Limited patterns/link/whitespace checks are not a comprehensive secret scan or security audit.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
