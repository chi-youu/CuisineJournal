"""Inspect public source candidates without staging, committing or touching the project's Git state."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BINARY = {".png", ".jpg", ".jpeg", ".ogg", ".woff", ".woff2"}
PATTERNS = {
    "private_key": re.compile(r"-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----"),
    "github_token": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{24,}|github_pat_[A-Za-z0-9_]{30,})\b"),
    "cloud_access_key": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "service_token": re.compile(r"\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{32,}\b"),
    "credentials_in_url": re.compile(r"https?://[^\s/@:]+:[^\s/@]{6,}@"),
    "jwt": re.compile(r"\beyJ[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\b"),
    "credential_assignment": re.compile(r"(?i)(?:password|client_secret|access_token|api_key)\s*[:=]\s*[\"'][^\"'\s]{8,}[\"']"),
    "machine_path": re.compile(r"(?<![A-Za-z0-9])(?:[A-DF-Z]:[/\\]|E:(?![/\\]Minecraft/test-instance)[/\\]|/" + r"Users/|/home/[A-Za-z0-9_.-]+/)"),
}


def candidates(root):
    """A temporary bare Git directory applies exactly Git's ignore rules, even before git init."""
    root = Path(root).resolve()
    with tempfile.TemporaryDirectory(prefix="cj-source-index-") as directory:
        environment = os.environ.copy()
        for key in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE", "GIT_COMMON_DIR"):
            environment.pop(key, None)
        subprocess.run(["git", "init", "--bare", "--quiet", directory], check=True, env=environment)
        result = subprocess.run(["git", "--git-dir=" + directory, "--work-tree=" + str(root),
                                 "-c", "core.excludesFile=", "ls-files", "--others", "--exclude-standard", "-z"],
                                check=True, stdout=subprocess.PIPE, env=environment)
    return sorted(path for path in result.stdout.decode("utf-8").split("\0") if path)


def scan_text(path, text):
    findings = []
    for number, line in enumerate(text.splitlines(), 1):
        for kind, pattern in PATTERNS.items():
            if pattern.search(line):
                findings.append({"path": path, "line": number, "kind": kind})
    return findings


def inspect(root):
    root = Path(root).resolve()
    files = []
    findings = []
    selected = candidates(root)
    selected_set = set(selected)
    for relative in selected:
        path = root / relative
        if path.is_symlink() or not path.resolve().is_relative_to(root):
            findings.append({"path": relative, "kind": "linked_or_escaping_path"})
            continue
        data = path.read_bytes()
        files.append({"path": relative, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
        if path.suffix.lower() == ".jar":
            if relative != "gradle/wrapper/gradle-wrapper.jar":
                findings.append({"path": relative, "kind": "third_party_jar"})
        elif path.suffix.lower() not in BINARY:
            try:
                text = data.decode("utf-8-sig")
                findings.extend(scan_text(relative, text))
                if path.suffix.lower() == ".md":
                    findings.extend(check_links(root, relative, text, selected_set))
            except UnicodeDecodeError:
                findings.append({"path": relative, "kind": "unreviewed_binary"})
        if len(data) > 10 * 1024 * 1024:
            findings.append({"path": relative, "kind": "large_file"})
    return {"files": files, "file_count": len(files), "total_bytes": sum(row["bytes"] for row in files),
            "findings": findings, "finding_count": len(findings)}


def check_links(root, relative, text, selected):
    findings = []
    for number, line in enumerate(text.splitlines(), 1):
        for match in re.finditer(r"\]\(([^)]+)\)", line):
            target = match.group(1).strip("<>").split("#", 1)[0]
            if not target or re.match(r"[A-Za-z][A-Za-z0-9+.-]*:", target):
                continue
            destination = (root / relative).parent.joinpath(target).resolve()
            if not destination.is_relative_to(root) or destination.relative_to(root).as_posix() not in selected:
                findings.append({"path": relative, "line": number, "kind": "unpublished_document_link"})
    return findings


def copy_source(root, destination, report):
    root, destination = Path(root).resolve(), Path(destination).resolve()
    if report["findings"]:
        raise ValueError("Resolve candidate findings before making a public source copy")
    if destination == root or root.is_relative_to(destination):
        raise ValueError("Source destination cannot replace the project or an ancestor")
    if destination.exists():
        raise ValueError("Refusing to overwrite an existing source copy")
    if destination.is_relative_to(root) and not destination.is_relative_to(root / "build" / "repository-preparation"):
        raise ValueError("Copies inside the project must stay under build/repository-preparation")
    destination.mkdir(parents=True)
    for row in report["files"]:
        source, target = root / row["path"], destination / row["path"]
        # A concurrent edit must not silently create a different snapshot from the reviewed manifest.
        if source.is_symlink() or hashlib.sha256(source.read_bytes()).hexdigest() != row["sha256"]:
            raise ValueError("Source changed during copy: " + row["path"])
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        if hashlib.sha256(target.read_bytes()).hexdigest() != row["sha256"]:
            raise ValueError("Copy verification failed: " + row["path"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--copy-to", type=Path)
    args = parser.parse_args()
    report = inspect(args.root)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Public source: %d files, %d bytes, %d findings" %
          (report["file_count"], report["total_bytes"], report["finding_count"]))
    for finding in report["findings"]:
        print("%s:%s %s" % (finding["path"], finding.get("line", "-"), finding["kind"]))
    if report["findings"]:
        return 1
    if args.copy_to:
        copy_source(args.root, args.copy_to, report)
        print("Clean source copy created and verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
