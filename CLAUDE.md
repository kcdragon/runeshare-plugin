# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A RuneLite external plugin for [RuneShare](https://osrs.runeshare.app/), a site for sharing and discovering Old School RuneScape bank tag tabs. The plugin does two things: pushes the player's active bank tag tab to the RuneShare API, and tracks combat "task sessions" (XP gained while fighting a given NPC).

Users authenticate with a per-user API token created at https://osrs.runeshare.app/api_tokens and pasted into the plugin's RuneLite settings.

## Build and run

Java 11 is required — `build.gradle` sets `options.release.set(11)`, matching the RuneLite plugin hub. The Gradle wrapper is 8.10, which cannot run on JDK 23+. A `mise.toml` pins `java = "temurin-11.0.32+9"`, so prefixing with `mise exec --` picks up the right JDK regardless of the global default.

```bash
mise exec -- ./gradlew build       # compile + test
mise exec -- ./gradlew clean build # full rebuild
```

To run RuneLite with the plugin loaded, launch the `app.runeshare.RuneSharePluginTest` main class (test source set) with program args `--debug --developer-mode`. `.run/RuneSharePluginTest.run.xml` is a ready-made IntelliJ configuration for this. From the CLI:

```bash
mise exec -- ./gradlew shadowJar   # fat jar with RuneLite + plugin, Main-Class is the launcher
java -jar build/libs/RuneShare-1.0-SNAPSHOT-all.jar --debug --developer-mode
```

There is no lint task and no checkstyle config. Despite the name, `RuneSharePluginTest` is a launcher `main`, not a JUnit test. The real tests are JUnit 4 unit tests of pure helpers (`LoadoutTest`, `PlayerAccountTest`); run one with `mise exec -- ./gradlew test --tests app.runeshare.PlayerAccountTest`. Logic that reads the `client` is kept thin so that the mapping it feeds can be extracted into such a helper and tested.

## Dependency versions

`runeLiteVersion` is `latest.release`, so builds float against whatever RuneLite has most recently published. A build that compiled yesterday can break today when RuneLite changes an API — if you hit an unexpected compile error in `net.runelite.*` code you did not touch, suspect this before suspecting the local change.

## Code style

Prefer self-documenting code over comments. A comment that restates what the code
does is noise that goes stale; a well-named variable or method carries the same
information and cannot drift from the code it describes.

- Extract a non-obvious condition into a named `final boolean` rather than
  explaining it above the `if`. See `leavingCharacter` in
  `RuneSharePlugin.onGameStateChanged`.
- Extract a block that needs a "this part does X" comment into a method named `x`.
- Name variables for what they hold, not their type.

Write a comment only when it explains something the code genuinely cannot: a
non-obvious *why*, an external constraint, or a trap for the next reader. The
threading and defensive-copy notes documented below are the kind that earn their
place — they warn about a rule that is invisible at the call site. When in doubt,
try renaming first.

## Architecture

Three layers, with the plugin class as the only thing touching RuneLite events:

- **`RuneSharePlugin`** — the RuneLite entry point. Subscribes to game events, owns all mutable "what is currently active" state, registers the nav button/panel.
- **`RuneShareSessionTracker`** — task-session state machine (running or not, current `taskSessionId`). Sits between the plugin and the API so the panel can start/stop sessions without knowing about HTTP.
- **`RuneShareApi`** — all HTTP. `@Singleton`, injected with RuneLite's shared `OkHttpClient` and `Gson`.
- **`api/RuneShareConnection`** — whether RuneShare accepts the configured token (`ConnectionStatus`) and whose it is (`CurrentUser`, from `GET /api/me`). Every response from every endpoint is reported to it, so a token revoked mid-session shows up without a restart. Reports carry the token the request was sent with and are ignored once the player has changed it.
- **`ui/RuneSharePluginPanel`** — the side panel.

`@PluginDependency(BankTagsPlugin.class)` makes RuneLite inject `TabManager`, `TagManager`, and `BankTagsService`. These are the only source of bank tag data; the plugin never reads the bank widget directly.

### Threading

