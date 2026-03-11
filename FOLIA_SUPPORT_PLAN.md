# Advanced Folia Support Plan (Target: Folia 1.21.11)

## 1) Objectives and Compatibility Contract

### Primary goals
- Run safely on Folia 1.21.11 without cross-thread world/entity access violations.
- Preserve current Paper behavior (single-threaded compatibility mode).
- Keep protocol and feature parity for core Axiom flows: block editing, chunk/entity data requests, world properties, annotations, and integrations.

### Compatibility targets
- **Primary runtime:** Folia `1.21.11`.
- **Secondary runtime:** Paper `1.21.x` (same artifact if possible).
- **NMS strategy:** keep current NMS coupling but isolate scheduler/thread-affinity concerns behind a small abstraction layer.

### Non-goals (phase 1)
- Full performance tuning for large multi-region edits before correctness.
- Removing all NMS usage.

---

## 2) Current-State Audit (Folia Risk Mapping)

Use this as a mandatory pre-implementation checklist.

### Thread-affinity hotspots to inventory
1. Global tick pipeline:
   - `AxiomPaper#onEnable` schedules `tick()` via Bukkit sync scheduler.
2. Central operation execution:
   - `OperationQueue` currently assumes/optimizes for server-main-thread semantics.
3. Packet listener handlers that mutate world/entity state:
   - `SetBlockPacketListener`, `SetBlockBufferPacketListener`, `SpawnEntityPacketListener`, `DeleteEntityPacketListener`, `ManipulateEntityPacketListener`, `SetTimePacketListener`, `TeleportPacketListener`, etc.
4. APIs and registries with direct world/entity access:
   - World property registry, custom blocks/displays APIs, annotations, extension caches.
5. Shared mutable structures currently using non-concurrent collections in plugin singleton (player permissions, plot bounds, no-physical-trigger sets).

### Deliverable
- A markdown audit table (`component`, `thread owner`, `current callsite`, `required Folia scheduler`, `status`) committed before feature implementation begins.

---

## 3) Architecture Changes

## 3.1 Introduce a scheduler/affinity abstraction
Create a small internal service (example: `ThreadingBridge`) with implementations:
- `PaperThreadingBridge`
- `FoliaThreadingBridge`

Capabilities to expose:
- `runGlobal(Runnable)` for plugin-global tasks.
- `runForWorldChunk(World, int chunkX, int chunkZ, Runnable)` for region-owned world mutation.
- `runForEntity(Entity, Runnable)` for entity-affine operations.
- `runForPlayer(Player, Runnable)` for player-affine operations.
- `executeNextTick*` variants where ordering matters.

All packet listeners and operation executors should route stateful operations through this bridge.

## 3.2 Replace global tick assumptions
- Refactor `OperationQueue` from one global per-tick loop into a scheduler-driven dispatcher:
  - queue operations by **world + region key** (or by chunk key if easier).
  - each queue is drained only by its owning region scheduler.
  - preserve FIFO per region key; avoid global FIFO guarantees across regions.

## 3.3 Context-aware operation model
Extend `PendingOperation` metadata so each operation declares scheduling intent:
- `REGION_MUTATION(chunk range)`
- `ENTITY_MUTATION(entity uuid)`
- `READ_ONLY_REGION`
- `GLOBAL_NON_WORLD`

This makes queue routing explicit and testable.

---

## 4) Implementation Phases

## Phase A — Foundation (no behavior change)
1. Add runtime detection (`isFolia`) and wire `ThreadingBridge` selection.
2. Add tracing logs (debug mode) for scheduler decisions.
3. Convert all direct scheduler entrypoints in plugin bootstrap to bridge calls.

**Exit criteria:** plugin boots on Paper unchanged; no feature regressions.

## Phase B — Safety wrappers around packet handlers
1. Wrap each packet listener action with the correct affinity executor.
2. Ensure any Bukkit/Paper API call touching world/entity/player runs in proper context.
3. Add guard utility for development builds that asserts expected thread/region ownership.

