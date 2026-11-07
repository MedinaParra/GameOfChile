"""Restore the verified Git bundle used to import the existing project."""
import hashlib
import json
import os
from pathlib import Path

payload = Path("payload")
manifest = json.loads((payload / "manifest.json").read_text())
target = Path(os.environ["RUNNER_TEMP"]) / "la-portada-v16.bundle"
total = hashlib.sha256()
size = 0
with target.open("wb") as output:
    for part in manifest["parts"]:
        name = part["name"]
        if Path(name).name != name or not name.startswith("part-"):
            raise ValueError("Invalid payload filename")
        block = (payload / name).read_bytes()
        if len(block) != part["size"] or hashlib.sha256(block).hexdigest() != part["sha256"]:
            raise ValueError("Payload verification failed: " + name)
        total.update(block)
        size += len(block)
        output.write(block)
if size != manifest["size"] or total.hexdigest() != manifest["sha256"]:
    raise ValueError("Git bundle verification failed")
print("Source bundle SHA256 verified.")
