#!/usr/bin/env python3
"""Verify an immutable HTTPS download before emitting or installing Paper pack settings."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
from urllib.parse import urlsplit
from urllib.request import build_opener, HTTPRedirectHandler, Request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RELEASE = "distribution/augmentwild-26.3.zip"

def https(url):
    parts = urlsplit(url)
    if parts.scheme != "https" or not parts.hostname or parts.username or parts.password or parts.fragment:
        raise ValueError("Use a directly downloadable HTTPS URL without credentials or a fragment.")
    if parts.port not in (None,443) or parts.hostname.lower() == "github.com" and "/actions/" in parts.path:
        raise ValueError("Use the raw HTTPS ZIP URL, not a GitHub Actions artifact or a custom port.")
    return url

class SecureRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return super().redirect_request(req, fp, code, msg, headers, https(newurl))

def verified(path, url):
    if path.stat().st_size > 32*1024*1024:
        raise ValueError("Resource pack exceeds 32 MiB.")
    local = path.read_bytes()
    with zipfile.ZipFile(io.BytesIO(local)) as archive:
        meta = json.loads(archive.read("pack.mcmeta"))
        names = archive.namelist()
        required = {"pack.mcmeta","assets/augmentwild/items/augment/steel_heart.json",
                    "assets/augmentwild/font/skills.json","assets/augmentwild/sounds.json"}
        if (meta.get("pack",{}).get("min_format") != [97,1] or meta["pack"].get("max_format") != [97,1]
                or not required <= set(names) or len(set(names)) != len(names)
                or len(names) > 4096 or sum(entry.file_size for entry in archive.infolist()) > 64*1024*1024
                or archive.testzip()):
            raise ValueError("Invalid AugmentWild 26.3 resource pack")
    request = Request(https(url), headers={"User-Agent":"AugmentWild-pack-installer/1"})
    with build_opener(SecureRedirect).open(request,timeout=30) as reply:
        if reply.status != 200:
            raise ValueError(f"Resource pack download returned HTTP {reply.status}.")
        if reply.read(len(local)+1) != local:
            raise ValueError("The downloaded bytes differ from the local ZIP.")
    return hashlib.sha1(local).hexdigest()

def config_text(original, url, sha):
    import yaml
    before = yaml.safe_load(original) or {}
    if not isinstance(before,dict): raise ValueError("Expected a mapping at the YAML root.")
    expected = dict(before)
    art = before.get("art",{})
    if not isinstance(art,dict): raise ValueError("Expected art to be a mapping.")
    expected["art"] = {**art,"url":url,"sha1":sha}
    block = re.compile(r"^art:[ \t]*(?:#[^\n]*)?\n(?:[ \t]+[^\n]*\n|\n)*",re.M)
    sections = list(block.finditer(original))
    if len(sections)>1 or ("art" in before and not sections):
        raise ValueError("Multiple or inline art mappings require manual editing.")
    lines = "  url: "+json.dumps(url)+"\n  sha1: "+json.dumps(sha)+"\n"
    if sections:
        old = sections[0]
        kept = re.sub(r"^  (?:url|sha1):[^\n]*(?:\n|$)","",old.group(),flags=re.M)
        result = original[:old.start()] + kept + lines + original[old.end():]
    else:
        result = original.rstrip()+"\n\nart:\n"+lines
    if yaml.safe_load(result) != expected:
        raise ValueError("Cannot preserve the existing configuration.")
    return result

def apply(path,url,sha):
    if path.is_symlink() or not path.is_file(): raise ValueError("Expected an existing regular config.yml")
    raw = path.read_bytes()
    output = config_text(raw.decode().rstrip("\n")+"\n",url,sha)
    with tempfile.NamedTemporaryFile("w",encoding="utf-8",dir=path.parent,delete=False) as tmp:
        name = Path(tmp.name)
        try:
            os.fchmod(tmp.fileno(),stat.S_IMODE(path.stat().st_mode))
            tmp.write(output)
            tmp.flush()
            os.fsync(tmp.fileno())
            if path.read_bytes()!=raw: raise ValueError("Configuration changed during verification.")
            os.replace(name,path)
        finally:
            name.unlink(missing_ok=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip",type=Path,default=ROOT/RELEASE)
    parser.add_argument("--url",help="Direct HTTPS URL; defaults to the published Git object for the included ZIP")
    parser.add_argument("--apply",type=Path,help="Existing server config.yml (requires PyYAML)")
    args=parser.parse_args()
    url=args.url
    if not url:
        commit=subprocess.check_output(["git","log","-1","--format=%H","--",RELEASE],cwd=ROOT,text=True).strip()
        if not re.fullmatch("[a-f0-9]{40}",commit): parser.error("No committed pack. Specify its HTTPS URL.")
        url=f"https://raw.githubusercontent.com/anohino88/wmdrkd/{commit}/{RELEASE}"
    sha=verified(args.zip,url)
    if args.apply: apply(args.apply,url,sha)
    print("art:\n  url: "+json.dumps(url)+"\n  sha1: "+json.dumps(sha))
if __name__=="__main__": main()
