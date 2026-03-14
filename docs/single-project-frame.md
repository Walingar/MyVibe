# Single ProjectFrame Architecture (Application-Level Project Switching)

## Scope

This document describes the "single frame for the whole application" refactor, where the IDE reuses one `ProjectFrame` UI and decouples frame lifetime from `Project` lifetime.

## Decision Summary

- The application should always show a `ProjectFrame` (even with zero open projects).
- `ProjectFrame` must not depend on a host/placeholder project.
- Project switching should be UI-silent: switching current project must not recreate frame-level UI.
- Project listing/switching is exposed via application-level toolwindow APIs.

## What Was Changed

### 1) No-project state now uses real `ProjectFrame` infrastructure

- `NoProjectFrameManager` now creates/uses `IdeProjectFrameHelper` + `ToolWindowPane`.
- No custom root-pane replacement is used anymore.
- When no projects are open, the same frame remains visible and is stored for reuse.

### 2) Reuse existing frame helper on project open

- `IdeProjectFrameAllocator` detects a reused frame that already has an `IdeProjectFrameHelper` with no project assigned.
- Instead of creating another helper, it reuses the existing one and continues normal project assignment flow.
- This prevents helper duplication and keeps one continuous frame UI.

### 3) Application-level toolwindow extension point

- Added `com.intellij.applicationToolWindowFactory` EP for toolwindows that are not project-scoped.
- Projects UI was moved to this application-level API.

## Why This Design

- It satisfies the requirement to keep exact `ProjectFrame` UI semantics without welcome screen.
- It removes coupling between "frame exists" and "project exists".
- It avoids visual churn when switching projects.
- It keeps future extension surface clean (`ApplicationToolWindowFactory`) for other app-scoped toolwindows.

## Constraints and Notes

- Loading indicators in no-project mode must use a completed loading state (otherwise a permanent spinner appears).
- Project actions still operate on the currently selected/active project context; frame lifetime is independent.
- No backward compatibility guarantees were targeted for this refactor.
