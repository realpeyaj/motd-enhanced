# Learned Preferences & Architectural Memory

## Core Principles
- **Avoid Inconsistency & Bloat**: Always prioritize surgical, minimal fixes over bloated code, multi-stage speculative fallbacks, or external network scraping. Keep implementations consistent with existing architectural patterns in the codebase.
- **Negative Constraints**:
  - Do NOT make background HTTP calls to external IP lookup services (e.g., `api.ipify.org`).
  - Do NOT scan network interfaces or register extraneous player join listeners for host/IP resolution.
  - Do NOT allow Gradle's bare `jar` task to collide with or overwrite `shadowJar`. Always set `jar.enabled = false` (or set a distinct archive classifier like `unshaded`) and make `assemble` depend on `shadowJar` so that unshaded jars without bundled libraries are never packaged as the primary distribution jar.
  - Do NOT add MOTD preset/template galleries or template selection menus to the web editor; keep the editor clean, focused, and lightweight.
## Project Identity & Versioning
- **Official Name**: `motd-enhanced` (Author: `peyaj`, Commands: `/motd`, `/motd-enhanced`, Package: `dev.peyaj.motdenhanced`).
- **Versioning Standard**: 1 decimal place only (e.g., `1.0`, `1.1`, `2.0`). Never use 3-part semver (e.g. `1.0.0`).
- **Adventure API Lifecycle**: In PaperMC/Bukkit environments, guard legacy `BukkitAudiences.create(this)` calls defensively in try-catch blocks to prevent startup crashes (`NoClassDefFoundError`), allowing native Paper Audience methods (`sender.sendMessage(Component)`) to execute natively.
- **Git Commit Messages**: Use natural, human-written phrasing (e.g., "Rebrand to motd-enhanced and embed local web editor") rather than rigid conventional commit prefixes ("feat:", "chore:").

