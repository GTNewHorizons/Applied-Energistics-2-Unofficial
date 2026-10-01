# Repeated crafting dispatch benchmark

Run from the repository root, using an already accepted `run/server/eula.txt`:

```console
python3 scripts/benchmark-crafting.py --before c18acef6a --offline
```

Remove `--offline` if Gradle dependencies are not cached. `--before` is a commit, branch, or tag;
the after version is the current working tree. The script creates a temporary baseline worktree,
copies the same benchmark harness into it, and runs before/after/after/before in fresh JVMs.
It preserves your sources and removes the baseline worktree when finished.

By default each sample transfers 10,000,000 units through 1,250,000 processing-pattern dispatches
(two input slots of four cobblestone, producing eight stone). Each JVM discards ten full-job
warmups and measures seven jobs. The CPU has a 4,096-operation rolling budget; normal budget
exhaustion and idle CPU updates are included. Both versions use a 1 GiB Java heap and G1 GC.
Change `--operations`, `--units`, `--warmup`, `--samples`, or `--forks` as needed.

Results are saved under `build/benchmarks/crafting-<timestamp>/`: raw samples and JVM details in
JSON, Gradle/server logs, the CPU source diff, and `comparison.md` with timings, allocations,
and counts of dirty notifications and input expansions. Every sample checks that all inputs
were transferred, all outputs were queued, and cached pattern inputs remained unchanged.
Timings are descriptive; there is no flaky timing threshold in the normal test suite.

This measures the real crafting CPU dispatch loop and real chunk/neighbor dirty notifications
in an isolated loaded world. The receiving medium accepts and consumes transfers without an
interface or storage network. Results therefore exclude the other interface/network hot spots
in a Spark profile and do not predict total server speedup or craft completion time.

For a single version, without baseline comparison:

```console
./gradlew runCraftingBenchmark --offline
```

Override settings with `-Pae2.craftingBenchmark.units=10000000`, `.operations=4096`, `.warmup=10`,
`.samples=7`, or `.output=/absolute/path/result.json` (use the full prefix for each property).
This opt-in mode writes its results and stops the development server automatically.
