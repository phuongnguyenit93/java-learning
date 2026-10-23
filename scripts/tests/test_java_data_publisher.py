"""Regression tests for the Java data package format and validation failures."""

import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path


SOURCE = Path(__file__).resolve().parents[1] / "publish-java-data.py"
SPEC = importlib.util.spec_from_file_location("java_data_publisher", SOURCE)
publisher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(publisher)


class JavaDataPublisherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.data = self.root / "data"
        self.module = self.data / "module" / "EXAMPLE"
        self.module.mkdir(parents=True)
        self.search = self.data / "search"
        self.search.mkdir()
        self.knowledge = self.module / "knowledge" / "vi"
        self.knowledge.mkdir(parents=True)

        catalog = {
            "version": 1,
            "root": {
                "id": "module",
                "kind": "GROUP",
                "children": [{
                    "id": "example",
                    "kind": "MODULE",
                    "routeId": "EXAMPLE",
                    "knowledge": {"vi": "/module/EXAMPLE/knowledge/vi/index.json"},
                    "quiz": {"vi": "/module/EXAMPLE/quiz/vi/question.yml"},
                    "children": [],
                }],
            },
        }
        self.write_json(self.data / "module-catalog.json", catalog)
        self.write_json(self.knowledge / "index.json", {
            "version": 1,
            "sections": [{"content": "/module/EXAMPLE/knowledge/vi/content/lesson.md"}],
        })
        self.write_text(self.knowledge / "content" / "lesson.md", "# Lesson\n")
        self.write_text(self.module / "quiz" / "vi" / "question.yml", "questions: []\n")
        for lang in ("en", "vi"):
            self.write_json(self.search / f"knowledge-search.{lang}.json", {
                "version": 1, "language": lang, "documents": [],
            })

    def write_text(self, path, content):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def write_json(self, path, content):
        self.write_text(path, json.dumps(content))

    def test_validate_and_reproducible_package(self):
        files, module_count, ref_count, _ = publisher.validate(self.data)
        self.assertGreater(len(files), 3)
        self.assertEqual(module_count, 1)
        self.assertEqual(ref_count, 3)
        commit = "a" * 40
        output = self.root / "release"
        publisher.package(self.data, output, commit, "2026-01-01T00:00:00Z")
        first_sha = publisher.sha256(output / "java-learning-data.zip")
        publisher.package(self.data, output, commit, "2026-01-01T00:00:00Z")
        self.assertEqual(first_sha, publisher.sha256(output / "java-learning-data.zip"))
        with zipfile.ZipFile(output / "java-learning-data.zip") as archive:
            names = archive.namelist()
            manifest = json.loads(archive.read("manifest.json"))
        self.assertEqual(manifest["sourceCommit"], commit)
        self.assertEqual(manifest["fileCount"], len(files))
        self.assertIn("module-catalog.json", names)
        self.assertFalse(any(name.startswith("search/knowledge-search.") for name in names))
        self.assertEqual(len(names), len(files) + 1)

    def test_legacy_search_files_are_excluded_and_not_required(self):
        files, _, _, _ = publisher.validate(self.data)
        self.assertFalse(any("knowledge-search." in str(file) for file in files))
        for legacy in self.search.iterdir():
            legacy.unlink()
        self.search.rmdir()
        files, _, _, _ = publisher.validate(self.data)
        self.assertFalse(any("knowledge-search." in str(file) for file in files))

    def test_same_output_skips_even_when_source_commit_changes(self):
        output = self.root / "release"
        publisher.package(self.data, output, "a" * 40, "2026-01-01T00:00:00Z")
        zip_file = output / "java-learning-data.zip"
        checksum = output / "java-learning-data.zip.sha256"
        self.assertTrue(publisher.same_published_content(self.data, zip_file, checksum))

        # A new commit changes the package manifest/ZIP checksum, but not the
        # actual consumer-visible data. Avoid a redundant publish and deploy.
        newer = self.root / "new-release"
        publisher.package(self.data, newer, "b" * 40, "2026-01-02T00:00:00Z")
        self.assertNotEqual(publisher.sha256(zip_file),
                            publisher.sha256(newer / "java-learning-data.zip"))
        self.assertTrue(publisher.same_published_content(self.data, zip_file, checksum))

    def test_output_change_or_removed_file_requires_publication(self):
        output = self.root / "release"
        publisher.package(self.data, output, "a" * 40, "2026-01-01T00:00:00Z")
        zip_file = output / "java-learning-data.zip"
        checksum = output / "java-learning-data.zip.sha256"
        self.write_text(self.knowledge / "content" / "lesson.md", "# New lesson\n")
        self.assertFalse(publisher.same_published_content(self.data, zip_file, checksum))
        self.write_text(self.knowledge / "content" / "lesson.md", "# Lesson\n")
        self.write_text(self.module / "reference" / "references.yml", "references: []\n")
        self.assertFalse(publisher.same_published_content(self.data, zip_file, checksum))

    def test_existing_release_checksum_mismatch_fails_closed(self):
        output = self.root / "release"
        publisher.package(self.data, output, "a" * 40, "2026-01-01T00:00:00Z")
        checksum = output / "java-learning-data.zip.sha256"
        checksum.write_text("0" * 64 + "  java-learning-data.zip\n", encoding="ascii")
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            publisher.same_published_content(
                self.data, output / "java-learning-data.zip", checksum)

    def test_existing_release_missing_manifest_fails_closed(self):
        output = self.root / "release"
        publisher.package(self.data, output, "a" * 40, "2026-01-01T00:00:00Z")
        archive = output / "java-learning-data.zip"
        bad = output / "corrupt.zip"
        with zipfile.ZipFile(archive) as source, zipfile.ZipFile(bad, "w") as target:
            for name in source.namelist():
                if name != "manifest.json":
                    target.writestr(name, source.read(name))
        checksum = output / "java-learning-data.zip.sha256"
        checksum.write_text(publisher.sha256(bad) + "  java-learning-data.zip\n", encoding="ascii")
        with self.assertRaisesRegex(ValueError, "Invalid existing Java data Release entries"):
            publisher.same_published_content(self.data, bad, checksum)

    def test_missing_catalog_asset_is_rejected(self):
        (self.module / "quiz" / "vi" / "question.yml").unlink()
        with self.assertRaisesRegex(ValueError, "Missing referenced asset"):
            publisher.validate(self.data)

    def test_missing_lazy_loaded_knowledge_is_rejected(self):
        (self.knowledge / "content" / "lesson.md").unlink()
        with self.assertRaisesRegex(ValueError, "Missing referenced asset"):
            publisher.validate(self.data)

    def test_invalid_yaml_is_rejected(self):
        self.write_text(self.module / "quiz" / "vi" / "question.yml", "questions: [\n")
        with self.assertRaisesRegex(ValueError, "Malformed YAML"):
            publisher.validate(self.data)

    def test_yaml_extension_is_allowed(self):
        self.write_text(self.module / "roadmap" / "vi" / "roadmap.yaml", "roadmap: []\n")
        files, _, _, _ = publisher.validate(self.data)
        self.assertTrue(any(file.suffix == ".yaml" for file in files))

    def test_forbidden_extensions_and_extensionless_files_are_rejected(self):
        for name in ("application.jar", "Example.class", "build.gradle", "notes.txt", "LICENSE"):
            with self.subTest(name=name):
                forbidden = self.module / "knowledge" / "vi" / "content" / name
                self.write_text(forbidden, "not allowed\n")
                with self.assertRaisesRegex(ValueError, "Forbidden generated file type"):
                    publisher.validate(self.data)
                forbidden.unlink()

    def test_package_rejects_forbidden_file_before_creating_archive(self):
        self.write_text(self.module / "hidden.jar", "not allowed\n")
        output = self.root / "release"
        with self.assertRaisesRegex(ValueError, "Forbidden generated file type"):
            publisher.package(self.data, output, "a" * 40, "2026-01-01T00:00:00Z")
        self.assertFalse((output / "java-learning-data.zip").exists())

    def test_unsafe_public_asset_path_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unsafe public asset path"):
            publisher.resolve_asset(self.data, "/module/%2e%2e/secret.txt")


if __name__ == "__main__":
    unittest.main()
