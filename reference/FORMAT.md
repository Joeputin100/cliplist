# Original `.m3u` byte format (Clip Sport "classic" profile)

Reverse-engineered from `com.matt.mym3ucreator` v2.1.1 (built 2016-07-30).
Source: `services/exportplaylist/ServiceExport.java`, class `AsyncGenerationSousListes`.

This is the **code path that matches our workflow** (`ecrireSousListes` → `AsyncGenerationSousListes`):
it walks folders recursively, gathers audio filenames per folder, sorts or shuffles them, then
writes one playlist co-located with each set of files. The other two code paths (`AsyncExport`,
`AsyncExportDocument`) handle single-playlist exports from explicit file/path lists and produce
slightly different path styles (relative-from-parent instead of bare); they are NOT the
target for our reimplementation.

This document is the **frozen specification** the new `:core-format` serializer must match
byte-for-byte. Do not change without also updating the golden fixtures in `format-fixtures/`.

---

## The 8 format parameters

| # | Parameter | Value |
|---|---|---|
| 1 | Character encoding | **UTF-8, no BOM** |
| 2 | Header lines | **None** — no `#EXTM3U`, no `#EXTINF`, no comment lines |
| 3 | Entry content | **Bare filename only** — `getName()` with no path prefix and no leading separator |
| 4 | Path separator in entries | **N/A** — bare filenames contain no separator |
| 5 | Line ending | **CRLF (`\r\n`)** after every entry, including the last |
| 6 | Trailing CRLF after last entry | **YES** — the loop appends `\r\n` to every entry unconditionally |
| 7 | Per-folder playlist filename | **`<FolderName>.m3u`** where `FolderName` is the exact DocumentFile folder name |
| 8 | Sort order (alphabetize=on) | **Java `Collections.sort(List<String>)`** — case-sensitive, Unicode code-point order (no Locale, no Collator) |

---

## Evidence (line-number references into `ServiceExport.java`)

### 1. Encoding — UTF-8, no BOM

`AsyncGenerationSousListes` writes via:
```java
// Line 254
outputStream.write((((String) it.next()) + "\r\n").getBytes());
```
`String.getBytes()` with no charset argument uses `Charset.defaultCharset()`.
On Android, `Charset.defaultCharset()` has always been **UTF-8** (the platform guarantees this,
unlike desktop JVMs which use the OS locale charset). No BOM is written — `getBytes()` emits
raw UTF-8 bytes without a byte-order mark.

Confirmed by the older `AsyncExport` code path (line 60):
```java
this.osw = new OutputStreamWriter(this.fos); // no charset arg → UTF-8 on Android
```
No explicit charset literal appears anywhere in the package (`grep` found none).

### 2. Header lines — None

No `#EXTM3U` or `#EXTINF` string appears anywhere in `ServiceExport.java` or the entire package
(`grep -rn "EXTM3U\|EXTINF"` found zero matches). The write loop (lines 251–255) iterates
directly over filenames — no header write before the loop, no metadata written per entry.

### 3 & 4. Entry content — Bare filename; no path separator

```java
// Lines 228–232: collect filenames by calling getName() on each DocumentFile
for (DocumentFile documentFile3 : listFiles) {
    if (documentFile3.isDirectory()) { ... }
    else if (fileFilterMusicFiles.isMusic(documentFile3.getName()) || ...) {
        arrayList.add(documentFile3.getName()); // ← just the filename, nothing else
    }
}
// Line 254: write each filename
outputStream.write((((String) it.next()) + "\r\n").getBytes());
```
Entries are `documentFile3.getName()` — the file's own name with extension, no parent path,
no leading `/`, no leading `./`. The playlist is created **in the same folder** (line 246):
```java
DocumentFile createFile = documentFile.createFile("", documentFile.getName() + ".m3u");
```
So the Clip Sport sees co-located entries: `Song Title.mp3` rather than `/Music/Rock/Song Title.mp3`.
This is the layout the device handles most reliably.

### 5 & 6. Line ending CRLF; trailing CRLF present

