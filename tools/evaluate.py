#!/usr/bin/env python3
"""Offline corpus checks and honest bookkeeping; this does not run inference."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys

ROOT = Path(__file__).resolve().parent.parent


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate():
    catalog = read(ROOT / "data/models.json")
    definitions = read(ROOT / "data/definitions.json")
    corpus = read(ROOT / "data/cases.json")
    require(catalog["project_prefix"] == "io.github.lucf15", "Wrong project prefix")
    capabilities = {c["id"]: c for c in definitions["capabilities"]}
    require(len(capabilities) == len(definitions["capabilities"]), "Duplicate capability")
    ids = set()
    for model in catalog["models"]:
        require(model["id"] not in ids, "Duplicate model ID")
        ids.add(model["id"])
        require(re.fullmatch(r"[0-9a-f]{40}", model["revision"]), "Unpinned model revision")
        a = model["artifact"]
        require(re.fullmatch(r"[0-9a-f]{64}", a["sha256"]), "Invalid artifact hash")
        require(type(a["size_bytes"]) is int and a["size_bytes"] > 0, "Invalid artifact size")
        path = PurePosixPath(a["filename"])
        require(not path.is_absolute() and ".." not in path.parts, "Unsafe artifact path")
        expected = f'https://huggingface.co/{model["repository"]}/resolve/{model["revision"]}/{a["filename"]}'
        require(a["download_url"] == expected, "Download URL is not revision pinned")
        require(set(model["capabilities_to_evaluate"]) <= capabilities.keys(), "Unknown proposed capability")
        require(not model["validated_capabilities"], "No model has yet been evaluated")
    case_ids = set()
    coverage = Counter()
    for case in corpus["cases"]:
        require(case["id"] not in case_ids, "Duplicate case ID")
        case_ids.add(case["id"])
        require(case["capability"] in capabilities, "Unknown case capability")
        definition = capabilities[case["capability"]]
        require(case["language"] in definition["languages_to_evaluate"], "Unknown language")
        require(case["variant"] in definition["variants"], "Unknown variant")
        require(bool(case["input"].strip()), "Empty input")
        require(bool(case["review"]), "Missing review guidance")
        coverage[(case["capability"], case["language"])] += 1
    expected = {(c["id"], lang) for c in capabilities.values() for lang in c["languages_to_evaluate"]}
    require(set(coverage) == expected, "Missing task/language coverage")
    require(len(set(coverage.values())) == 1, "Unbalanced task/language coverage")
    return catalog, definitions, corpus


def prepare(args):
    catalog, definitions, corpus = validate()
    candidates = {m["id"]: m for m in catalog["models"]}
    require(args.model in candidates, "Unknown model ID")
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    write(out / "cases.json", corpus)
    write(out / "capabilities.json", definitions)
    write(out / "model.json", candidates[args.model])
    write(out / "run.json", {
        "schema_version": 1,
        "created_utc": datetime.now(timezone.utc).isoformat(),
        "execution_status": "not_run",
        "requested_backend": args.backend,
        "environment": {
            "platform": "Android", "device": None, "android_version": None,
            "runtime_version": None, "app_build": None, "effective_backend": None,
            "generation_settings": None,
        },
        "input_sha256": {f: digest(out / f) for f in ["cases.json", "capabilities.json", "model.json"]},
    })
    with (out / "results.jsonl").open("x", encoding="utf-8") as stream:
        for case in corpus["cases"]:
            stream.write(json.dumps({
                "case_id": case["id"], "status": "pending", "output": None,
                "finish_reason": None, "error": None, "metrics": None, "review": None,
            }) + "\n")
    print(f"Prepared {len(corpus['cases'])} pending cases in {out}")
    print("No inference executed. No model weights downloaded.")


def report(args):
    path = args.run.resolve()
    manifest = read(path / "run.json")
    for file, expected in manifest["input_sha256"].items():
        require(file in {"cases.json", "capabilities.json", "model.json"}, "Unexpected snapshot file")
        require(digest(path / file) == expected, f"Frozen input changed: {file}")
    cases = {c["id"]: c for c in read(path / "cases.json")["cases"]}
    definitions = {c["id"]: c for c in read(path / "capabilities.json")["capabilities"]}
    rows = [json.loads(line) for line in (path / "results.jsonl").read_text().splitlines() if line.strip()]
    require(len({r["case_id"] for r in rows}) == len(rows), "Duplicate result case ID")
    require({r["case_id"] for r in rows} == cases.keys(), "Results must cover every frozen case")
    counts = Counter()
    reviews = Counter()
    critical = Counter()
    truncated = Counter()
    unknown_stop = Counter()
    for row in rows:
        case = cases[row["case_id"]]
        definition = definitions[case["capability"]]
        group = (case["capability"], case["language"])
        status = row["status"]
        require(status in {"pending", "success", "error", "cancelled"}, "Invalid status")
        counts[(group, status)] += 1
        if status == "pending":
            require(all(row.get(k) is None for k in ["output", "finish_reason", "error", "metrics", "review"]), "Pending row contains execution data")
        elif status == "success":
            require(isinstance(row["output"], str) and row["output"].strip(), "Successful row needs output")
            require(row["finish_reason"] in {"stop", "token_limit", "unknown"}, "Missing finish reason")
            if row["finish_reason"] == "token_limit":
                truncated[group] += 1
            if row["finish_reason"] == "unknown":
                unknown_stop[group] += 1
        elif status == "error":
            require(row["finish_reason"] == "error" and bool(row["error"]), "Error row needs cause")
        else:
            require(row["finish_reason"] == "cancelled", "Cancelled row needs finish reason")
        review = row.get("review")
        if review is not None:
            require(status == "success", "Only generated output can be quality reviewed")
            require(set(review["scores"]) == set(definition["rubric"]), "Incomplete rubric")
            require(all(type(v) is int and 0 <= v <= 4 for v in review["scores"].values()), "Scores must be integers 0-4")
            require(isinstance(review["critical_errors"], list), "Critical errors must be a list")
            require(set(review["critical_errors"]) <= set(definition["critical_errors"]), "Unknown critical-error label")
            require(isinstance(review.get("notes"), str) and review["notes"].strip(), "Review needs reasons")
            reviews[group] += 1
            if review["critical_errors"]:
                critical[group] += 1
    env = manifest["environment"]
    complete = env.get("platform") == "Android" and all(env.get(k) for k in ["device", "android_version", "runtime_version", "app_build", "effective_backend", "generation_settings"])
    print("capability / language | pending | generated | errors | cancelled | reviewed | critical | token-limited | unknown-stop")
    for group in sorted({(c["capability"], c["language"]) for c in cases.values()}):
        values = [counts[(group, s)] for s in ["pending", "success", "error", "cancelled"]]
        print(f"{' / '.join(group)} | " + " | ".join(map(str, values + [reviews[group], critical[group], truncated[group], unknown_stop[group]])))
    print("Environment:", "recorded" if complete else "incomplete; results are not comparable")
    print("Model approval: not determined. This report does not validate a capability.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("validate")
    prep = sub.add_parser("prepare")
    prep.add_argument("--model", required=True)
    prep.add_argument("--backend", required=True, choices=["cpu", "gpu"])
    prep.add_argument("--out", required=True, type=Path)
    rep = sub.add_parser("report")
    rep.add_argument("run", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "validate":
            models, defs, corpus = validate()
            print(f"Valid: {len(models['models'])} pinned artifacts, {len(defs['capabilities'])} proposed capabilities, {len(corpus['cases'])} balanced synthetic cases.")
        elif args.command == "prepare":
            prepare(args)
        else:
            report(args)
    except (ValueError, KeyError, TypeError, OSError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
