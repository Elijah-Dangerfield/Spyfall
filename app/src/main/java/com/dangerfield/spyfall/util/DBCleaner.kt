package com.dangerfield.spyfall.util

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.dangerfield.spyfall.api.Constants
import com.dangerfield.spyfall.api.Resource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DBCleaner(private val db: FirebaseFirestore, private val constants: Constants) {

    fun cleandb(): LiveData<Resource<String, String>> {
        val result = MutableLiveData<Resource<String, String>>()
        val now = System.currentTimeMillis() / 1000
        db.collection(constants.games_prod)
            .whereLessThanOrEqualTo(Constants.GameFields.expiration, now).get()
            .addOnSuccessListener {
                CoroutineScope(IO).launch {
                    it.documents.toList().pmap { game ->
                        db.collection(constants.games_prod).document(game.id).delete()
                    }
                    val message = "Deleted ${it.documents.size} expired games"
                    result.postValue(Resource.Success(message))
                }
            }.addOnFailureListener {
                result.postValue(Resource.Error(error = "Failed to delete expired games"))
            }
        return result
    }

    suspend fun cleanExpiredGamesAndNoExpirationGames(): MutableLiveData<Resource<String, String>> {
        val result = MutableLiveData<Resource<String, String>>()
        val now = System.currentTimeMillis() / 1000
        var count = 0
        try {
            val it = db.collection(constants.games_prod).get().await()
            val list = it.documents.toList().filter {
                val exp = (it.get("expiration") as Long?)
                exp == null || exp.minus(now) <= 0
            }
            Log.d("Elijah", "Attempting to clean ${list.size} games")
            list.forEach { game ->
                count++
                db.collection(constants.games_prod).document(game.id).delete().await()
                Log.d("Elijah", "cleaned $count games")
            }

            val message = "Deleted $count games"
            result.postValue(Resource.Success(message))
        } catch (e: Exception) {
            result.postValue(Resource.Error(error = "Failed to delete games"))
        }
        return result
    }
}