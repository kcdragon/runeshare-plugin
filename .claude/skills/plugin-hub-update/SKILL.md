---
name: plugin-hub-update
description: Prepare a RuneLite plugin hub update for RuneShare. Syncs the user's plugin-hub fork with upstream, creates a branch that points plugins/runeshare at the latest pushed plugin commit, pushes it, and gives back a link to open the PR. Use when the user wants to release, publish, or update the plugin on the plugin hub.
---

# Plugin hub update

The RuneLite plugin hub (https://github.com/runelite/plugin-hub) builds plugins from a
manifest file, `plugins/runeshare`, that pins a commit of this repo:

```
repository=https://github.com/kcdragon/runeshare-plugin.git
commit=<40-char sha>
warning=...
```

An update is a PR to upstream that changes only `commit=`. **Do not open the PR.** The
user opens it. The deliverable is a compare URL they click to open it.

## Layout

- Plugin repo: this repo, `origin` = `kcdragon/runeshare-plugin`, branch `master`.
- Hub fork: `~/code/runelite-plugin-hub`
  - `origin` = `kcdragon/runelite-plugin-hub` (fork)
  - `upstream` = `runelite/plugin-hub`
- Hub branch name: `runeshare` (reused for every update, force-pushed).

## Steps

### 1. Make sure the plugin commit is on GitHub

The hub's CI clones from GitHub, so an unpushed commit fails the build.

```bash
git -C ~/Code/runeshare-plugin fetch origin
git -C ~/Code/runeshare-plugin status -sb   # look for "ahead N"
```

If local `master` is ahead of `origin/master`, ask the user before pushing. Pushing
is outward-facing. If they decline, use `origin/master`'s sha instead.

```bash
SHA=$(git -C ~/Code/runeshare-plugin rev-parse origin/master)
```

Also confirm that `mise exec -- ./gradlew build` passes at that commit. The hub
won't build a broken plugin.

### 2. Sync the fork's master with upstream

```bash
cd ~/code/runelite-plugin-hub
git status --porcelain       # must be empty; stop and ask if it isn't
git fetch upstream
git fetch origin
git push origin upstream/master:refs/heads/master
```

The fork's `master` is never committed to directly, so that push is a fast-forward.
If it is rejected as non-fast-forward, stop and ask. Don't force it.

### 3. Branch off upstream and bump the commit

```bash
git checkout -B runeshare upstream/master
OLD=$(sed -n 's/^commit=//p' plugins/runeshare)
sed -i '' "s/^commit=.*/commit=$SHA/" plugins/runeshare   # BSD sed (macOS)
git diff                                                   # exactly one line changed
git add plugins/runeshare
git commit -m "update runeshare"
git push -f -u origin runeshare
```

The upstream convention for the commit message is `update <plugin-name>`. Leave off the
Co-Authored-By trailer. This repo belongs to RuneLite, and every other commit
there is a bare `update <name>`.

If `$OLD` already equals `$SHA`, there's nothing to release. Tell the user and stop.

### 4. Check the `warning=` line

The `warning=` line lists what data the plugin sends off-client. Review the plugin
changes being released (`git -C ~/Code/runeshare-plugin log --oneline $OLD..$SHA`). If a
change sends a new *kind* of data, ask the user whether the warning should change.
Examples: location, IP, account details, chat, items. Hub reviewers check it. To find
new data, look at fields added to the request DTOs in `app.runeshare.api`.

If the warning changes, keep the existing sentence shape and add the new data to the
list:

```
warning=This plugin submits your bank tag tab data, inventory and equipment, account type, in-game location, and IP address to a server not controlled or verified by the RuneLite developers.
```

Fold the edit into the same `update runeshare` commit (`git commit -a --amend --no-edit`)
and force-push again. The PR should stay one commit.

### 5. Hand back the link and a recommended PR body

Always give both. The link:

```
https://github.com/runelite/plugin-hub/compare/master...kcdragon:runelite-plugin-hub:runeshare?expand=1
```

The PR body goes in a fenced markdown block, ready to paste. The readers are hub
reviewers, so write it for them. Say what changed for the player. Call out anything
that affects what data leaves the client, and say whether it can be turned off in
config. Leave out internal refactors.

Use this template:

```markdown
Updates RuneShare from `<old short sha>` to `<new short sha>`.

### Changes
- <one bullet per user-facing change, in plain language>

### Data sent
<what new data is sent, to which endpoint, and the config toggle that controls it.
If the warning= line changed, say so here. If nothing new is sent, say "No new
data is sent.">

Full diff: https://github.com/kcdragon/runeshare-plugin/compare/<old sha>...<new sha>
```

No Claude attribution line goes in this body. The user is the one opening the PR.

## After the PR merges

Nothing is required. The `runeshare` branch gets recreated from `upstream/master` and
force-pushed next time. If a PR is still open, pushing to `runeshare` updates it in
place. Upstream prefers this over opening a second PR.
