# Play Store Publishing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tagged releases automatically upload a signed AAB + full 10-language store listing to the Play closed-testing (alpha) track; the user does the one-time Console setup from a pre-filled cheat-sheet.

**Architecture:** Gradle Play Publisher (GPP) on `:app` does uploads and listing sync, authenticated by a dedicated service-account JSON in a GitHub secret; the release job gains bundle+publish steps gated to tags. Listing text/graphics are repo files. A standalone `androidpublisher` Python script is the committed plan-B if GPP fights AGP 9.2.

**Tech Stack:** Gradle Play Publisher, Google Play Developer API v3 (androidpublisher), gcloud service accounts, GitHub Pages, Pillow + Vertex image gen (assets).

## Global Constraints

- Package `com.cliplist.app`; app name "My Playlist Creator 2026"; repo https://github.com/Joeputin100/cliplist; GCP project `static-webbing-461904-c4`.
- Track: **alpha** (Console "Closed testing"); first releases as **DRAFT** (Console forms incomplete until user finishes cheat-sheet).
- Secrets: new `PLAY_SERVICE_ACCOUNT_JSON` (SA key JSON, verbatim). Existing keystore/google-services secrets unchanged.
- Play locale codes: en-US, es-ES, fr-FR, de-DE, pt-BR, it-IT, ru-RU, ja-JP, ko-KR, zh-CN. Title ≤30 chars, short description ≤80, full ≤4000. All claims factual (crash reports exist; no ads/accounts/tracking).
- No local Android builds — compile gate is the push-triggered CI run; publishing runs only on tags/dispatch.
- Commit trailers as used throughout this repo (Co-Authored-By + Claude-Session).
- The FIRST real upload cannot run until the user creates the app in the Console and invites the SA (cheat-sheet steps 1–3) — everything else lands ready.

---

### Task 1: GitHub Pages privacy URL

- [ ] `gh api -X POST repos/Joeputin100/cliplist/pages -f "source[branch]=main" -f "source[path]=/docs" ` (409 = already enabled → `gh api repos/Joeputin100/cliplist/pages` to inspect).
- [ ] Poll until built (`gh api repos/Joeputin100/cliplist/pages --jq .status` → `built`, may take ~2 min), then probe BOTH `https://joeputin100.github.io/cliplist/privacy-policy.html` and `.../privacy-policy` with `curl -s -o /dev/null -w "%{http_code}"`; record which returns 200 (Jekyll renders `docs/privacy-policy.md` → `.html`; extensionless may 404 — that's fine).
- [ ] Report the working URL — Tasks 4 and 7 consume it as `PRIVACY_URL`. No commit (server-side config only).

### Task 2: Publisher service account + secret

- [ ] `gcloud iam service-accounts create play-publisher --display-name="Play publisher (cliplist CI)" --project=static-webbing-461904-c4` (ALREADY_EXISTS is fine).
- [ ] `gcloud iam service-accounts keys create "$RUNNER_TMP_OR_/tmp/play-pub-key.json" --iam-account=play-publisher@static-webbing-461904-c4.iam.gserviceaccount.com --project=static-webbing-461904-c4`
- [ ] `gh secret set PLAY_SERVICE_ACCOUNT_JSON < /tmp/play-pub-key.json && gh secret list | grep PLAY_` then `shred -u /tmp/play-pub-key.json`. Never print the key.
- [ ] Report the SA email (cheat-sheet consumes it). No repo commit.

### Task 3: Gradle — GPP plugin + tag-driven versionName

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`.

- [ ] Find the current GPP release: `gh api repos/Triplet/gradle-play-publisher/releases/latest --jq .tag_name` (org is `Triplet`; if 404 try `gh api repos/triplet-gradle/play-publisher/releases/latest`). Use that version below (shown as X.Y.Z).
- [ ] `libs.versions.toml`: under `[versions]` add `playPublisher = "X.Y.Z"`; under `[plugins]` add `play-publisher = { id = "com.github.triplet.play", version.ref = "playPublisher" }`.
- [ ] `app/build.gradle.kts` plugins block: add `alias(libs.plugins.play.publisher)`. Change `versionName = "1.0.0"` to `versionName = System.getenv("VERSION_NAME") ?: "1.0.0"`. Append at file scope:

```kotlin
play {
    // CI writes the key file and sets this env var; absent locally → publishing tasks
    // simply can't run, which is correct (no local publishing).
    System.getenv("PLAY_SA_KEY_FILE")?.let { serviceAccountCredentials.set(file(it)) }
    track.set("alpha")                     // Console name: Closed testing
    defaultToAppBundles.set(true)
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.DRAFT)
}
```

- [ ] Commit ("feat(play): Gradle Play Publisher wired to the closed track (draft)"), push, poll push CI to success (compile gate — assembleRelease still builds). **If configuration fails on CI with a GPP/AGP incompatibility, revert the plugin lines, keep versionName, note BLOCKED-to-fallback in the report** — Task 6 then uses the committed plan-B script instead of `publishBundle`.

### Task 4: Listing-as-code (10 locales)

**Files:** Create `app/src/main/play/listings/<locale>/{title,short-description,full-description}.txt` ×10, `app/src/main/play/release-notes/en-US/default.txt`, `app/src/main/play/contact-website.txt` (repo URL).

- [ ] en-US content (verbatim):
  - `title.txt`: `My Playlist Creator 2026`
  - `short-description.txt`: `Byte-perfect .m3u playlists for the SanDisk Clip Sport. One tap per folder.`
  - `full-description.txt`:

```
Your SanDisk Clip Sport needs .m3u playlists — this app writes them perfectly, one per folder, in one tap.

