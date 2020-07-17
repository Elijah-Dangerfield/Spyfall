package com.dangerfield.spyfall.models

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

interface SessionListener {
    fun onSessionEnded()
    fun onGameUpdates(game: Game)
}

@Parcelize
class Session (
    val accessCode: String,
    var currentUser: String,
    var game : Game
) : Parcelable {

    override fun toString(): String {
        return "(accessCode: $accessCode, username: $currentUser)"
    }
}