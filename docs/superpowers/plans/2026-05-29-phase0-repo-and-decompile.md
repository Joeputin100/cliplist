# ClipList Phase 0 — Repo & CI Decompile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the private GitHub repo and a CI-only reverse-engineering pipeline that extracts the original *My Music Playlist Creator* app's exact `.m3u` byte format into a documented `FORMAT.md` plus golden-master fixtures, with zero local Android builds.

**Architecture:** A single private GitHub repo holds the reference APK, the decompile workflow, and (later) the app. A manually-triggered GitHub Actions workflow runs `jadx` + `apktool` on the APK (CI has ~16 GB RAM, so no OOM), uploads the decompiled source as an artifact (never committed), and a human/agent reads the export routines to author `FORMAT.md` and synthetic golden fixtures that later phases test against.

**Tech Stack:** git, GitHub CLI (`gh`), GitHub Actions, jadx, apktool, Java 17 (CI only). No Android SDK or Gradle in this phase.

**This is Phase 0 of a phased series.** Phases 1–5 (the `:core-format` serializer, scan/SAF storage, Compose MD3E UI, cleaning flow, build/release) will be planned **after** this phase reveals the byte format. See `docs/superpowers/specs/2026-05-29-cliplist-design.md` §21.

**Naming note:** the repo is named `cliplist` and the reference APK is renamed `mym3ucreator-2.1.1.apk`. If you prefer different names, change them consistently everywhere before starting.

---

### Task 1: Organize the reference APK under `reference/`

**Files:**
- Move: `My Music Playlist Creator_2.1.1_APKPure.apk` → `reference/mym3ucreator-2.1.1.apk`
- Create: `reference/README.md`

- [ ] **Step 1: Move and rename the APK into `reference/`**

```bash
cd /home/projects/mpc
mkdir -p reference
mv "My Music Playlist Creator_2.1.1_APKPure.apk" reference/mym3ucreator-2.1.1.apk
ls -la reference/
```

Expected: `reference/mym3ucreator-2.1.1.apk` exists (~1.4 MB); the spaced filename is gone.

- [ ] **Step 2: Record provenance + checksum in `reference/README.md`**

First get the checksum:

```bash
sha256sum reference/mym3ucreator-2.1.1.apk
```

Then create `reference/README.md` (paste the real hash from the command above in place of `<SHA256>`):

```markdown
# Reference material (private — do not redistribute)

- **File:** `mym3ucreator-2.1.1.apk`
- **App:** My Music Playlist Creator (`com.matt.mym3ucreator`), v2.1.1, built 2016-07-30
- **Source:** downloaded from APKPure by the repo owner
- **SHA-256:** `<SHA256>`

Kept solely as a private reference for **interoperability and study** — to reproduce the
exact `.m3u` byte format the SanDisk Clip Sport expects. The decompiled source is **never
committed** (it is produced as an ephemeral CI artifact). See `FORMAT.md` once generated.
```

- [ ] **Step 3: Verify the APK is the file we expect**

```bash
unzip -l reference/mym3ucreator-2.1.1.apk | grep -E "classes.dex|AndroidManifest.xml"
```

Expected: lists `AndroidManifest.xml` and `classes.dex` (~2.69 MB).

- [ ] **Step 4: Commit**

```bash
cd /home/projects/mpc
git add reference/
git commit -m "chore: vendor reference APK under reference/ with provenance"
```

Expected: commit succeeds; `git ls-files reference/` shows the APK and README.

---

### Task 2: Create the private GitHub repo and push

**Files:** none (remote + push operation)

- [ ] **Step 1: Confirm auth and identity**

```bash
gh auth status
git -C /home/projects/mpc log --oneline -1
```

Expected: logged in as `Joeputin100` with `repo` + `workflow` scopes; at least one local commit exists.

- [ ] **Step 2: Create the private repo and push `main`**