```java
// Lines 252–255
while (it.hasNext()) {
    outputStream.write((((String) it.next()) + "\r\n").getBytes()); // \r\n every time
}
// stream is closed immediately after — no extra bytes, but the last \r\n IS written
```
Every entry, including the last, gets `\r\n`. There is no special-casing of the final entry.
A 3-song playlist file therefore ends with `…Song C.mp3\r\n` (not `…Song C.mp3`).

This matches the known Clip Sport requirement: missing `\r` (i.e., Unix `\n` only) causes
the device to display the playlist as empty.

### 7. Playlist filename — `<FolderName>.m3u`

```java
// Line 246
DocumentFile createFile = documentFile.createFile("", documentFile.getName() + ".m3u");
```
The playlist file is created inside `documentFile` (the current folder) with the name
`<folder's own name>.m3u`. If the folder is named `Rock`, the playlist is `Rock.m3u` placed
inside the `Rock/` directory.

If a file named `<FolderName>.m3u` already exists (case-insensitive match), it is deleted first:
```java
// Lines 233–235
} else if (documentFile3.getName().equalsIgnoreCase(documentFile3.getParentFile().getName() + ".m3u")) {
    documentFile2 = documentFile3; // track it
}
// Lines 238–239
if (documentFile2 != null) {
    DocumentsContract.deleteDocument(this.mActivity.getContentResolver(), documentFile2.getUri());
}
```
This is a **replace** operation, not an append.

### 8. Sort order — Java natural String order, case-sensitive

```java
// Lines 241–244
if (this.ordreAlphabetique) {
    Collections.sort(arrayList); // List<String>, no Comparator → String.compareTo()
} else {
    Collections.shuffle(arrayList);
}
```
`Collections.sort(List<String>)` uses `String.compareTo()`, which compares by Unicode code
point — **case-sensitive** (uppercase `Z` (U+005A = 90) sorts before lowercase `a` (U+0061 = 97)).
No `Collator`, no `Locale`, no `CASE_INSENSITIVE_ORDER`. Digits sort before uppercase, uppercase
before lowercase, then extended characters by code point value.

---

## Audio extensions (from `FileFilterMusicFiles.java`)

The original recognises these music file extensions (all checked against lowercased filename):

```
.mp3  .ogg  .wav  .m4a  .flac  .wma  .ac3  .aac  .alac
```

It also recognises video files (`.avi .mkv .mp4 .wmv .webm .3gp .xvid .divx .mov .ogv .ts`)
because it was a general-purpose playlist tool; our reimplementation targets **audio only**
for the Clip Sport and drops video extensions.

**Differences from our spec's initial guess:** the original has `ac3` and `alac` but
lacks `oga` (Ogg audio), `opus`, and Audible (`aa`/`aax`). Our app will support the
superset: `mp3 ogg oga wav m4a aac alac flac wma ac3 opus aa aax` (configurable in Settings).

---

## Non-alphabetize (shuffle) behaviour

When alphabetize is **off**, the original calls `Collections.shuffle(arrayList)` — a true random
shuffle of the filenames. Our app replicates this for the shuffle option.

---

## Anomalies / notes

None. The code is straightforward and internally consistent. Both the File-API and SAF
code paths write the same byte sequence (CRLF, bare names, no header, UTF-8). No version
guards affect the write logic. No BOM, no metadata, no hidden header.

---

## Applying this to the new serializer

The `:core-format` serializer must:

1. Accept a list of `String` filenames and a `folderName: String`.
2. Optionally sort via `Collections.sort` semantics (Java natural order, case-sensitive).
3. For each filename: emit `filename.getBytes(StandardCharsets.UTF_8)` then `"\r\n".getBytes()`.
4. Write NO header bytes before the first entry.
5. The output stream is closed after the last entry; no flush-without-close issues.
6. Name the output file `"$folderName.m3u"` co-located with the music files.

This is deliberately minimal — a golden-master byte test is the acceptance criterion, not
this prose description.
