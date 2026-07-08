# Play Console Setup — Fill-in Cheat Sheet

This is a one-time walkthrough. Everything Google could possibly ask you is
answered below — you're transcribing, not composing. Work top to bottom.

**Already done for you (nothing to type):** the app's title, descriptions,
and store-listing text in 10 languages, plus the icon, feature graphic, and
screenshots, all upload themselves automatically the moment you finish
steps 1–3 below and hand this back to Claude. Your job on this page is only
the account setup (1–3) and the declaration forms Google requires a human
to click through (4–12) — there is no API for those.

---

## 1. Create the app

Play Console → **Create app**.

| Field | Enter |
|---|---|
| App name | `My Playlist Creator 2026` |
| Default language | `English (United States)` |
| App or game | **App** |
| Free or paid | **Free** |

Tick both declaration checkboxes at the bottom (Developer Program
Policies, US export laws) and hit **Create app**.

You do NOT need to type a package name anywhere — it gets set
automatically the first time a build arrives (that happens after Claude
tags the first release — see "Hand back to Claude" at the bottom).

---

## 2. Link the GCP project (API access)

Left sidebar → **Setup** → **API access**.

- If it already says **"This Play Console account is linked to the Google
  Cloud project `static-webbing-461904-c4`"** (or shows that project name
  anywhere on the page) — it's already linked, skip to step 3.
- If instead you see a **"Link project"** / **"Choose a project"**
  button: click it, pick `static-webbing-461904-c4` from the list, and
  confirm.

---

## 3. Invite the robot

Same **API access** page → find the service accounts section → **Manage
Play Console permissions** (or **Users and permissions** → **Invite new
users**).

| Field | Enter |
|---|---|
| Email address | `play-publisher@static-webbing-461904-c4.iam.gserviceaccount.com` |

Under **App permissions**, add this app (My Playlist Creator 2026) and
tick exactly these three:

- ☑ Release to testing tracks
- ☑ Manage testing tracks
- ☑ Manage store presence

Send the invite. (No email actually goes anywhere — it's a robot account;
the permissions apply the moment you save.)

---

### ⏸ CHECKPOINT — hand back to Claude now

Steps 1–3 are the only things that had to happen by human hand first.
Go tell Claude you've done them — see **"Hand back to Claude"** at the
bottom of this page. While Claude tags the first release, keep working
through steps 4–12 below; nothing below depends on the release existing.

---

## 4. Content rating

Left sidebar → **Setup** → **Content rating** → **Start questionnaire**.

| Field | Enter |
|---|---|
| Email address | `joeputin100@gmail.com` |
| Category | **Utility, Productivity, Communication, or Other** |

Every question after that (violence, sexuality, profanity, drugs,
gambling, hate speech/discrimination, user-generated content, shared
location, data sharing for rating purposes, etc.) — answer **No** to
all of them. This app just scans a folder and writes playlist files; it
doesn't do any of those things.

Expected result: **Everyone**. Submit.

---

## 5. Target audience

Left sidebar → **Setup** → **Target audience and content**.

- Age groups: tick **13 and over** only (do not tick any 12-and-under
  group).
- "Is your app designed to appeal to children?" **No.**

---

## 6. Data safety

Left sidebar → **Setup** → **Data safety** → **Start** (or **Manage**).

Overview:

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all user data encrypted in transit? | **Yes** |
| Do you provide a way to request data deletion? | **No** |

Declare data collected — add exactly these two entries:

| Data type | Collected | Purpose | Required | Shared with third parties |
|---|---|---|---|---|
| App info & performance → **Crash logs** | Yes | App functionality | Required (no opt-out) | No |
| App info & performance → **Diagnostics** | Yes | App functionality | Required (no opt-out) | No |

(This is Firebase Crashlytics. Firebase is a service provider processing
data on our behalf — Play's own definitions say that's not "sharing," so
answer "not shared" when asked.)

Everything else Google lists (location, contacts, photos, messages,
financial info, health, etc.) — leave unchecked / answer **No**. This app
doesn't collect it.

Submit.

---

## 7. App access

Left sidebar → **Setup** → **App access**.

Choose: **"All functionality is available without special access"**
(i.e. no login wall, no restricted areas — a reviewer can use the whole
app immediately after installing it).

---

## 8. Ads

Left sidebar → **Setup** → **Ads**.

**No, my app does not contain ads.**

---

## 9. A few more quick declarations

Left sidebar → **Setup** → **App content** — a few remaining one-question
forms live here. Each is a single dropdown; the app is a plain local
utility, so the answer to all of them is the "none of this applies" option:

| Form | Answer |
|---|---|
| News apps | **No, my app is not a news app** |
| Financial features | **None of the above** (selling isn't involved — this section means banking/loans/crypto) |
| Health | **No health features** |
| Government apps | **No** |

---

## 10. Privacy policy

Left sidebar → **Setup** → **Store settings** (or wherever the "Privacy
policy" field appears — Google moves this around).

**URL:** `https://joeputin100.github.io/cliplist/privacy-policy`

---

## 11. Store settings — category and contact

Left sidebar → **Setup** → **Store settings**.

| Field | Enter |
|---|---|
| App category | **Music & Audio** |
| Contact email | `joeputin100@gmail.com` |

(This is separate from the store-listing text Claude uploads automatically
— category and contact email are Console-only fields with no API, so this
is the one bit of "listing" info you still have to type by hand. Without
a category set here, the listing can't go live to testers even once
everything else is done.)

---

## 12. Closed testing — create the track and testers

Left sidebar → **Testing** → **Closed testing**.

If there's no track yet, click **Create track**. Use the default name —
if it's asking, this is the same track the app's build system calls
"alpha" internally; in the Console it's just labeled **Closed testing**.
(Claude's automated release lands here, so don't create a second,
differently-named closed track — use the first/default one.)

- **Testers** tab → add a new email list → paste in **12 or more**
  tester email addresses (anyone you want testing the app; Google
  requires at least 12 for a closed test, and they need to stay opted in
  for 14 continuous days before the app can go further).
- Save. The page will show a **join URL / opt-in link** — something like
  `https://play.google.com/apps/testing/com.cliplist.app`. Copy that
  link and send it to your testers; they open it, tap "Become a
  tester," and can then install the app from the Play Store like normal.
- **Save the opt-in URL somewhere** (a note, an email to yourself) — you
  and Claude will both want it again later.

You can do this step even before Claude's first release lands — the
track and tester list don't need a build attached yet.

---

## Hand back to Claude

**After steps 1–3** (app created, GCP project linked, robot invited),
tell Claude something like: *"I've created the app, linked the GCP
project, and invited the robot — go ahead and tag the first release."*

Claude will then:

1. Tag and push the first release (`git tag v1.0.0 && git push origin
   v1.0.0`), which triggers CI to build the signed AAB and upload it —
   along with the full 10-language store listing and all the graphics
   (icon, feature graphic, screenshots) — to Play Console automatically,
   landing as a **DRAFT** release on the Closed testing track.
2. Confirm the draft shows up correctly (right version, listing text in
   all 10 languages, graphics visible) and hand it back to you.

**Meanwhile, you finish steps 4–12** above (the declaration forms) — they
don't block the draft release, but Play won't let anything go live to
real testers until every one of those forms is complete.

**After that:** Google requires the closed test to run for **14
continuous days** with your testers opted in before the app is eligible
to move further. Once that clock has run out, tell Claude to *"flip the
release from draft to completed and promote it toward production"* —
Claude will handle that through the API; you won't need to come back to
this page.
