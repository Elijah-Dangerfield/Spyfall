package com.dangerfield.spyfall.data

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class Player(var role: String,val username: String,var votes: Int): Parcelable {


constructor():this("","",0)

}