```bash
cd /home/projects/mpc
gh repo create Joeputin100/cliplist \
  --private \
  --source=. \
  --remote=origin \
  --push \
  --description "ClipList — modern SanDisk Clip Sport playlist creator (private; reference APK kept for interop/study)"
```

Expected: prints the new repo URL; pushes `main`. If the name `cliplist` is taken, pick another (e.g. `cliplist-app`) and use it consistently from here on.

- [ ] **Step 3: Verify it is private and the push landed**

```bash
gh repo view Joeputin100/cliplist --json visibility,defaultBranchRef -q '.visibility, .defaultBranchRef.name'
git -C /home/projects/mpc remote -v
```

Expected: `PRIVATE` then `main`; `origin` points at `github.com:Joeputin100/cliplist`.

---

### Task 3: Add the CI decompile workflow

**Files:**
- Create: `.github/workflows/decompile.yml`

- [ ] **Step 1: Create the workflow file**

Create `.github/workflows/decompile.yml`:

```yaml
name: Decompile reference APK

# Manual only — this is a one-off reverse-engineering job, not part of CI builds.
on:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  decompile:
    runs-on: ubuntu-latest
    env:
      APK: reference/mym3ucreator-2.1.1.apk
      JADX_VERSION: "1.5.0"
      APKTOOL_VERSION: "2.10.0"
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Install jadx
        run: |
          set -euo pipefail
          mkdir -p "$HOME/tools/jadx"
          curl -fSL -o /tmp/jadx.zip \
            "https://github.com/skylot/jadx/releases/download/v${JADX_VERSION}/jadx-${JADX_VERSION}.zip"
          unzip -q /tmp/jadx.zip -d "$HOME/tools/jadx"
          echo "$HOME/tools/jadx/bin" >> "$GITHUB_PATH"

      - name: Install apktool
        run: |
          set -euo pipefail
          mkdir -p "$HOME/tools/apktool"
          curl -fSL -o "$HOME/tools/apktool/apktool.jar" \
            "https://github.com/iBotPeaches/Apktool/releases/download/v${APKTOOL_VERSION}/apktool_${APKTOOL_VERSION}.jar"

      - name: Decompile Java sources with jadx
        # jadx can exit non-zero on a few classes it cannot fully decompile;
        # we keep going because partial output is still useful.
        run: |
          jadx --no-debug-info -d decompiled/jadx "$APK" || true

      - name: Decode resources + smali + manifest with apktool
        run: |
          java -jar "$HOME/tools/apktool/apktool.jar" d -f -o decompiled/apktool "$APK"

      - name: Sanity-check that the export classes were recovered
        run: |
          echo "== jadx export-related files =="
          find decompiled/jadx -iname "*.java" | grep -i "mym3ucreator" | head -50 || true

      - name: Upload decompiled output
        uses: actions/upload-artifact@v4
        with:
          name: decompiled-reference
          path: decompiled/
          retention-days: 14
```

- [ ] **Step 2: Verify the workflow file is valid YAML locally**

```bash
cd /home/projects/mpc
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/decompile.yml')); print('YAML OK')"
```

Expected: `YAML OK` (no traceback).

- [ ] **Step 3: Commit and push**

```bash
cd /home/projects/mpc
git add .github/workflows/decompile.yml
git commit -m "ci: add manual jadx+apktool decompile workflow"
git push origin main
```

Expected: push succeeds (the `gh` token has the `workflow` scope).

- [ ] **Step 4: Verify GitHub registered the workflow**

```bash
gh workflow list
```

Expected: `Decompile reference APK` appears in the list.

---

### Task 4: Run the decompile job in CI and fetch the output

**Files:** none (CI run + artifact download; `decompiled/` is git-ignored)

- [ ] **Step 1: Dispatch the workflow**

```bash
cd /home/projects/mpc
gh workflow run decompile.yml --ref main
sleep 8
gh run list --workflow=decompile.yml --limit 1
```

