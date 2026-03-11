# Axiom Paper Plugin (with Folia Support)

Server-side bridge for the **Axiom** Minecraft editor on Paper and Folia.

This plugin enables Axiom clients to safely perform high-performance editing actions on multiplayer servers while respecting permissions, integration hooks, and server-side restrictions.

---

## Table of Contents

- [What this plugin does](#what-this-plugin-does)
- [Folia support overview](#folia-support-overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [First-run checklist](#first-run-checklist)
- [Permissions model](#permissions-model)
- [Commands](#commands)
- [Configuration reference (operator-focused)](#configuration-reference-operator-focused)
- [Integrations](#integrations)
- [Troubleshooting](#troubleshooting)
- [Developer notes](#developer-notes)
- [Security & hardening recommendations](#security--hardening-recommendations)
- [FAQ](#faq)

---

## What this plugin does

Axiom Paper Plugin is the server component that:

- Handles Axiom client handshake and compatibility checks.
- Receives and validates editor operations from clients.
- Applies world/entity/player modifications server-side.
- Exposes permissions and restrictions so you can control which tools/features players may use.
- Adds support for custom display/block/entity interoperability via plugin channels.
- Integrates with common ecosystem plugins like LuckPerms, CoreProtect, WorldGuard, PlotSquared, and ViaVersion.

If the plugin is not installed on the server, most multiplayer Axiom editing functionality will not work.

---

## Folia support overview

This project includes explicit Folia compatibility and runtime scheduling abstraction.

### How Folia is detected

At startup, the plugin checks for Folia classes and determines whether it is running in Folia mode.

### Scheduler abstraction used by the plugin

The plugin uses a `ThreadingBridge` abstraction with four execution modes:

- Global tasks
- World/chunk-scoped tasks
- Entity-scoped tasks
- Player-scoped tasks

#### On Paper
All four execution paths route through Bukkit's classic scheduler.

#### On Folia
Each execution path routes to the correct Folia scheduler:

- Global → `GlobalRegionScheduler`
- Chunk/world work → `RegionScheduler`
- Entity work → entity scheduler
- Player work → player scheduler

### Tick loop behavior

The periodic plugin tick uses:

- `GlobalRegionScheduler.runAtFixedRate(...)` on Folia
- `scheduleSyncRepeatingTask(...)` on non-Folia Paper

This allows the same plugin binary to run on both modern Folia and regular Paper without requiring separate builds.

---

## Requirements

- **Minecraft / Paper API target:** 1.21.x
- **Java:** 21
- **Server software:** Paper or Folia-compatible Paper API environment

Optional integrations (soft dependencies):

- CoreProtect
- ViaVersion
- WorldGuard
- PlotSquared
- LuckPerms (listener-based permission cache behavior)

---

## Installation

1. Download the latest release from Modrinth:  
   https://modrinth.com/plugin/axiom-paper-plugin/
2. Stop your server.
3. Place the plugin `.jar` into your `plugins/` directory.
4. Start the server once to generate default config files.
5. Configure `plugins/Axiom-Paper/config.yml` to your policy.
6. Restart the server.

---

## First-run checklist

For each Axiom user:

1. Ensure they have permission (recommended: `axiom.default`, or `axiom.all` for trusted staff).
2. Have them disconnect and reconnect after permission changes.
3. If editing still fails, run debugging checks with `/axiompaperdebug ...`.

If users report mismatch warnings, review:

- `incompatible-data-version`
- `unsupported-axiom-version`

---

## Permissions model

### High-level groups

- `axiom.all` – full access (op-like override)
- `axiom.default` – recommended baseline for public editing servers
- `axiom.entity.*` – entity operations (spawn/manipulate/delete/request)
- `axiom.blueprint.*` – server blueprint upload/request/manifest
- `axiom.annotation.*` – annotation create/clear operations
- `axiom.debug` – allows `/axiompaperdebug`

### Important default children

`axiom.default` includes broad editor/tool/build/world/player capability trees such as:

- chunk data requests (`axiom.chunk.*`)
- block placement and advanced placement modes (`axiom.build.*`)
- editor UI and views (`axiom.editor.*`)
- player controls (teleport/speed/gamemode groups)
- world controls (`axiom.world.*`)
- capabilities (`axiom.capability.*`)
- tools (`axiom.tool.*`, `axiom.builder_tool.*`)

### Admin bypass

- `axiomadmin.bypass_region_checks` bypasses region checks from integrations.

### Rank-based operation rate overrides

`config.yml` supports per-rank operation throughput using `limits.<name>.block-buffer-rate-limit` and granting players `axiomlimits.<name>`.

---

## Commands

### `/axiompaperdebug <subcommand...>`

Diagnostics for Axiom state (permission-gated). Useful examples:

- `canUseAxiom`
- `isMismatchedDataVersion`
- `canModifyWorld`
- `isClientListening <channel>`
- `hasPermission <node>`
- `getRestrictions`
- integration checks for PlotSquared/WorldGuard context

Access: player must be OP, `axiom.all`, `axiom.debug`, or a specific internal maintainer UUID.

### `/axiompapermigrateconfig`

Migrates/refreshes `config.yml` keys based on defaults while preserving existing matching values and creating `config.yml.bak` backup.

Access: OP players or console.

---

## Configuration reference (operator-focused)

Below are the settings most relevant to production behavior.

### Performance and throughput

- `max-chunk-sends-per-tick` – cap outgoing chunk sends per world (`0` = unlimited).
- `max-chunk-relights-per-tick` – cap relights per world (`0` = unlimited).
- `block-buffer-rate-limit` – section throughput budget per second (`0` = default internal limit).
- `limits.<name>.block-buffer-rate-limit` – per-permission override.

### Editing scope & safety

- `max-chunk-load-distance` – max edit request distance in chunks.
- `whitelist-world-regex` / `blacklist-world-regex` – world-level gating.
- `disallowed-blocks` – forbid specific blocks/states.
- `whitelist-entities` / `blacklist-entities` – restrict client entity operations.

### Network and payload policy

- `max-block-buffer-packet-size` – upper bound for block buffer packets.
- `allow-large-chunk-data-request` – enables larger chunk/entity request payload behavior.
- `allow-large-payload-for-all-packets` – raises all packet limits (not recommended for public servers).

### Feature toggles

- `blueprint-sharing` – enables server-side blueprint upload/download pathways.
- `allow-annotations` – enables annotation features.
- `send-markers` – marker visibility/manipulation behavior.
- `allow-teleport-between-worlds` – view-based world teleport support.
- `infinite-reach-limit` – cap for infinite reach capability.

### Compatibility behavior

- `incompatible-data-version` – action for incompatible data version (`kick`, `warn`, `ignore`).
- `unsupported-axiom-version` – action for unsupported Axiom client (`kick`, `warn`, `ignore`).

### Logging and audit

- `log-large-block-buffer-changes` – logs large buffer operations.
- `log-core-protect-changes` – when CoreProtect exists, control Axiom change logging.
- `disable-entity-sanitization` – disables entity sanitization (high-risk; generally avoid).

---

## Integrations

This plugin supports optional integrations when the target plugin is present:

- **LuckPerms**: permission-cache related behavior and updates.
- **CoreProtect**: change logging support.
- **WorldGuard / PlotSquared**: region ownership/protection-aware editing constraints.
- **ViaVersion**: protocol compatibility support in mixed-version environments.

---

## Troubleshooting

### Player can use Axiom in singleplayer but not multiplayer

1. Grant `axiom.default` (or OP / `axiom.all`).
2. Reconnect the player.
3. Verify no world regex restriction blocks target world.
4. Check region-protection integration state (WorldGuard/PlotSquared).
5. Run `/axiompaperdebug canUseAxiom` and `/axiompaperdebug getRestrictions`.

### Version mismatch warnings

- Ensure server/client versions are compatible.
- Adjust `incompatible-data-version` and `unsupported-axiom-version` policy depending on how strict you want server admission to be.

### Config changed but behavior did not

- Restart server after significant policy/scheduler-affecting changes.
- If keys are outdated, run `/axiompapermigrateconfig`.

---

## Developer notes

### Build from source

```bash
./gradlew build
```

### Local server testing tasks

```bash
./gradlew runServer
```

### Packaging

The project uses Shadow for shaded outputs and paperweight for Paper development bundles.

### Folia-safe extension design guidance

If you add new world/entity mutations:

- Prefer scheduling through the existing `ThreadingBridge`.
- Use chunk-region or entity/player scheduler context for mutation code.
- Keep global scheduler work for truly global/plugin-only tasks.

---

## Security & hardening recommendations

For public servers, recommended baseline:

- Keep `allow-large-payload-for-all-packets: false`.
- Keep `allow-large-chunk-data-request: false` unless you trust your player base.
- Keep `disable-entity-sanitization: false`.
- Avoid granting broad `axiom.entity.*` unless required.
- Use world allow/deny regexes to constrain editing scope.
- Use per-rank `block-buffer-rate-limit` overrides to prevent heavy abuse.

---

## FAQ

### Does this plugin support Folia?

Yes. It declares Folia support in plugin metadata and routes execution to Folia schedulers at runtime when Folia is detected.

### Do I need a separate build for Folia?

No. The runtime bridge handles Paper and Folia scheduler differences.

### Which permission should I give normal builders?

Start with `axiom.default`, then remove/override specific capabilities if your security model requires a stricter profile.

### Is this safe for public servers?

Yes, when configured conservatively. Review payload limits, entity permissions, and world/region constraints before opening access broadly.
