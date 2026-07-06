# Reference format material

- **`FORMAT.md`** — the frozen, byte-exact `.m3u` format the serializer must produce
  (CRLF line endings, ordering, header rules). Derived by studying the output of the
  original *My Playlist Creator* (`com.matt.mym3ucreator`) by Matt Duss, for
  interoperability with the SanDisk Clip Sport.
- **`format-fixtures/`** — golden input/output pairs replayed by `:core-format` tests.

The original app itself is not distributed here.
