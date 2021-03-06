package com.dangerfield.spyfall.models

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Game(var chosenLocation: String,
                var chosenPacks: ArrayList<String>,
                var started: Boolean,
                var playerList: ArrayList<String>,
                var playerObjectList: ArrayList<Player>,
                var timeLimit: Long,
                var locationList: ArrayList<String>,
                var expiration: Long

) : Parcelable{

    constructor() : this("", ArrayList<String>(),false,ArrayList<String>(),ArrayList<Player>(),0.0.toLong(),ArrayList<String>(), 0)

    companion object {
        fun getEmptyGame(): Game {
            return Game("", ArrayList<String>(),false,ArrayList<String>(),ArrayList<Player>(),0L ,ArrayList<String>(), 0)
        }
    }
}