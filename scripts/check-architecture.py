"""Check source-layer boundaries without starting services or touching databases."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
errors = []
counts = {"entity": 0, "repository": 0, "service": 0, "controller": 0}
for module in sorted(root.glob("*-service")):
    source = module / "src/main/java"
    for path in source.rglob("*.java"):
        text = path.read_text(encoding="utf-8-sig")
        layer = path.parent.name
        if layer in counts:
            counts[layer] += int(layer != "entity" or "@Entity" in text)
        if layer in {"controller", "service"}:
            if re.search(r"\b(?:JdbcTemplate|EntityManager|ResultSet)\b", text):
                errors.append(f"Persistence detail outside repository: {path.relative_to(root)}")
            if re.search(r'"\s*(?:SELECT\s+.+\s+FROM|INSERT\s+INTO|UPDATE\s+\w+\s+SET|DELETE\s+FROM)', text, re.I):
                errors.append(f"SQL outside repository: {path.relative_to(root)}")
        if layer == "entity":
            package = re.search(r"package\s+(com\.domesticservice\.\w+)\.entity;", text)
            for foreign in re.findall(r"import\s+(com\.domesticservice\.\w+)\.entity\.", text):
                if package and foreign != package.group(1):
                    errors.append(f"Cross-service entity import: {path.relative_to(root)}")

for path in (root / "common-web/src/main/java").rglob("*.java"):
    if re.search(r"jakarta\.persistence|\.entity\.", path.read_text(encoding="utf-8")):
        errors.append(f"Persistence in common-web: {path.relative_to(root)}")

for error in errors:
    print(error)
if errors:
    raise SystemExit(1)
print("Architecture boundaries OK:", ", ".join(f"{count} {layer}" for layer, count in counts.items()))
