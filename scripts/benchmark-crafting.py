#!/usr/bin/env python3
"""Compare a Git revision with the working tree without replacing working-tree sources."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import platform
import re
import shutil
import statistics
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
CPU = Path("src/main/java/appeng/me/cluster/implementations/CraftingCPUCluster.java")
HARNESS = (
    Path("addon.gradle"),
    Path("src/functionalTest/java/appeng/test/AppengTestMod.java"),
    Path("src/functionalTest/java/appeng/test/benchmark/CraftingCPUClusterBenchmark.java"),
)


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def jvm_options(run):
    return [option for option in run["jvmArguments"] if option.startswith("-X")]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", default="HEAD", help="baseline revision (default: HEAD)")
    parser.add_argument("--units", type=int, default=10_000_000)
    parser.add_argument("--operations", type=int, default=4096, help="CPU's rolling operation budget")
    parser.add_argument("--warmup", type=int, default=10, help="full jobs discarded per JVM")
    parser.add_argument("--samples", type=int, default=7, help="measured full jobs per JVM")
    parser.add_argument("--forks", type=int, default=2, help="fresh JVMs per version; order alternates")
    parser.add_argument("--offline", action="store_true", help="use cached Gradle dependencies")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.units <= 0 or args.units % 8 or args.units // 8 > 2**31 - 1:
        parser.error("--units must be a positive multiple of 8, with at most 2^31-1 crafts")
    if min(args.operations, args.warmup, args.samples, args.forks) <= 0 or args.operations > 2**31 - 1:
        parser.error("operations, warmup, samples and forks must be positive; operations must fit an int")
    eula = ROOT / "run/server/eula.txt"
    if not eula.is_file() or not re.search(r"^eula\s*=\s*true\s*$", eula.read_text(), re.MULTILINE | re.IGNORECASE):
        parser.error("run/server/eula.txt must contain your existing Minecraft EULA acceptance (eula=true)")
    revision = git("rev-parse", "--verify", args.before + "^{commit}")
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output = (args.output or ROOT / "build/benchmarks" / ("crafting-" + timestamp)).resolve()
    output.mkdir(parents=True, exist_ok=False)
    source_hash = hashlib.sha256((ROOT / CPU).read_bytes()).hexdigest()
    (output / "working-tree.patch").write_text(
        subprocess.check_output(["git", "diff", revision, "--", str(CPU)], cwd=ROOT, text=True)
    )
    runs = {"before": [], "after": []}
    with tempfile.TemporaryDirectory(prefix="ae2-crafting-benchmark-") as temporary:
        temporary = Path(temporary)
        baseline = temporary / "baseline"
        subprocess.run(["git", "worktree", "add", "--detach", str(baseline), revision], cwd=ROOT, check=True)
        try:
            # Both builds run the identical harness; the baseline retains its original production sources.
            for relative in HARNESS:
                destination = baseline / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / relative, destination)
            for fork in range(1, args.forks + 1):
                for label in (("before", "after") if fork % 2 else ("after", "before")):
                    directory = baseline if label == "before" else ROOT
                    name = f"{label}-{fork}"
                    server = temporary / (name + "-server")
                    server.mkdir()
                    shutil.copy2(eula, server / "eula.txt")
                    (server / "server.properties").write_text(
                        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n"
                        "level-type=FLAT\nlevel-seed=256\ngenerate-structures=false\n"
                        "spawn-animals=false\nspawn-monsters=false\nallow-nether=false\n"
                    )
                    result_path = output / (name + ".json")
                    command = [
                        "./gradlew", "runServer", "--console=plain",
                        "-Pae2.craftingBenchmark=true",
                        f"-PrunServerWorkingDirectory={server}",
                        f"-Pae2.craftingBenchmark.output={result_path}",
                    ]
                    for key in ("units", "operations", "warmup", "samples"):
                        command.append(f"-Pae2.craftingBenchmark.{key}={getattr(args, key)}")
                    if args.offline:
                        command.append("--offline")
                    print(f"Running {name}: {args.units:,} units, {args.warmup} warmups, {args.samples} samples", flush=True)
                    with (output / (name + ".log")).open("w") as log:
                        completed = subprocess.run(command, cwd=directory, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
                    if completed.returncode or not result_path.is_file():
                        raise RuntimeError(f"{name} failed; see {output / (name + '.log')}")
                    result = json.loads(result_path.read_text())
                    if len(result["samples"]) != args.samples or any(
                        sample["dispatches"] != args.units // 8
                        or sample["consumedUnits"] != args.units
                        or sample["waitingUnits"] != args.units
                        for sample in result["samples"]
                    ):
                        raise RuntimeError(f"{name} did not complete the requested workload")
                    runs[label].append(result)
                    median_ms = statistics.median(sample["nanos"] for sample in result["samples"]) / 1_000_000
                    print(f"{name}: median {median_ms:.3f} ms", flush=True)
        finally:
            subprocess.run(["git", "worktree", "remove", "--force", str(baseline)], cwd=ROOT, check=True)

    if hashlib.sha256((ROOT / CPU).read_bytes()).hexdigest() != source_hash:
        raise RuntimeError("Working-tree CPU source changed during the comparison; rerun with a stable source")
    reference = runs["before"][0]
    for result in runs["before"] + runs["after"]:
        for key in ("units", "crafts", "operations", "warmup", "javaVersion", "javaVm"):
            if result[key] != reference[key]:
                raise RuntimeError(f"Mismatched benchmark environment: {key}")
        if jvm_options(result) != jvm_options(reference):
            raise RuntimeError("Mismatched JVM options")

    def median(label, field):
        # Give each JVM equal weight and retain every raw observation in the JSON files.
        return statistics.median(statistics.median(s[field] for s in run["samples"]) for run in runs[label])

    before_ns, after_ns = median("before", "nanos"), median("after", "nanos")
    lines = [
        "# Crafting CPU benchmark", "",
        f"Before: `{revision}`. After: working tree, CPU SHA-256 `{source_hash}`.", "",
        f"{args.units:,} output units / 8 = {args.units // 8:,} dispatches per sample. "
        f"Operation budget: {args.operations:,}. {args.forks} fresh JVMs per version, "
        f"{args.warmup} warmup jobs and {args.samples} measured jobs per JVM; alternating run order.", "",
        f"Java: {reference['javaVm']} {reference['javaVersion']}. "
        f"JVM options: `{' '.join(jvm_options(reference))}`. Machine: {platform.platform()}.", "",
        "Values below are the median of the individual JVM medians.", "",
        "| Metric | Before | After |", "| --- | ---: | ---: |",
        f"| Time per {args.units:,}-unit job (ms) | {before_ns / 1_000_000:.3f} | {after_ns / 1_000_000:.3f} |",
        f"| ns per dispatch | {before_ns / reference['crafts']:.1f} | {after_ns / reference['crafts']:.1f} |",
    ]
    for field, title in (("allocatedBytes", "Allocated bytes per dispatch"), ("dirtyNotifications", "Dirty notifications"),
                         ("condensedExpansions", "Condensed input expansions"), ("slotExpansions", "Slot input expansions"), ("ticks", "CPU updates")):
        divisor = reference["crafts"] if field == "allocatedBytes" else 1
        values = [median(label, field) / divisor for label in ("before", "after")]
        if field == "allocatedBytes" and min(values) < 0:
            lines.append(f"| {title} | unavailable | unavailable |")
        else:
            precision = 1 if field == "allocatedBytes" else 0
            lines.append(f"| {title} | {values[0]:,.{precision}f} | {values[1]:,.{precision}f} |")
    lines += ["", f"Dispatch loop elapsed time changed by {(after_ns / before_ns - 1) * 100:+.1f}% ({before_ns / after_ns:.2f}x speedup).", "",
              "Individual JVM medians (ms):", ""]
    for label in ("before", "after"):
        medians = [statistics.median(s["nanos"] for s in run["samples"]) / 1_000_000 for run in runs[label]]
        observations = [s["nanos"] / 1_000_000 for run in runs[label] for s in run["samples"]]
        lines.append(f"- {label}: " + ", ".join(f"{value:.3f}" for value in medians)
                     + f"; all measured samples: {min(observations):.3f}–{max(observations):.3f} ms")
    lines += ["", "This isolates the real CPU update, input expansion, extraction, transfer inventory, output bookkeeping, "
              "and TileEntity chunk-dirty/neighbor notifications in a dummy loaded world with stone neighbors. "
              "Energy uses the real grid cache with infinite power. An accepting medium consumes each transfer. "
              "Receiving-interface insertion, grid-wide events, storage, network size, and normal server tick scheduling "
              "are excluded. This is dispatch CPU cost, not wall-clock craft completion time or an end-to-end server speedup.", ""]
    report = output / "comparison.md"
    report.write_text("\n".join(lines))
    print(report.read_text(), flush=True)
    print(f"Report and raw results: {output}")


if __name__ == "__main__":
    main()
