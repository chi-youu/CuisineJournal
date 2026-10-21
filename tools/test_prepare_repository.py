"""Repository preparation tests use synthetic secrets only and never modify the working tree."""
from pathlib import Path
import tempfile
import unittest

from prepare_repository import candidates, check_links, copy_source, inspect, scan_text


class RepositoryPreparationTest(unittest.TestCase):
    def test_project_template_notices_are_scoped(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ignore = Path(__file__).resolve().parents[1] / ".gitignore"
            (root / ".gitignore").write_bytes(ignore.read_bytes())
            for name in ("LICENSE.txt", "README.txt", "LICENSE.md"):
                (root / name).write_text("fixture", encoding="utf-8")
            notice = root / "docs/third-party/forge-mdk/LICENSE.txt"
            notice.parent.mkdir(parents=True)
            notice.write_text("upstream notice", encoding="utf-8")
            selected = candidates(root)
            self.assertNotIn("LICENSE.txt", selected)
            self.assertNotIn("README.txt", selected)
            self.assertIn("LICENSE.md", selected)
            self.assertIn("docs/third-party/forge-mdk/LICENSE.txt", selected)

    def test_ignore_rules_and_copy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "source"
            root.mkdir()
            (root / ".gitignore").write_text("/*\n!/.gitignore\n!/src/\n!/gradle/\n*.jar\n!/gradle/wrapper/gradle-wrapper.jar\n", encoding="utf-8")
            (root / "src").mkdir()
            (root / "src/example.java").write_text("class Example {}\n", encoding="utf-8")
            (root / "private.log").write_text("not public", encoding="utf-8")
            (root / "gradle/wrapper").mkdir(parents=True)
            (root / "gradle/wrapper/gradle-wrapper.jar").write_bytes(b"wrapper")
            self.assertEqual(candidates(root), [".gitignore", "gradle/wrapper/gradle-wrapper.jar", "src/example.java"])
            report = inspect(root)
            self.assertFalse(report["findings"])
            target = Path(directory) / "copy"
            copy_source(root, target, report)
            self.assertEqual(inspect(target)["files"], report["files"])
            self.assertFalse((target / "private.log").exists())
            self.assertFalse((root / ".git").exists())
            with self.assertRaises(ValueError):
                copy_source(root, target, report)
            with self.assertRaises(ValueError):
                copy_source(root, root, report)
            (root / "src/example.java").write_text("changed", encoding="utf-8")
            with self.assertRaises(ValueError):
                copy_source(root, Path(directory) / "changed-copy", report)

    def test_findings_do_not_echo_values(self):
        token = "ghp_" + "a" * 36
        private = "-----BEGIN " + "PRIVATE KEY-----"
        text = token + "\n" + private + "\n" + "password = '" + "x" * 12 + "'"
        findings = scan_text("example.txt", text)
        self.assertEqual({row["kind"] for row in findings},
                         {"github_token", "private_key", "credential_assignment"})
        self.assertNotIn(token, str(findings))
        self.assertTrue(scan_text("example.txt", "D:" + "/private/source"))
        self.assertFalse(scan_text("example.txt", "E:/Minecraft/test-instance/mods"))

    def test_document_links(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            self.assertFalse(check_links(root, "README.md", "[Build](docs/BUILDING.md#test)", {"docs/BUILDING.md"}))
            self.assertFalse(check_links(root, "README.md", "[External](https://example.org)", set()))
            self.assertTrue(check_links(root, "README.md", "[Private](CURRENT_STATUS.md)", set()))


if __name__ == "__main__":
    unittest.main()
