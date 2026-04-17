# VS2 Agent Notes

## Disabling broken code during a version port (1.21.1 port in progress)

When porting to a new MC version, mod-compat / mixin code that references
missing mods or removed MC APIs is commented out with a block comment so it
can be restored later:

- Wrap the entire file contents in `/*VS2 1.21.1 TODO: <reason>\n ... \n*/`
- If the file contains inner `*/` (e.g. from existing block comments), that
  would close the outer comment prematurely. Either:
  - Change the inner `/* ... */` comments to `// ...` line comments, OR
  - As a minimal-diff fix, replace inner `*/` with `* /` (harmless inside the
    outer comment, but `* /` must be fixed when the file is re-enabled)
- Also remove the class's entry from `valkyrienskies-common.mixins.json`
  (and fabric/forge variants) when the mixin is disabled
- Every such file starts with `/*VS2 1.21.1 TODO: <short reason>` so
  searching for `VS2 1.21.1 TODO` finds everything to restore
- **Do not delete code that targets removed APIs; comment it out with a
  `// VS2 1.21.1 TODO: ...` note instead.** The block needs to come back
  when the API is restored, so keep the original lines for reference.

## Running Gametests

### Setup — CRITICAL
```bash
# ALWAYS kill leftover java processes before running gametests!
# Zombie JVMs from previous runs consume 4-8GB each and cause OOMs/hangs.
pkill -9 -f "TransformerRuntime" 2>/dev/null
sleep 2
```

### Run gametests
```bash
# ALWAYS use --no-daemon to avoid zombie gradle processes
./gradlew :forge:runGameTestServer --no-daemon --console=plain > /tmp/gametest.log 2>&1 &
# Monitor with:
tail -f /tmp/gametest.log | grep -E "VS2|GAME TEST|FAIL|Ships|Chunks"
```

