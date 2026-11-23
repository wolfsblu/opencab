package dev.opencab.feature.roster

import android.net.Uri

const val ROSTER_LOCO_ID_ARG: String = "locoId"
const val ROSTER_ROUTE: String = "roster"
const val ROSTER_EDIT_ROUTE: String = "roster/edit/{$ROSTER_LOCO_ID_ARG}"
const val ROSTER_CREATE_ROUTE: String = "roster/new"

fun rosterEditRoute(locoId: String): String = "roster/edit/${Uri.encode(locoId)}"
