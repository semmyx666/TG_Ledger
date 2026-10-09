> Історичний опис: зміни 2.0 та актуальні обмеження див. UPDATE_2_UK.md.

# Test matrix

## Included executable tests (not executed in the delivery sandbox)

- Domain: decimal precision/validation/round-trip; six-digit PIN policy; lockout cap;
  trim/update; future-date rejection; currency separation; seven-day zero-fill;
  search-vs-summary behavior; idempotent delete/undo.
- Data JVM: real generated RSA signature verification and tamper rejection; empty-key
  fail-closed behavior; Unicode search and literal SQL wildcard escaping.
- Data instrumentation: Room CRUD, aggregates, Unicode search, currency filters,
  edit-vs-delete race protection, tombstone purge; real Android Keystore encrypted PIN
  persistence, attempt persistence, lockout and ciphertext tampering.
- Presentation JVM: initial lock/session invalidation, language preference propagation,
  and two-pass PIN enrollment with a coroutine test dispatcher.
- Compose instrumentation: locale changes within the same composition, accessible
  PIN controls, support clipboard state and contact only after user action.

Run the CI commands from README. Tests intentionally use real behavior and complete
in-memory fakes rather than `TODO` methods. Test PIN strings exist only in test sources.

## Additional physical/manual coverage

Use API 26 and API 36 as the baseline matrix; include a small phone, modern Pixel,
Samsung-class device, a non-Play emulator and a device with strong face unlock if
supported. Weak face unlock must offer the PIN fallback, not silently downgrade.

Billing must be exercised in a signed internal-track build with license testers.
Raw local unit tests cannot certify Play transactions, refunds, acknowledgement,
offer eligibility or biometric hardware. Follow the release checklist.

`tools/audit_project.py` is an independent offline check of syntax delimiters,
resource parity, architecture imports, manifest safety settings and the **exact SQL
queries extracted from LedgerDao**. It is not a Kotlin compiler, Room annotation
processor, instrumentation runner or substitute for this matrix.