Expected: a run appears (status `queued` or `in_progress`).

- [ ] **Step 2: Wait for it to finish**

```bash
RUN_ID=$(gh run list --workflow=decompile.yml --limit 1 --json databaseId -q '.[0].databaseId')
echo "Watching run $RUN_ID"
gh run watch "$RUN_ID" --exit-status
```

Expected: ends with `✓` success. If it fails on a download URL (404), bump `JADX_VERSION` / `APKTOOL_VERSION` in the workflow to the current latest release, recommit, and re-run.

- [ ] **Step 3: Download the artifact**

```bash
cd /home/projects/mpc
RUN_ID=$(gh run list --workflow=decompile.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run download "$RUN_ID" -n decompiled-reference -D decompiled
ls decompiled/jadx/sources/com/matt/mym3ucreator/ 2>/dev/null || find decompiled -iname "*.java" | grep -i mym3ucreator | head
```

Expected: the `com/matt/mym3ucreator/...` Java tree is present, including a `services/exportplaylist/` directory.

- [ ] **Step 4: Confirm the key export source files exist**

```bash
cd /home/projects/mpc
find decompiled/jadx -iname "ServiceExport*.java" -o -iname "*Export*.java" | head
grep -rl "ecrirePlayListFromFiles\|ecrirePlayListFromPaths" decompiled/jadx 2>/dev/null
```

Expected: at least one file containing the `ecrirePlayList...` export methods is listed. (No commit — `decompiled/` is intentionally git-ignored.)

---

### Task 5: Author `reference/FORMAT.md` from the decompiled export routines

**Files:**
- Create: `reference/FORMAT.md`
- Modify: `README.md` (add a link to `reference/FORMAT.md`)

- [ ] **Step 1: Inspect how the playlist bytes are written**

Run each probe and read the surrounding code in the matched files:

```bash
cd /home/projects/mpc
SRC=decompiled/jadx/sources/com/matt/mym3ucreator
echo "== EXT headers? =="; grep -rn "EXTM3U\|EXTINF" "$SRC" || echo "NONE -> bare paths"
echo "== line endings =="; grep -rn '\\r\\n\|println\|newLine\|lineSeparator\|line.separator' "$SRC"
echo "== charset / writers =="; grep -rn "OutputStreamWriter\|FileWriter\|PrintWriter\|BufferedWriter\|OutputStream\|getBytes\|Charset\|UTF-8\|UTF_8\|ISO-8859\|ISO_8859\|windows-1252\|Latin" "$SRC"
echo "== entry path style =="; grep -rn "getName()\|getAbsolutePath\|getPath\|separator\|append(" "$SRC"/services/exportplaylist/* 2>/dev/null
echo "== sort =="; grep -rn "Collator\|Collections.sort\|sort(\|compareTo\|compareToIgnoreCase\|CASE_INSENSITIVE" "$SRC"
echo "== playlist filename =="; grep -rn '\.m3u' "$SRC"
```

Read `ServiceExport.java` (and `ecrirePlayListFromFiles` / `ecrirePlayListFromPaths`) end-to-end to confirm each answer.

- [ ] **Step 2: Verify the line-ending finding against the empty-playlist rule**

The Clip Sport requires CRLF; Android's platform line separator is `\n`. So a working app **must** write `\r\n` explicitly (not `println()`/`newLine()`). Confirm which it is:

```bash
cd /home/projects/mpc
grep -rn '"\\r\\n"\|\\r\\n\|0x0d\|0x0a\|(char) 13\|(char) 10' decompiled/jadx/sources/com/matt/mym3ucreator/
```

Expected: evidence of an explicit `\r\n` (or equivalent char codes). If instead you find `println()`/`newLine()` with **no** explicit CR, record that anomaly in FORMAT.md — it changes our assumptions and must be flagged.

- [ ] **Step 3: Write `reference/FORMAT.md` answering every parameter with evidence**

