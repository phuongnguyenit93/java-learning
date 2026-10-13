"""Regression tests for build-input V2 hashing, run by GitHub Actions with Python 3."""

import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "local-run-fingerprint-v2.py"
SPEC = importlib.util.spec_from_file_location("local_run_fingerprint", SCRIPT)
fingerprint_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(fingerprint_module)


class FingerprintV2Tests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.module_a = "module/example/a"
        self.module_b = "module/example/b"
        self.module_other = "module/example/other"
        self.write("build.gradle", "plugins { id 'java' }")
        self.write("module/example/a/master.json", json.dumps({"SERVICE_NAME": {"VALUE": "A"}}))
        self.write("module/example/a/gradle.properties", "IS_MODULE=TRUE")
        self.write("module/example/a/properties.json", json.dumps({
            "MODULE_DEPEND": {"MODULE_DEPEND_LIST": {"VALUE": "B"}}}))
        self.write("module/example/a/src/A.java", "class A {}")
        self.write("module/example/b/master.json", json.dumps({"SERVICE_NAME": {"VALUE": "B"}}))
        self.write("module/example/b/gradle.properties", "IS_MODULE=TRUE")
        self.write("module/example/b/src/B.java", "class B {}")
        self.write("module/example/other/src/C.java", "class C {}")
        self.write("project-build/gradle-runtime/src/main/resources/local-run/fingerprint-v2-roots.txt",
                   "build.gradle\nproject-build/gradle-runtime\nproject-build/springboot-runtime\n")
        self.write("project-build/gradle-runtime/src/main/resources/structure/module-structure.txt",
                   "module\nold generated structure\n")
        self.write("project-build/springboot-runtime/common.txt", "common")
        self.git("init", "-q")
        self.git("config", "user.email", "test@example.com")
        self.git("config", "user.name", "Test")
        self.commit()
        self.root_patch = patch.object(fingerprint_module, "ROOT", self.root)
        self.inputs_patch = patch.object(fingerprint_module, "INPUTS", self.root / (
            "project-build/gradle-runtime/src/main/resources/local-run/fingerprint-v2-roots.txt"))
        self.root_patch.start()
        self.inputs_patch.start()
        self.addCleanup(self.root_patch.stop)
        self.addCleanup(self.inputs_patch.stop)

    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.root), *args], check=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True).stdout.strip()

    def write(self, path, content):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    def commit(self):
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "fixture")

    def test_v2_transitive_and_shared_inputs(self):
        original = fingerprint_module.fingerprint(self.module_a, "HEAD")
        self.assertRegex(original, r"^[0-9a-f]{64}$")
        self.assertNotEqual(original, self.git("rev-parse", f"HEAD:{self.module_a}"))  # V1
        self.assertEqual(original, fingerprint_module.fingerprint(self.module_a, "HEAD"))

        self.write("module/example/other/src/C.java", "class C { int unrelated; }")
        self.commit()
        self.assertEqual(original, fingerprint_module.fingerprint(self.module_a, "HEAD"))

        self.write("module/example/b/src/B.java", "class B { int dependency; }")
        self.commit()
        after_dependency = fingerprint_module.fingerprint(self.module_a, "HEAD")
        self.assertNotEqual(original, after_dependency)

        self.write("build.gradle", "plugins { id 'java-library' }")
        self.commit()
        after_gradle = fingerprint_module.fingerprint(self.module_a, "HEAD")
        self.assertNotEqual(after_dependency, after_gradle)

        self.write("project-build/springboot-runtime/common.txt", "different runtime")
        self.commit()
        self.assertNotEqual(after_gradle, fingerprint_module.fingerprint(self.module_a, "HEAD"))

    def test_uses_committed_snapshot_not_dirty_source(self):
        committed = fingerprint_module.fingerprint(self.module_a, "HEAD")
        self.write("module/example/a/src/A.java", "class A { int dirty; }")
        self.assertEqual(committed, fingerprint_module.fingerprint(self.module_a, "HEAD"))
        self.commit()
        self.assertNotEqual(committed, fingerprint_module.fingerprint(self.module_a, "HEAD"))

    def test_generated_structure_does_not_invalidate_local_run(self):
        original = fingerprint_module.fingerprint(self.module_a, "HEAD")
        self.write("project-build/gradle-runtime/src/main/resources/structure/module-structure.txt",
                   "module\nnew generated structure\n")
        self.commit()
        self.assertEqual(original, fingerprint_module.fingerprint(self.module_a, "HEAD"))

        # All other committed Gradle runtime inputs remain fingerprinted.
        self.write("project-build/gradle-runtime/src/main/groovy/NewPlugin.groovy",
                   "class NewPlugin {}\n")
        self.commit()
        self.assertNotEqual(original, fingerprint_module.fingerprint(self.module_a, "HEAD"))

    def test_direct_gradle_project_dependency_is_included(self):
        self.write("module/example/a/properties.json", "{}")
        self.write("module/example/a/build.gradle",
                   "dependencies { implementation project(':module:example:other') }")
        self.commit()
        original = fingerprint_module.fingerprint(self.module_a, "HEAD")
        self.write("module/example/other/src/C.java", "class C { int newDependencyCode; }")
        self.commit()
        self.assertNotEqual(original, fingerprint_module.fingerprint(self.module_a, "HEAD"))

    def test_historical_revision_is_stable(self):
        previous = self.git("rev-parse", "HEAD")
        first = fingerprint_module.fingerprint(self.module_a, previous)
        self.write("module/example/a/src/A.java", "class A { int newer; }")
        self.commit()
        self.assertEqual(first, fingerprint_module.fingerprint(self.module_a, previous))
        self.assertNotEqual(first, fingerprint_module.fingerprint(self.module_a, "HEAD"))

    def test_historical_dependency_graph_is_read_from_revision_not_checkout(self):
        baseline = self.git("rev-parse", "HEAD")
        original = fingerprint_module.fingerprint(self.module_a, baseline)
        self.write("module/example/c/master.json", json.dumps({"SERVICE_NAME": {"VALUE": "C"}}))
        self.write("module/example/c/gradle.properties", "IS_MODULE=TRUE")
        self.write("module/example/c/src/C.java", "class C {}")
        self.write("module/example/a/properties.json", json.dumps({
            "MODULE_DEPEND": {"MODULE_DEPEND_LIST": {"VALUE": "C"}}}))
        self.commit()
        latest = fingerprint_module.fingerprint(self.module_a, "HEAD")
        self.assertNotEqual(original, latest)
        self.assertEqual(original, fingerprint_module.fingerprint(self.module_a, baseline))

        self.write("module/example/c/src/C.java", "class C { int changed; }")
        self.commit()
        self.assertNotEqual(latest, fingerprint_module.fingerprint(self.module_a, "HEAD"))
        self.assertEqual(original, fingerprint_module.fingerprint(self.module_a, baseline))


if __name__ == "__main__":
    unittest.main()
