# v0.4: RealScale × Chunky × DH LOD persistence validation

## Why this QA stage exists
RealScale makes full Minecraft chunks. Chunky pre-generates full Minecraft
chunks, and Distant Horizons (DH) processes them into far-distance levels
of detail (LOD). Chunky's official FAQ warns that pre-generation can outrun
DH's LOD processing; therefore **server startup + loaded full chunk alone
cannot prove that far-distance LOD exists or will appear on the GPU**.

References:
- https://github.com/pop4959/Chunky/wiki/FAQ
- https://gitlab.com/distant-horizons-team/distant-horizons/-/wikis/2-frequently-asked-questions/1-general/General

## Read-only SQLite persistence audit
`qa/neoforge/audit_dh_lod_sqlite.py` searches under the user-selected
**root directory** for official DH `DistantHorizons.sqlite` databases.
For every inspected file it verifies whether the main SQLite database opens
read-only, whether SQLite's limited quick-check succeeds, and whether any
ordinary or likely LOD-data tables contain at least one record.

Run locally AFTER exiting Minecraft:
```bash
python3 qa/neoforge/audit_dh_lod_sqlite.py path/to/copy-of-your-save dh-lod-audit.json
```
**Never share the original world or unredacted logs to obtain this status.**
The JSON contains only file counts, size estimates, read/health booleans,
and generic states. It contains **no world names, usernames, coordinates,
save-directory paths, table names, or raw database rows**.

If a `-wal` sidecar exists, the tool reports that the immutable read-only
main database is an **incomplete snapshot**. Do not delete or detach
`*.sqlite-wal` or `*.sqlite-shm`. Always close the game cleanly for
the most meaningful audit. The script never issues SQL writes, WAL
checkpoints, schema migrations, VACUUM, repair, or world mutations.

### Interpreting the results
- `no_lod_database_found`: no official-named DH SQLite file was located
  inside the selected root. This may mean DH has not yet saved data or the
  user selected the wrong path; it is not proof that DH is malfunctioning.
- `sqlite_opened_no_table_records`: an inspectable main SQLite database
  contains no ordinary table rows in the checked snapshot.
- `sqlite_records_found_lod_payload_not_verified`: some ordinary tables
  have records, but recognizable LOD-table data was not confirmed.
- `candidate_lod_records_present`: a table whose name suggests DH
  render/full/LOD data contains at least one row. This is **partial storage
  evidence**, not a decoder-level check of DH LOD geometry.
- `incomplete_due_to_sqlite_wal`: WAL may contain the most recent data.
  The audit deliberately declines to declare the main database complete.
- `database_read_or_integrity_problem`: a database could not be inspected
  or the limited SQLite check did not report success.

In every state, `lod_payload_verified=false` and
`gpu_lod_visible=unverified`. To confirm client rendering, visually compare
DH with shaders OFF, with Sodium, with Iris shaderpack OFF, then a shaderpack
explicitly supporting DH.

## Integrated real NeoForge QA
The actual `qa/neoforge/rcon_worldgen_smoke.py` now runs this storage
audit *after a real game-server process exits*, not during its normal worldgen.
It writes a redacted `neoforge/build/dh-chunky-lod-storage.json` report;
existing GitHub Actions artifacts already include `dh-chunky-*.json`
and therefore capture this audit without changing the third-party mod JARs.

Two terrain presets and both bridge states are still tested. A lack of
persisted candidate LOD rows is reported as **uncertainty**, not
misrepresented as proof of a broken or working renderer. The existing v0.3
JAR and macOS-verified v0.1 remain unchanged.

## Safety and performance
- Only the dedicated QA program is changed; no runtime shader, biome,
  density, height or packet code is added.
- All sqlite handles are query-only and immutable.
- Single-row probes use SQL `LIMIT 1`; database and table scans are bounded.
- The DB schema is not assumed to match one specific DH release.
- Unit tests include no-file, corrupt-file, WAL, metadata-only,
  candidate-content, original bytes unchanged, and privacy cases.
