# Golden-master fixtures

Each case is a synthetic folder of music (invented filenames — zero proprietary content).
`input.json` describes the folder and options; `expected/<name>.m3u` is the **exact bytes**
the serializer must produce, constructed by hand from `../FORMAT.md`.

Phase 1 feeds `input.json` through `:core-format` and asserts the output equals
`expected/<name>.m3u` byte-for-byte. This is the automated stand-in for "works on the Clip Sport."

## `input.json` schema

```json
{
  "folderName": "Rock",
  "files": ["01 - Song A.mp3", "02 - Song B.mp3"],
  "options": { "alphabetize": true }
}
```

## Cases

| Case | Purpose |
|---|---|
| `caseA-ascii/` | Plain ASCII filenames, alphabetize=true. Core happy path. |
| `caseB-accented/` | Accented UTF-8 filenames, alphabetize=true. Proves encoding is UTF-8 not Latin-1. |

Add further cases here as edge conditions are identified during Phase 1 TDD.
