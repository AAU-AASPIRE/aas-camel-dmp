"""Copy a run's figure, its data and what it was made from into paper/<experiment>/, the versioned record of the
paper's figures. Raw samples and logs stay in results/.

    uv run python -m analysis.publish results/<experiment>/<run>
    uv run python -m analysis.figures paper/<experiment>     # redraws the figure from that record
"""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

EVALUATION = Path(__file__).resolve().parent.parent


def publish(run: Path) -> Path:
    manifest = json.loads((run / "manifest.json").read_text())
    target = EVALUATION / "paper" / manifest["experiment"]
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)
    # The host's paths are no part of the record.
    manifest["command"] = ["python", "-m", "harness.run", *manifest["command"][1:]]
    manifest["run"] = run.name
    (target / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    for path in run.iterdir():
        if path.name == "summary.json" or path.suffix in (".pdf", ".png", ".csv"):
            shutil.copy2(path, target / path.name)
    return target


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    print(publish(Path(sys.argv[1]).resolve()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
