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

    def test_module_group_assessment_assets_are_published_and_checked(self):
        path = "/module/EXAMPLE/casestudy/vi/index.json"
        catalog_file = self.data / "module-catalog.json"
        catalog = json.loads(catalog_file.read_text(encoding="utf-8"))
        catalog["root"]["children"][0]["casestudy"] = {"vi": path}
        self.write_json(catalog_file, catalog)
        self.write_json(self.module / "casestudy" / "vi" / "index.json", {
            "mode": "groups",
            "chapters": [{"id": "group-1", "title": "First", "questionCount": 1,
                          "path": "groups/1.yml"}],
            "totalQuestions": 1,
        })
        self.write_text(self.module / "casestudy" / "vi" / "groups" / "1.yml",
                        "cases:\n  - situation: Test\n    resolve: Done\n"
                        "    knowledge: SRP\n    code: Example\n")
        files, _, refs, _ = publisher.validate(self.data)
        self.assertTrue(any(f.name == "1.yml" for f in files))
        self.assertGreaterEqual(refs, 4)
        (self.module / "casestudy" / "vi" / "index.json").unlink()
        with self.assertRaisesRegex(ValueError, "Missing referenced asset"):
            publisher.validate(self.data)
        self.write_json(self.module / "casestudy" / "vi" / "index.json", {
            "mode": "groups",
            "chapters": [{"id": "group-1", "title": "First", "questionCount": 1,
                          "path": "groups/1.yml"}],
            "totalQuestions": 1,
        })
        (self.module / "casestudy" / "vi" / "groups" / "1.yml").unlink()
        with self.assertRaisesRegex(ValueError, "Missing referenced asset"):
            publisher.validate(self.data)

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
        self.write_text(self.module / "reference" / "vi" / "references.yml",
                        "references: []\n")
        self.assertFalse(publisher.same_published_content(self.data, zip_file, checksum))

    def test_localized_reference_assets_are_checked_for_each_language(self):
        catalog_file = self.data / "module-catalog.json"
        catalog = json.loads(catalog_file.read_text(encoding="utf-8"))
        reference = {
            "vi": "/module/EXAMPLE/reference/vi/references.yml",
            "en": "/module/EXAMPLE/reference/en/references.yml",
        }
        catalog["root"]["children"][0]["reference"] = reference
        self.write_json(catalog_file, catalog)
        for language in ("vi", "en"):
            self.write_text(self.module / "reference" / language / "references.yml",
                            "references:\n  - title: Specification\n"
                            "    url: https://example.org/spec\n")

        _, _, ref_count, _ = publisher.validate(self.data)
        self.assertEqual(ref_count, 5)

        (self.module / "reference" / "en" / "references.yml").unlink()
        with self.assertRaisesRegex(ValueError, "Missing referenced asset"):
            publisher.validate(self.data)

        for invalid in (
                {"vi": "/module/EXAMPLE/reference/en/references.yml"},
                {"en": "/module/OTHER/reference/en/references.yml"},
                {"fr": "/module/EXAMPLE/reference/fr/references.yml"},
                {},
                ["/module/EXAMPLE/reference/vi/references.yml"]):
            with self.subTest(reference=invalid):
                catalog["root"]["children"][0]["reference"] = invalid
                self.write_json(catalog_file, catalog)
                with self.assertRaisesRegex(ValueError, "Invalid localized Reference paths"):
                    publisher.validate(self.data)

    def test_legacy_shared_reference_catalog_remains_valid_during_rollout(self):
        catalog_file = self.data / "module-catalog.json"
        catalog = json.loads(catalog_file.read_text(encoding="utf-8"))
        catalog["root"]["children"][0]["reference"] = (
            "/module/EXAMPLE/reference/references.yml")
        self.write_json(catalog_file, catalog)
        self.write_text(self.module / "reference" / "references.yml", "references: []\n")
        _, _, ref_count, _ = publisher.validate(self.data)
        self.assertEqual(ref_count, 4)
        catalog["root"]["children"][0]["reference"] = (
            "/module/EXAMPLE/reference/other.yml")
        self.write_json(catalog_file, catalog)
        with self.assertRaisesRegex(ValueError, "Invalid Reference path"):
            publisher.validate(self.data)

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

    def test_invalid_module_route_ids_are_rejected_before_release(self):
        catalog_file = self.data / "module-catalog.json"
        original = json.loads(catalog_file.read_text(encoding="utf-8"))
        for invalid in ("externalized-configuration", "fundamentals", "BAD/ID", "A" * 129):
            with self.subTest(route_id=invalid):
                catalog = json.loads(json.dumps(original))
                catalog["root"]["children"][0]["routeId"] = invalid
                self.write_json(catalog_file, catalog)
                with self.assertRaisesRegex(ValueError, "Invalid routeId in catalog"):
                    publisher.validate(self.data)
        self.write_json(catalog_file, original)

    def test_duplicate_module_route_ids_are_rejected(self):
        catalog_file = self.data / "module-catalog.json"
        catalog = json.loads(catalog_file.read_text(encoding="utf-8"))
        duplicate = json.loads(json.dumps(catalog["root"]["children"][0]))
        duplicate["id"] = "another-module"
        catalog["root"]["children"].append(duplicate)
        self.write_json(catalog_file, catalog)
        with self.assertRaisesRegex(ValueError, "Duplicate routeId in catalog: EXAMPLE"):
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

    def test_retired_module_wide_explorer_is_rejected(self):
        catalog_file = self.data / "module-catalog.json"
        catalog = json.loads(catalog_file.read_text(encoding="utf-8"))
        catalog["root"]["children"][0]["codeExplorer"] = (
            "/module/EXAMPLE/code-explorer/index.json")
        self.write_json(catalog_file, catalog)
        with self.assertRaisesRegex(ValueError, "Retired Code Explorer catalog field"):
            publisher.validate(self.data)
        del catalog["root"]["children"][0]["codeExplorer"]
        self.write_json(catalog_file, catalog)
        folder = self.module / "code-explorer"
        self.write_json(folder / "index.json", {"version": 1})
        with self.assertRaisesRegex(ValueError, "Retired Code Explorer asset"):
            publisher.validate(self.data)
        (folder / "index.json").unlink()

    def test_step8_case_scoped_java_is_verified_and_requires_full_manifest(self):
        import hashlib
        base = self.module / "code-explorer" / "casestudy"
        folder = base / "groups" / "1" / "cases" / "1"
        path = "demo/casestudy/controller/Group1Controller.java"
        source_file = folder / "files" / path
        self.write_text(source_file, "package demo.casestudy.controller; public class Group1Controller {}\n")
        source = source_file.read_bytes()
        self.write_json(folder / "index.json", {
            "version": 1, "type": "CODE_EXPLORER", "id": "casestudy-g1-c1",
            "files": [{"path": path, "bytes": len(source),
                       "sha256": hashlib.sha256(source).hexdigest()}],
            "symbols": "symbols.json", "references": "references.json",
        })
        self.write_json(folder / "symbols.json", {"version": 1, "symbols": []})
        self.write_json(folder / "references.json", {"version": 1, "references": []})
        manifest = {"version": 1, "type": "CASE_STUDY_EXPLORER",
                    "complete": True, "cases": [{
                        "groupID": 1, "caseIndex": 1,
                        "path": "groups/1/cases/1/index.json", "files": 1}]}
        self.write_json(base / "index.json", manifest)
        catalog_file = self.data / "module-catalog.json"
        catalog = json.loads(catalog_file.read_text(encoding="utf-8"))
        catalog["root"]["children"][0]["caseStudyExplorer"] = (
            "/module/EXAMPLE/code-explorer/casestudy/index.json")
        self.write_json(catalog_file, catalog)
        files, _, _, _ = publisher.validate(self.data)
        self.assertIn(source_file, files)
        self.assertFalse((folder / "api-map.json").exists())
        index = json.loads((folder / "index.json").read_text(encoding="utf-8"))
        index["apiMap"] = "api-map.json"
        self.write_json(folder / "index.json", index)
        with self.assertRaisesRegex(ValueError, "Invalid STEP8_EXPLORER bundle"):
            publisher.validate(self.data)
        del index["apiMap"]
        self.write_json(folder / "index.json", index)
        self.write_json(folder / "api-map.json", {"version": 1, "endpoints": []})
        with self.assertRaisesRegex(ValueError, "Retired Code Explorer asset"):
            publisher.validate(self.data)
        (folder / "api-map.json").unlink()
        manifest["complete"] = False
        self.write_json(base / "index.json", manifest)
        with self.assertRaisesRegex(ValueError, "Incomplete STEP8_EXPLORER"):
            publisher.validate(self.data)
        manifest["complete"] = True
        self.write_json(base / "index.json", manifest)
        self.write_text(source_file, "tampered")
        with self.assertRaisesRegex(ValueError, "STEP8_EXPLORER source checksum mismatch"):
            publisher.validate(self.data)

    def test_unclaimed_java_and_unrelated_java_are_rejected(self):
        extra = self.module / "code-explorer" / "files" / "Demo.java"
        self.write_text(extra, "public class Demo {}")
        with self.assertRaisesRegex(ValueError, "Forbidden generated file type"):
            publisher.validate(self.data)
        extra.unlink()
        orphan = (self.module / "code-explorer" / "casestudy" / "groups" /
                  "1" / "cases" / "1" / "files" / "Demo.java")
        self.write_text(orphan, "public class Demo {}")
        with self.assertRaisesRegex(ValueError, "Unclaimed Code Explorer Java assets"):
            publisher.validate(self.data)
        orphan.unlink()
        self.write_text(self.module / "Example.java", "public class Example {}")
        with self.assertRaisesRegex(ValueError, "Forbidden generated file type"):
            publisher.validate(self.data)

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