Create `reference/FORMAT.md`. Replace each `ANSWER:` with the finding and paste the 1–3 decompiled lines that prove it under `Evidence:`. No parameter may be left blank or "unknown" — if something is genuinely undeterminable from the source, state that explicitly and how we will resolve it (e.g., run the APK in an emulator).

```markdown
# Original `.m3u` byte format (Clip Sport "classic" profile)

Reverse-engineered from `com.matt.mym3ucreator` v2.1.1 export routines
(`services/exportplaylist/ServiceExport`, `ecrirePlayListFromFiles`, `ecrirePlayListFromPaths`).
This is the **frozen specification** the new serializer must match byte-for-byte.

| # | Parameter | Value |
|---|-----------|-------|
| 1 | Character encoding | ANSWER: |
| 2 | Header lines (`#EXTM3U` / `#EXTINF`) | ANSWER: present? exact text? |
| 3 | Entry path style (bare filename / relative subpath) | ANSWER: |
| 4 | Path separator | ANSWER: |
| 5 | Line ending | ANSWER: (expect `\r\n`) |
| 6 | Trailing newline after last entry | ANSWER: yes / no |
| 7 | Per-folder playlist filename | ANSWER: e.g. `<FolderName>.m3u` |
| 8 | Sort order (alphabetize option) | ANSWER: comparator + case handling |

## Evidence
1. Encoding — Evidence:
2. Header — Evidence:
3. Path style — Evidence:
4. Separator — Evidence:
5. Line ending — Evidence:
6. Trailing newline — Evidence:
7. Filename — Evidence:
8. Sort — Evidence:

## Notes / anomalies
ANSWER: anything surprising, version caveats, or undeterminable points + resolution plan.
```

- [ ] **Step 4: Link FORMAT.md from the top-level README**

In `README.md`, under the "Design spec" line, add:

```markdown
- **Byte format reference:** [`reference/FORMAT.md`](reference/FORMAT.md) — the frozen `.m3u` format the serializer must match.
```

- [ ] **Step 5: Commit and push**

```bash
cd /home/projects/mpc
git add reference/FORMAT.md README.md
git commit -m "docs: document original .m3u byte format from decompiled export routines"
git push origin main
```

Expected: push succeeds; `git ls-files reference/` shows `FORMAT.md`; `decompiled/` is NOT tracked (`git status` shows it ignored/untracked).

---

### Task 6: Create golden-master fixtures derived from `FORMAT.md`

**Files:**
- Create: `reference/format-fixtures/caseA-ascii/input.json`
- Create: `reference/format-fixtures/caseA-ascii/expected/<FolderName>.m3u`
- Create: `reference/format-fixtures/caseB-accented/input.json`
- Create: `reference/format-fixtures/caseB-accented/expected/<FolderName>.m3u`
- Create: `reference/format-fixtures/README.md`

These are **synthetic** (invented filenames — zero proprietary content) and become the byte-exact test inputs/outputs for Phase 1.

- [ ] **Step 1: Define the fixture input format**

Create `reference/format-fixtures/README.md`:

```markdown
# Golden-master fixtures

Each case is a synthetic folder of music. `input.json` describes the folder and options;
`expected/<name>.m3u` is the **exact bytes** the serializer must produce, constructed by
hand from `../FORMAT.md`. Phase 1 feeds `input.json` through `:core-format` and asserts the
output equals `expected/...` byte-for-byte.

