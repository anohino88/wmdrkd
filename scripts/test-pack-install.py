#!/usr/bin/env python3
import io
import json
import runpy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

install=runpy.run_path(str(Path(__file__).with_name("install-pack.py")))
class PackInstallTest(unittest.TestCase):
    def test_reject_unsafe_url(self):
        for value in ["http://example.org/p.zip","https://person:pass@example.org/p.zip","https:///p.zip"]:
            with self.subTest(value=value),self.assertRaises(ValueError): install["https"](value)

    def test_wrong_download(self):
        fn=install["verified"]
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/"p.zip"
            with zipfile.ZipFile(path,"w") as pack:
                pack.writestr("pack.mcmeta",json.dumps({"pack":{"min_format":[97,1]}}))
                pack.writestr("assets/augmentwild/items/augment/steel_heart.json","{}")
            with patch.dict(fn.__globals__,build_opener=lambda *_:self):
                self.payload=b"error document"
                with self.assertRaises(ValueError): fn(path,"https://example.org/p.zip")
                self.payload=path.read_bytes()
                self.assertEqual(len(fn(path,"https://example.org/p.zip")),40)

    def open(self,*args,**kwargs): return io.BytesIO(self.payload)

    def test_atomic_edit(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/"config.yml"
            path.write_text("# server\ngame:\n  min: 2\nart:\n  keep: true\n  url: old\n  sha1: old\n")
            install["apply"](path,"https://example.org/game.zip","a"*40)
            self.assertIn("# server\ngame:\n  min: 2",path.read_text())
            self.assertIn("  keep: true",path.read_text())
            previous=path.read_bytes()
            install["apply"](path,"https://example.org/game.zip","a"*40)
            self.assertEqual(previous,path.read_bytes())

    def test_reject_ambiguous_section(self):
        with self.assertRaises(ValueError):
            install["config_text"]("art: {}\n","https://example.org/p.zip","a"*40)
if __name__=="__main__": unittest.main()