For a decade Clip Sport owners relied on My Playlist Creator by Matt Duss. It was abandoned and delisted. My Playlist Creator 2026 is its open-source revival: the same byte-exact playlist format, rebuilt from scratch for modern Android.

HOW IT WORKS
1. Pick your music folder or SD card
2. Scan and preview every playlist, rename and warning before anything is written
3. Generate — one .m3u per folder, in the Clip Sport's exact format

FEATURES
• Clean file names (optional): renames symbols and accents the player can't handle — Rock & Roll!.mp3 becomes Rock and Roll.mp3. Full preview first, nothing ever deleted.
• Real track durations read from your files; unreadable files are skipped, never deleted
• Optional folder.jpg cover art
• Safe eject: jumps straight to the SD card's Unmount screen
• 10 languages
• Private by design: no accounts, no ads, no tracking. Only anonymous crash reports ever leave your device.

Free software under GPLv3 — source code at https://github.com/Joeputin100/cliplist. Not affiliated with or endorsed by Matt Duss or SanDisk.
```

  - `release-notes/en-US/default.txt`: `First closed-testing release: scan a folder, preview, and write one byte-perfect .m3u playlist per folder. Clean file names, safe eject, 10 languages.`
- [ ] Translate title/short/full into the other 9 locales, matching the app's existing translation register in `localization/strings.json` (reuse its terminology; keep product terms + the GitHub URL untranslated; title may stay identical where natural). Enforce limits: `python3 - <<'EOF'` script that walks `app/src/main/play/listings/*/` asserting len(title)≤30, len(short)≤80, len(full)≤4000 — must print `all limits OK`.
- [ ] Commit ("feat(play): store listing as code — 10 locales + release notes"), push, CI green (resource-only, still compiles).

### Task 5: Listing graphics

**Files:** Create under `app/src/main/play/listings/en-US/graphics/`: `icon/1.png` (512×512), `feature-graphic/1.png` (1024×500), `phone-screenshots/{1,2,3}.png`.

- [ ] Icon: recreate crisp at 1024 from the 192px launcher via Vertex (per memory `image-generation-setup`: `genai.Client(vertexai=True, project="static-webbing-461904-c4", location="global")`, model `gemini-3.1-flash-image`, reference `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`, prompt "Recreate this exact app icon, identical design and colors, as a crisp high-resolution 1024×1024 square, full-bleed, no added text or border"), then Pillow-downscale to 512. Read the result to confirm identity match. Fallback if Vertex unavailable/mismatched: Pillow Lanczos 192→512 with `Image.LANCZOS`.
- [ ] Feature graphic: from `docs/readme-assets/hero-dark.png` (1600×497): scale to width 1024 (height ≈318), paste centered on a 1024×500 canvas filled `#0e1113`. Pillow one-liner; Read to verify.
- [ ] Screenshots: copy `docs/screenshots/{home,preview,results}.png` → `phone-screenshots/{1,2,3}.png`.
- [ ] Commit ("feat(play): listing graphics — icon, feature graphic, screenshots"), push, CI green.

### Task 6: Release-job publish steps (+ committed plan-B script)

**Files:** Modify `.github/workflows/build.yml` (the job that decodes `ANDROID_KEYSTORE_BASE64` and runs `:app:assembleRelease`); Create `tools/play_upload.py`.

- [ ] Add to that job, after the existing signing/env setup, keeping its keystore + google-services steps intact:

```yaml
      - name: Publish AAB to Play closed testing (tags only)
        if: startsWith(github.ref, 'refs/tags/')
        env:
          PLAY_SA_JSON: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}
          ANDROID_KEYSTORE_FILE: ${{ runner.temp }}/upload.jks
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
        run: |
          printf '%s' "$PLAY_SA_JSON" > "$RUNNER_TEMP/play-sa-key.json"
          export PLAY_SA_KEY_FILE="$RUNNER_TEMP/play-sa-key.json"
          export VERSION_NAME="${GITHUB_REF_NAME#v}"
          ./gradlew :app:publishBundle --no-daemon
```

  (Match the env names the existing assembleRelease step uses for the keystore — copy them exactly from that step; the keystore decode step must run before this one.) If Task 3 reported the GPP fallback, replace the `./gradlew :app:publishBundle` line with `./gradlew :app:bundleRelease --no-daemon && python3 tools/play_upload.py app/build/outputs/bundle/release/app-release.aab`.
- [ ] Create `tools/play_upload.py` (plan-B, committed either way):

```python
#!/usr/bin/env python3
"""Upload an AAB to the Play alpha track as a draft release (plan-B for GPP).
Auth: service-account JSON at $PLAY_SA_KEY_FILE. Usage: play_upload.py <aab-path>"""
import os, sys
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

PACKAGE = "com.cliplist.app"
creds = service_account.Credentials.from_service_account_file(
    os.environ["PLAY_SA_KEY_FILE"],
    scopes=["https://www.googleapis.com/auth/androidpublisher"])
pub = build("androidpublisher", "v3", credentials=creds)
edit = pub.edits().insert(packageName=PACKAGE).execute()
eid = edit["id"]
bundle = pub.edits().bundles().upload(
    packageName=PACKAGE, editId=eid,
    media_body=MediaFileUpload(sys.argv[1], mimetype="application/octet-stream",
                               chunksize=-1, resumable=True)).execute()
vc = bundle["versionCode"]
pub.edits().tracks().update(
    packageName=PACKAGE, editId=eid, track="alpha",
    body={"track": "alpha",
          "releases": [{"status": "draft", "versionCodes": [str(vc)]}]}).execute()
print(pub.edits().commit(packageName=PACKAGE, editId=eid).execute())
```

  (CI already has python3; add `pip install google-api-python-client google-auth` to the step only if the fallback path is active.)
- [ ] Commit ("ci(play): publish tagged AABs to the closed track"), push, CI green (the publish step is tag-gated and does not run on push).

### Task 7: Console cheat-sheet + handoff + first release

**Files:** Create `play-listing/console-cheat-sheet.md`.

- [ ] Write the cheat-sheet with these pre-filled sections (complete, fill-in style like pdfposter's): **(1) Create app** — name `My Playlist Creator 2026`, default language `English (United States)`, App, Free, accept declarations. **(2) API access** — Settings → API access: link project `static-webbing-461904-c4` if prompted (may already be linked). **(3) Invite the robot** — Users & permissions → Invite user → email `play-publisher@static-webbing-461904-c4.iam.gserviceaccount.com` → app-level permissions: Release to testing tracks, Manage testing tracks, Manage store presence. **(4) Content rating** — questionnaire: Utility category; no violence/sex/profanity/drugs/gambling/hate; no user-generated content; no data sharing for rating purposes; contact `joeputin100@gmail.com`; expected result Everyone. **(5) Target audience** — 13+ (not directed at children). **(6) Data safety** — Collects data: Yes → Crash logs + Diagnostics; purpose App functionality; collection required (no opt-out); encrypted in transit: Yes; deletion request: No in-app mechanism; not shared with third parties (Firebase = service provider). **(7) App access** — All functionality available without special access. **(8) Ads** — No. **(9) Privacy policy** — the Task 1 `PRIVACY_URL`. **(10) Closed testing** — create track "Closed testing", add 12+ tester emails, save the opt-in URL. **(11) Wait for the robot** — after (1)–(3), tell Claude to run the credential probe and tag the first release; the AAB and full listing arrive by themselves as a DRAFT release; then complete (4)–(10) and roll it out.
- [ ] Commit ("docs(play): Console cheat-sheet — one-time setup, everything pre-filled"), push.
- [ ] **CHECKPOINT (user):** hand over the cheat-sheet; user performs (1)–(3).
- [ ] Credential probe after user confirms: `PLAY_SA_KEY_FILE` unavailable locally — probe via a manual `gh workflow dispatch`? No: run locally with a fresh short-lived key? Simplest reliable probe that avoids re-downloading keys: `git tag v1.0.0 && git push origin v1.0.0` and watch the release job — the publish step IS the probe (a draft upload is harmless and is the goal). If it fails on auth, gather the error, fix (usually: SA not invited yet / API access unlinked), delete the tag remotely if the release run must be repeated (`git push --delete origin v1.0.0; git tag -d v1.0.0`), and retry with v1.0.1.
- [ ] Verify: Play Console shows the DRAFT release on Closed testing with the CI versionCode, listing text present in all 10 locales, graphics visible. Report to user with the opt-in URL location.

## Self-Review Notes

- Spec coverage: §1→Task 1, §2→Task 2, §3→Tasks 3+6, §4→Tasks 4+5, §5→Task 7, §6 verification→Task 7 checkpoint (probe-by-tag replaces a separate validate call — same evidence, fewer moving parts).
- GPP org/repo for the latest-release lookup has two candidate slugs (both listed) — implementer verifies which resolves.
- `releaseStatus DRAFT → COMPLETED` flip after Console completion is deliberately OUT of this plan (one-line follow-up once the 14-day test finishes; recorded in the cheat-sheet's last section).
- Fallback script and GPP never run together — Task 6 picks exactly one path based on Task 3's report.