`input.json` schema:
{
  "folderName": "Rock",
  "files": ["01 - Song A.mp3", "02 - Song B.mp3"],
  "options": { "alphabetize": true, "cleanFilenames": false }
}
```

- [ ] **Step 2: Create case A (plain ASCII)**

Create `reference/format-fixtures/caseA-ascii/input.json`:

```json
{
  "folderName": "Rock",
  "files": ["03 - Gamma.mp3", "01 - Alpha.mp3", "02 - Beta.mp3"],
  "options": { "alphabetize": true, "cleanFilenames": false }
}
```

Create `reference/format-fixtures/caseA-ascii/expected/Rock.m3u` containing **exactly** what the original would produce for that input per `FORMAT.md` (correct header-or-not, alphabetized order, the path style, and CRLF line endings). Build it precisely; do not approximate.

- [ ] **Step 3: Verify case A has CRLF endings and the right line count**

```bash
cd /home/projects/mpc
F=reference/format-fixtures/caseA-ascii/expected/Rock.m3u
echo "CRLF count:"; grep -c $'\r' "$F"
echo "Hex tail:"; xxd "$F" | tail -3
```

Expected: the CRLF count equals the number of lines FORMAT.md says it should have (e.g., 3 entries, or 4 if an `#EXTM3U` header line is included); the hex shows `0d 0a` at line ends and the trailing-newline state matches FORMAT.md.

- [ ] **Step 4: Create case B (accented names — exercises encoding)**

Create `reference/format-fixtures/caseB-accented/input.json`:

```json
{
  "folderName": "Café",
  "files": ["Naïve.mp3", "Résumé.mp3"],
  "options": { "alphabetize": true, "cleanFilenames": false }
}
```

Create `reference/format-fixtures/caseB-accented/expected/Café.m3u` with the exact bytes per `FORMAT.md`'s encoding (Task 5 parameter #1). This case is what proves we replicate the original's charset, not just ASCII.

- [ ] **Step 5: Verify case B encoding matches FORMAT.md**

```bash
cd /home/projects/mpc
F="reference/format-fixtures/caseB-accented/expected/Café.m3u"
echo "Detected encoding:"; file -i "$F"
echo "Bytes for the é in Résumé (expect c3a9 if UTF-8, e9 if Latin-1/1252):"; xxd "$F" | grep -iE "c3 a9|e9" | head
```

Expected: the encoding shown matches FORMAT.md parameter #1 (e.g., `charset=utf-8` with `c3 a9`, or `charset=iso-8859-1` with a lone `e9`).

- [ ] **Step 6: Commit and push**

```bash
cd /home/projects/mpc
git add reference/format-fixtures/
git commit -m "test: add synthetic golden-master fixtures for the .m3u format"
git push origin main
```

Expected: push succeeds; both cases tracked.

---

## Definition of Done (Phase 0)

- [ ] Private repo `Joeputin100/cliplist` exists, `main` pushed, reference APK vendored under `reference/`.
- [ ] `decompile.yml` runs green in Actions and produces the `decompiled-reference` artifact; decompiled source is **not** committed.
- [ ] `reference/FORMAT.md` answers all 8 byte-format parameters with quoted evidence (CRLF confirmed).
- [ ] `reference/format-fixtures/` contains at least the ASCII and accented cases with verified CRLF + encoding.
- [ ] Ready to plan Phase 1 (`:core-format`) against the now-known format.

## Self-Review (completed by plan author)

- **Spec coverage:** Implements spec §7 (CI decompile → FORMAT.md → fixtures), §2 (the named export routines), §8 (all 8 format parameters), §17 (private repo, `decompile.yml`, decompiled source never committed). Phases 1–5 are explicitly out of scope and deferred until the format is known.
- **Placeholders:** The FORMAT.md/fixtures tasks are reverse-engineering deliverables; every step gives exact inspection commands, an exact output schema, and verification commands. The only intentionally-deferred values are the discovered bytes themselves — which is the point of the phase, with explicit "no blanks / flag anomalies" rules.
- **Consistency:** Paths are consistent throughout (`reference/mym3ucreator-2.1.1.apk`, `decompiled/jadx/sources/com/matt/mym3ucreator/...`, `reference/FORMAT.md`, `reference/format-fixtures/...`). Repo name `cliplist` used consistently.
