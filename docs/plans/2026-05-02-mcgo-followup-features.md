# MC-GO Follow-up Features Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Remove misleading hardcoded/latest behaviors, add persistent appearance settings and manual Java override, simplify server/tunnel UI, and start the deeper FRP real-runtime + multi-server architecture work.

**Architecture:** Split work into two tracks. Track A updates model/storage/UI with TDD-first regression coverage and should be shippable incrementally. Track B introduces real tunnel runtime orchestration and multi-server runtime isolation; it requires deeper service/runtime changes and must be validated end-to-end.

**Tech Stack:** Kotlin, Jetpack Compose, Android Service/foreground service, native JLI launcher, properties-backed local persistence, Gradle Android build.

---

### Task 1: Remove hardcoded 26.1.2 assumptions from version fallback/UI copy

**Objective:** Stop presenting `26.1.2` as a baked-in latest patch while retaining Java 25 support for 26.x.

**Files:**
- Modify: `app/src/main/java/com/mcgo/app/server/PaperServerRuntime.kt`
- Modify: `app/src/main/java/com/mcgo/app/ui/model/JavaManagementModels.kt`
- Test: `app/src/test/java/com/mcgo/app/server/PaperVersionRepositoryTest.kt`
- Test: `app/src/test/java/com/mcgo/app/ui/model/JavaManagementModelsTest.kt`

**Step 1: Write failing tests**
- Add test asserting fallback versions do not hard-pin `26.1.2`.
- Add test asserting Java 25 description does not contain `26.1.2`.

**Step 2: Run tests to verify failure**
Run: `./gradlew testDebugUnitTest --tests 'com.mcgo.app.server.PaperVersionRepositoryTest' --tests 'com.mcgo.app.ui.model.JavaManagementModelsTest'`

**Step 3: Implement minimal production fix**
- Remove `26.1.2` from hardcoded fallback list.
- Make Java 25 label generic for `26.x` instead of a fixed patch.

**Step 4: Re-run tests**
Run same command and ensure pass.

### Task 2: Add explicit auto/manual Java selection model and persistence

**Objective:** Let users keep recommended Java by default but manually override it and persist the choice.

**Files:**
- Modify: `app/src/main/java/com/mcgo/app/ui/model/McGoUiModels.kt`
- Modify: `app/src/main/java/com/mcgo/app/ui/storage/ServerProfileStore.kt`
- Test: `app/src/test/java/com/mcgo/app/ui/model/ServerModelsTest.kt`
- Create/Modify: `app/src/test/java/com/mcgo/app/ui/storage/ServerProfileStoreTest.kt`

**Step 1: Write failing tests**
- Auto-recommended server still updates Java with MC version changes.
- Manual override survives edits and store round-trip.

**Step 2: Run targeted tests to verify failure**
Run: `./gradlew testDebugUnitTest --tests 'com.mcgo.app.ui.model.ServerModelsTest' --tests 'com.mcgo.app.ui.storage.ServerProfileStoreTest'`

**Step 3: Implement minimal model/storage changes**
- Add auto/manual Java selection semantics to `ServerCardState`.
- Persist explicit Java version + selection mode.
- Preserve manual override in edit flows.

**Step 4: Re-run tests**
Run same command and ensure pass.

### Task 3: Add Java selection UI to create/edit server dialogs

**Objective:** Expose recommended Java plus manual selection controls in server create/edit dialogs.