This is the constraint most likely to bite you. RuneLite calls `@Subscribe` handlers on the **client thread**; Swing work must happen on the **EDT**. The established pattern is: read everything you need off the client thread synchronously into local `final` variables, then hand only those values to `SwingUtilities.invokeLater`. See `onHitsplatApplied` in `RuneSharePlugin`, which snapshots all seven skill XP values before the `invokeLater` — do not move `client.getSkillExperience(...)` (or any other `client.*` call) inside the lambda.

HTTP is never blocking: every `RuneShareApi` method builds a request and uses `okHttpClient.newCall(request).enqueue(...)`, with results delivered through the `...ResponseHandler` callback interfaces. Do not switch these to `execute()` — it would block whichever thread called in. Those callbacks run on OkHttp threads; `RuneShareSessionTracker` hops to the EDT before touching session state or calling the panel back, and `RuneShareConnection` and `BankTabSync` guard their state with a lock and notify the panel, whose `redraw()` makes the hop.

Retries are scheduled on RuneLite's injected `ScheduledExecutorService`. It is shared by every plugin: cancel the futures you scheduled (`RuneShareApi.cancelRetries()`, `BankTabSync.cancel()` in `shutDown`), never shut down the executor.

### Change detection

There are no RuneLite events for "the bank tag tab changed", so `onGameTick` polls: each tick it compares the active tag, item IDs, and layout against the cached copies and acts only on a difference. The cached `Layout` and item list are **defensive copies** (`new Layout(layout)`, `new ArrayList<>(itemIds)`) because RuneLite mutates the originals in place — comparing against a live reference would always report "unchanged". Preserve that when editing.

Combat task events are separately rate-limited to one every 30s (`TIME_BETWEEN_TASK_EVENTS_MS`) via `lastTaskEventSentAtMs`, since hitsplats fire far too often to send each one.

### API conventions

All requests go to `https://osrs.runeshare.app`, authenticate with `Authorization: Token token=<apiToken>`, and serialize with one Gson built in `RuneShareApi`'s constructor as `gson.newBuilder().setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)`. The API expects snake_case, so camelCase Java fields map automatically — new request classes are plain Lombok `@Builder`/`@Getter` DTOs in `app.runeshare.api` and need no annotations to serialize correctly.

Endpoints in use: `GET /api/me`, `POST /api/bank_tabs`, `POST /api/task_sessions`, `PUT /api/task_sessions/{id}`, `POST /api/task_sessions/{id}/task_events`. The server documents them in `docs/API.md` of the `runeshare` repo; fields are only ever added there, so treat any field newer than the endpoint (like `profile_url` on `/me`) as nullable.

Every request goes through `RuneShareApi.send`, which logs failures with the response body, reports the outcome to `RuneShareConnection`, and wraps it in an `ApiResponse` (`NO_RESPONSE` when there was none, plus the first entry of a 422's `errors` array). How each call handles failure:

- `POST /api/bank_tabs` — `BankTabSync` keeps only the latest tab, retries transient failures per `RetryPolicy.BANK_TAB` (30s, 60s, 120s), and shows the outcome in the Bank Tabs section.
- `POST /api/task_sessions` — no retry; the panel re-enables Start and shows why.
- `PUT /api/task_sessions/{id}` — retried per `RetryPolicy.STOP_TASK_SESSION`; the panel has already moved on, and only shows a message if every try fails.
- `POST .../task_events` — no retry, nothing shown. Each event carries total XP, so the next one makes up for a lost one.

### Panel rendering

`RuneSharePluginPanel.drawPanel()` rebuilds the entire panel from scratch (`removeAll()` then re-add) on every update rather than mutating existing components. Every state change funnels through it, so follow that pattern rather than introducing incremental updates. What renders is driven by, in order: whether an API token is configured, the `ConnectionStatus` (a rejected token hides everything else), whether there is an active tag tab, whether `autoSave` is on (auto-save message vs. a manual "Sync to RuneShare" button), and whether an NPC is being fought (Start/Stop Session button).

`RuneShareConfig.autoSave()` defaults to `false`, i.e. manual sync.

## Plugin hub metadata

`runelite-plugin.properties` (displayName, author, description, tags, plugin class) is what the RuneLite plugin hub reads. Its `tags` and `description` duplicate the `@PluginDescriptor` annotation on `RuneSharePlugin` — keep the two in sync when changing either.
