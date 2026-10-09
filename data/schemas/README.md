# Room schema history

The database starts at version 1. KSP exports its canonical schema into this directory
on the first Android build. Commit that generated JSON before the first production
release. Future schema changes must increase the version, add explicit migrations,
and run MigrationTestHelper against committed historical snapshots. Destructive
migration fallback is intentionally absent. No preexisting version is fabricated.
