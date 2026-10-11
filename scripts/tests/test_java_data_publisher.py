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
        self.assertEqual(len(names), len(files) + 1)

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

    def test_unsafe_public_asset_path_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unsafe public asset path"):
            publisher.resolve_asset(self.data, "/module/%2e%2e/secret.txt")


if __name__ == "__main__":
    unittest.main()
