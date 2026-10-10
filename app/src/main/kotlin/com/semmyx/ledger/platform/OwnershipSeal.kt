package com.semmyx.ledger.platform

/**
 * Copyright (c) 2026 Semmyx (semmyx666). All rights reserved. See LICENSE.
 *
 * Ownership seal: SHA-256("TG Ledger|semmyx666|semmyxofficial666@gmail.com|2026"), first 32 hex chars.
 * It is published in docs/OWNERSHIP.md and is present in every build (APK/AAB), so a copy of the app can be
 * traced back to this project even after it is renamed or re-signed.
 */
object OwnershipSeal {
    const val OWNER = "Semmyx (semmyx666)"
    const val ID = "11b3e1bb7fc072422cf2d25d258c65c8"

    /** Keeps the seal referenced so that shrinking/obfuscation can never strip it from release builds. */
    fun register() { System.setProperty("com.semmyx.ledger.seal", "$OWNER|$ID") }
}
