package dev.opencab.feature.roster

/**
 * Compile-time flags for roster work.
 *
 * JMRI roster import is safe to ship: `RL` is a server-pushed greeting list and acquisition by
 * roster id is standard WiThrottle 1.7+. The flag exists so a layout that advertises an
 * unexpected roster format can be supported by simply turning the affordance off; the rest of
 * the roster is unaffected.
 */
object RosterFeatureFlags {
    const val JMRI_ROSTER_IMPORT_ENABLED: Boolean = true
}