Or blocking (simpler but can't Ctrl+C cleanly):
```bash
./gradlew :forge:runGameTestServer --no-daemon --console=plain 2>&1 | tee /tmp/gametest.log
```

### Common pitfalls
- **NEVER use piped grep with `head -N`** — when head exits after N lines, the pipe closes, and the gradle process becomes a zombie still running in the background eating memory
- **ALWAYS check `ps aux | grep TransformerRuntime`** before running — if any are alive, kill them first
- **ALWAYS manually kill Java processes after running benchmarks/gametests** — the gametest server JVM often lingers after Gradle exits, consuming 4-8GB of RAM. Run `pkill -9 -f "TransformerRuntime"` (or on Windows: `taskkill /F /IM java.exe`) after each run
- **Use `> file 2>&1 &`** for background runs, not `| tee file | grep`
- The gametest server has `-Xmx8G` configured in `forge/build.gradle`
- Tests take ~3-9 minutes depending on the machine
- Server startup alone takes ~2 minutes (mixin loading, Create recipes, etc.)

### Test files
- Gametest sources: `forge/src/main/java/.../forge/gametest/` (Forge loads these, not the gametest sourceset)
- Gametest sourceset copies: `forge/src/gametest/java/.../forge/gametest/` (keep in sync with main!)
- Template structure: `forge/src/main/resources/data/valkyrienskies/structures/empty_platform.nbt`
- SNBT template for GameTestServer: `forge/run/gameteststructures/empty_platform.snbt`
- **IMPORTANT**: Forge's `@GameTestHolder` only discovers classes from `src/main`, NOT `src/gametest`.
  Always put test classes in BOTH source sets or only in `src/main`.
- `ShipGameTests` (12 tests) — all ship tests in one class:
  - 5 original assembly tests
  - 2 chunk loading tests (Fix 1+2)
  - 2 collision/grace period tests (Fix 3)
  - 1 save performance test (Fix 4)
  - 2 lighting tests (Fix 5)
- `ShipBenchmarkTests` (3 benchmarks):
  - `benchmarkSpawnShipsAndTick` — creates ships, measures spawn + tick performance
  - `benchmarkShipChunkLoading` — simulates loading ships from a world save
  - `benchmarkBaseline100Ticks` — measures ticks with zero ships (baseline)

### Checking results
```bash
grep -E "(VS2 |Ships |Ship chunks|Chunks loaded|Ticks to|Wall time|Avg per|Effective|GAME TEST|required)" /tmp/gametest.log
```

## Performance Findings

### Ship Chunk Loading (the main bottleneck)
When loading a world with many ships, MC's chunk loading pipeline is the primary bottleneck:

1. **Ticket level matters hugely** — Vanilla's `updateChunkForced` uses level 31 (entity ticking) which loads ~25 neighbor chunks per ship chunk. Using a custom ticket at level 32 (ticking) reduces this to ~9 neighbors. See `VSTicketType.kt` and `ChunkManagement.kt`.

2. **Empty section terrain updates** — When a ship chunk loads, VS2 sends terrain updates to vs-core for all 24 vertical sections. Empty sections still need `newEmptyVoxelShapeUpdate` so vs-core knows they're air (not unloaded). These are cheap but necessary.

3. **postTick chunk holder iteration** — `MixinServerLevel.postTick` scans all chunk holders every tick. The fast path (`vs$pendingForcedChunks`) handles ship chunks via O(1) lookups. The slow path still iterates for world terrain chunks.

4. **`toDenseVoxelUpdate`** — Iterates 4096 blocks per non-empty section to convert to vs-core format. Called via `BlockStateInfo.cache.get()` which does `Block.getId()` + hashmap lookup. This is the main per-section cost.

### Results (100 single-block ships, same machine)

| Metric | Before optimization | After optimization |
|--------|--------------------|--------------------|
| Spawn 100 ships | ~38s | ~31s |
| Avg tick (100 ships) | ~40ms | ~33ms |

### Client-side ship spawn freeze (INVESTIGATED)
When a ship is created, the client freezes for a noticeable fraction of a second. Root causes:

1. **`SeamlessChunksManager.dispatchQueuedPackets()`** (`SeamlessChunksManager.kt:89-97`) — When a ship finishes loading, ALL deferred chunk update packets are replayed synchronously on the render thread. Each `handleLevelChunkWithLight()` triggers `toDenseVoxelUpdate()` for all 16 sections.

2. **`toDenseVoxelUpdate()` on client** (`MixinClientChunkCache.java:108-109`) — For each loaded chunk, iterates 16 sections × 4096 blocks = 65,536 `BlockStateInfo.cache.get()` lookups, all synchronous on the render thread.

3. **`forceUpdateConnectivityChunk()` per section** (`MixinClientChunkCache.java:120-128`) — When `shouldForce=true` (first load), calls vs-core physics update for every section (16 per chunk).

4. **`ClientConnectivityUpdateQueue.onRegistriesCompleted()`** (`ClientConnectivityUpdateQueue.kt:23-64`) — Processes all deferred chunks in a tight loop until empty, calling `toDenseVoxelUpdate()` + `forceUpdateConnectivityChunk()` for each.

**Fix ideas:**
- Throttle `dispatchQueuedPackets()` to process max N packets per frame
- Move `toDenseVoxelUpdate()` to a worker thread (it's read-only)
- Batch connectivity updates instead of per-section calls

### Ship chunk loading benchmark results
When loading 100 ships (simulated unload/reload):
- **500 ship chunks** loaded in **1 tick** taking **6,749ms** (6.7 second freeze!)
- Each single-block ship has ~5 active chunks (not 1 — investigate why)
- All chunks processed in one `postTick()` call with no throttling
- **Fix needed:** Throttle `vs$loadChunk` calls per tick to avoid server freeze

## HTTP Proxy for Web Claude / CI Environments

When running in Web Claude or similar sandboxed environments, Gradle cannot download
dependencies because Java's HTTP client doesn't correctly pass the proxy auth credentials
from environment variables. **You MUST set up a local proxy to build or run gametests.**

### Step-by-step setup

```bash
# 1. Create and start the local proxy (proxy.py should already exist at /home/user/proxy.py)
#    If not, write a Python HTTP proxy that:
#    - Reads PROXY_USER/PROXY_PASS from $HTTP_PROXY env var
#    - Listens on 127.0.0.1:18080 with NO authentication
#    - Forwards all HTTP/HTTPS (CONNECT tunnel) to the upstream proxy with auth
#    - Uses ThreadingMixIn for concurrent Gradle downloads
#    - Sets 120s timeouts on all sockets
python3 /home/user/proxy.py 18080 &

# 2. Configure Gradle to use the local proxy:
cat > ~/.gradle/gradle.properties << 'EOF'
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=18080
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=18080
systemProp.http.nonProxyHosts=localhost|127.0.0.1
org.gradle.jvmargs=-Xmx4G -XX:MaxMetaspaceSize=1G
EOF

# 3. CRITICAL: Clear JAVA_TOOL_OPTIONS before every Gradle command!
#    The env var has broken proxy auth that overrides ~/.gradle/gradle.properties
JAVA_TOOL_OPTIONS="" ./gradlew :forge:runGameTestServer --no-daemon
```

### Build commands cheat sheet

```bash
# Always start with this:
pgrep -f "proxy.py" > /dev/null 2>&1 || { python3 /home/user/proxy.py 18080 & sleep 1; }

# Compile everything:
JAVA_TOOL_OPTIONS="" ./gradlew :common:compileKotlin :common:compileJava :forge:compileJava :forge:gametestClasses --no-daemon

# Run gametests (ALWAYS kill old java processes first!):
pkill -f "TransformerRuntime" 2>/dev/null; sleep 2
rm -rf forge/run/world  # Clean world data between runs
JAVA_TOOL_OPTIONS="" ./gradlew :forge:runGameTestServer --no-daemon 2>&1 | tee /tmp/gametest.log

# Check results:
grep -E "COMPLETE|passed|failed" /tmp/gametest.log | tail -5
```

### Dependency workarounds

- **jitpack.io timeout**: Comment out `ImmersivePortalsMod` deps in `common/build.gradle`
  and `fabric/build.gradle`. Also exclude from `valkyrienskies-common.mixins.json` and
  add `exclude '**/immersive_portals/**'` to `tasks.withType(JavaCompile)` in `common/build.gradle`.
- **Large JAR download failures**: Pre-download via `curl` and place in Gradle cache:
  ```bash
  curl -L -o /tmp/big-dep.jar "https://repo.maven.apache.org/maven2/path/to/dep.jar"
  sha1=$(sha1sum /tmp/big-dep.jar | cut -d' ' -f1)
  mkdir -p ~/.gradle/caches/modules-2/files-2.1/group/artifact/version/$sha1/
  cp /tmp/big-dep.jar ~/.gradle/caches/modules-2/files-2.1/group/artifact/version/$sha1/
  ```
- **Stale Loom lock**: `rm -f ~/.gradle/caches/fabric-loom/.*.lock`
- **Network failures**: Retry with exponential backoff. The proxy handles most issues
  but jitpack.io and large downloads may need manual intervention.

### Gametest-specific notes

- **Test source location**: Forge discovers `@GameTestHolder` classes from `src/main/java`,
  NOT `src/gametest/java`. Always put test classes in BOTH source sets (or just `src/main`).
- **Template structure**: `forge/run/gameteststructures/empty_platform.snbt` must exist.
- **OOM prevention**: Keep benchmark ship counts ≤20 in gametests. The gametest server runs
  all tests concurrently, sharing 8GB.
- **Zombie processes**: ALWAYS kill `TransformerRuntime` processes before running.
  Multiple JVMs at 8GB each = instant OOM.
- **Checking specific test results**:
  ```bash
  grep "LogTestReporter.*failed" /tmp/gametest.log  # Failed tests
  grep "VS2GameTests\|VS2Benchmark\|Sandwich Factory" /tmp/gametest.log  # VS2 output
  grep "VS2 PERF" /tmp/gametest.log  # Performance profiling
  ```

## Architecture Notes

### Ship chunk lifecycle
1. `createNewShipAtBlock()` in vs-core allocates a `ChunkClaim` (256x256 chunk region in shipyard)
2. `moveBlocksFromTo()` uses `StructureTemplate` to copy blocks to ship's chunk
3. vs-core sends `VsiChunkWatchTask` for each active ship chunk
4. `ChunkManagement.tickChunkLoading()` adds tickets to force-load those chunks
5. `MixinServerLevel.postTick()` detects loaded chunks and sends terrain updates to vs-core
6. vs-core uses terrain data for physics collision

### Key files
- `common/.../world/ChunkManagement.kt` — chunk watch/unwatch task execution
- `common/.../world/VSTicketType.kt` — custom ticket type for ship chunks
- `common/.../mixin/server/world/MixinServerLevel.java` — chunk loading detection + terrain updates
- `common/.../assembly/ShipAssembler.kt` — ship creation/assembly
- `common/.../VSGameUtils.kt` — `toDenseVoxelUpdate`, `isChunkLoadedForVS`
- `common/.../BlockStateInfoProvider.kt` — block state mass/type cache