**Files:**
- Modify: `app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt`
- Modify: `app/src/main/java/com/mcgo/app/ui/MCGoApp.kt`
- Possibly modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/mcgo/app/ui/model/ServerModelsTest.kt`

**Step 1: Extend failing tests**
- Add model-level test for choosing manual Java.

**Step 2: Run targeted test**
Run: `./gradlew testDebugUnitTest --tests 'com.mcgo.app.ui.model.ServerModelsTest'`

**Step 3: Implement minimal UI wiring**
- Add Java selector and “跟随推荐/手动指定” affordance.
- Feed selected Java into create/edit output.

**Step 4: Re-run tests and compile check**
Run: `./gradlew testDebugUnitTest --tests 'com.mcgo.app.ui.model.ServerModelsTest' :app:compileDebugKotlin`

### Task 4: Persist appearance preferences across cold starts

**Objective:** Save theme/accent/font/transparency/background settings so they survive app restart.

**Files:**
- Create: `app/src/main/java/com/mcgo/app/ui/storage/AppearancePreferencesStore.kt`
- Modify: `app/src/main/java/com/mcgo/app/ui/model/AppearancePreferences.kt`
- Modify: `app/src/main/java/com/mcgo/app/ui/MCGoApp.kt`
- Create: `app/src/test/java/com/mcgo/app/ui/storage/AppearancePreferencesStoreTest.kt`

**Step 1: Write failing tests**
- Round-trip save/load for all appearance fields.

**Step 2: Run test to verify failure**
Run: `./gradlew testDebugUnitTest --tests 'com.mcgo.app.ui.storage.AppearancePreferencesStoreTest'`

**Step 3: Implement store and hook app startup/change persistence**
- Reuse simple properties/shared-preferences style.

**Step 4: Re-run tests**
Run same test and `:app:compileDebugKotlin`.

### Task 5: Simplify tunnel latency UX to manual refresh

**Objective:** Remove auto-polling latency updates and replace with explicit user-triggered refresh from tunnel cards/top-right area.

**Files:**
- Modify: `app/src/main/java/com/mcgo/app/ui/MCGoApp.kt`
- Modify: `app/src/main/java/com/mcgo/app/ui/screens/TunnelScreen.kt`
- Modify: `app/src/main/java/com/mcgo/app/ui/model/TunnelModels.kt`
- Test: `app/src/test/java/com/mcgo/app/ui/model/TunnelModelsTest.kt`

**Step 1: Write failing tests**
- Add model test for refresh state text if needed.

**Step 2: Run targeted tests**
Run: `./gradlew testDebugUnitTest --tests 'com.mcgo.app.ui.model.TunnelModelsTest'`

**Step 3: Implement**
- Remove 5s loop.
- Add explicit refresh action callback.
- Show single latency label and refresh icon.

**Step 4: Re-run tests and compile**
Run targeted tests and `:app:compileDebugKotlin`.

### Task 6: Simplify server cards and move log copy into console only

**Objective:** Remove inline runtime logs from server cards, add copyable address pill, and reduce controls to icon-only actions.

**Files:**
- Modify: `app/src/main/java/com/mcgo/app/ui/screens/ServersScreen.kt`
- Modify: `app/src/main/java/com/mcgo/app/ui/MCGoApp.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Step 1: Add small regression assertions where practical**
- Server model test for connection/address helper if introduced.

**Step 2: Implement UI cleanup**
- Remove `RuntimeProgressPanel` from list cards.
- Add address pill with copy action.
- Move copy-log action to console dialog.
- Replace chips with icon buttons and a clearer console icon.

**Step 3: Compile verify**
Run: `./gradlew :app:compileDebugKotlin`

### Task 7: Prepare FRP real runtime scaffolding

**Objective:** Add actual tunnel runtime architecture entry points rather than UI-only metadata.

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create/Modify: tunnel runtime service/orchestrator files under `app/src/main/java/com/mcgo/app/tunnel/`
- Modify: `app/src/main/java/com/mcgo/app/ui/model/TunnelModels.kt`
- Modify: `app/src/main/java/com/mcgo/app/ui/MCGoApp.kt`

**Step 1: Decide first supported scope**
- First real support should target FRP.
- Prefer `PastedConfig` as the first true runnable path.

**Step 2: Add tests around orchestration state where possible**
- Service/controller unit tests for per-tunnel start/stop state.

**Step 3: Implement minimal runnable scaffolding**
- No fake success states.
- Do not mark tunnel/server externally reachable unless runtime actually starts.

### Task 8: Remove single-server gate and start multi-server runtime refactor

**Objective:** Begin allowing multiple distinct servers to start, removing explicit UI/service single-server guardrails.

**Files:**
- Modify: `app/src/main/java/com/mcgo/app/ui/MCGoApp.kt`
- Modify: `app/src/main/java/com/mcgo/app/server/PaperServerService.kt`
- Test: `app/src/test/java/com/mcgo/app/server/PaperServerServiceStateTest.kt`

**Step 1: Write failing tests**
- Replace assertions that require `单服运行` conflicts.

**Step 2: Run tests to verify failure**
Run: `./gradlew testDebugUnitTest --tests 'com.mcgo.app.server.PaperServerServiceStateTest'`

**Step 3: Implement the first stage**
- Remove front-end single-server rejection.
- Remove service string contract that claims only single-server is supported.
- If deeper runtime isolation is not complete yet, keep honest “not yet available” semantics per architecture stage.

### Task 9: Full verification

**Objective:** Re-run targeted and full validation before shipping.

**Files:**
- N/A

**Step 1: Run targeted suites**
Run:
- `./gradlew testDebugUnitTest --tests 'com.mcgo.app.server.PaperVersionRepositoryTest' --tests 'com.mcgo.app.ui.model.JavaManagementModelsTest' --tests 'com.mcgo.app.ui.model.ServerModelsTest' --tests 'com.mcgo.app.ui.model.TunnelModelsTest'`

**Step 2: Run full app verification**
Run:
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
- `git diff --check`

**Step 3: Review**
- Independent reviewer on changed diff.

**Step 4: Package**
- Copy APK to release path and compute SHA-256.