**Exit criteria:** no Folia illegal-access exceptions in smoke tests.

## Phase C — OperationQueue regionalization
1. Replace `Map<ServerLevel, List<PendingOperation>>` with region-scoped queues.
2. Partition large edits (`SetBlockBufferOperation`, chunk requests) into chunk/region batches.
3. Add back-pressure controls per region queue (limits already in config can be reused/extended).

**Exit criteria:** large edits complete without stalling unrelated regions.

## Phase D — Integration hardening
1. Audit third-party hooks (CoreProtect, PlotSquared, WorldGuard, LuckPerms callbacks).
2. If external APIs are main-thread-only, bridge execution safely (global/region handoff).
3. Add fallback behavior and user-facing warnings when an integration cannot run safely under Folia context.

**Exit criteria:** integrations operate or degrade gracefully with explicit logs.

## Phase E — Release preparation
1. Add docs for supported server types and known caveats.
2. Add config switches for conservative mode (`strict-folia-routing`, `max-region-jobs`).
3. Prepare changelog + migration notes.

**Exit criteria:** release candidate validated on target matrix.

---

## 5) Data Consistency and Concurrency Plan

- Replace mutable `HashMap`/`HashSet` plugin state touched from multiple contexts with:
  - `ConcurrentHashMap`/`ConcurrentHashMap.newKeySet()` where lock-free access is enough.
  - explicit locks for compound mutations.
- Minimize shared mutable state by moving operation-local data into immutable snapshots.
- Ensure cross-context communication happens by message passing (queueing), not direct object mutation.

---

## 6) Testing Strategy (Targeting 1.21.11)

## 6.1 Automated checks
- Unit tests for queue routing and scheduling intent resolution.
- Integration tests (MockBukkit or harness) for packet->scheduler dispatch behavior.
- Static scan/checkstyle rule (or simple test) banning direct world mutation from listener codepaths unless via bridge.

## 6.2 Manual matrix
1. Paper 1.21.x single-player edit flows.
2. Folia 1.21.11 single player, same-region edits.
3. Folia 1.21.11 multi-player, different-region simultaneous edits.
4. Stress: large buffer paste while players move between regions.
5. Integration scenarios (CoreProtect/PlotSquared/WorldGuard installed).

## 6.3 Observability
- Add counters/timers:
  - queued ops per region
  - scheduler handoff latency
  - failed/aborted ops by reason
- Include temporary debug command output for live queue diagnostics.

---

## 7) Risk Register and Mitigations

1. **Risk:** hidden thread violations in less-used packet handlers.
   - **Mitigation:** mandatory routing through bridge + assert utility in dev mode.
2. **Risk:** operation ordering differences after regionalization.
   - **Mitigation:** document and enforce FIFO only within region; add tests.
3. **Risk:** third-party plugin APIs not Folia-safe.
   - **Mitigation:** integration adapter layer + fail-open/skip behavior with logs.
4. **Risk:** performance regressions from over-serialization.
   - **Mitigation:** batch by chunk, tune queue limits, instrument latency.

---

## 8) Proposed Milestone Timeline

- **Week 1:** Audit + `ThreadingBridge` scaffold + Paper parity checks.
- **Week 2:** Packet handler routing + guard utilities.
- **Week 3:** OperationQueue regionalization + stress testing.
- **Week 4:** Integration hardening + docs + RC build for Folia 1.21.11.

---

## 9) Definition of Done

Folia support is complete when all of the following are true:
- Plugin runs on Folia 1.21.11 through core editing workflows with no thread-access exceptions.
- No critical feature regression on Paper 1.21.x.
- Large block-buffer operations are region-safe and do not globally block unrelated activity.
- Integrations are either working safely or explicitly disabled with clear operator logs.
- Documentation clearly states support level, caveats, and tuning knobs.
