package com.dangerfield.spyfall.util

import com.dangerfield.spyfall.api.Constants
import com.dangerfield.spyfall.models.Session
import com.dangerfield.spyfall.models.Game
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration


class SessionListenerHelper(
    private val constants: Constants,
    private val db: FirebaseFirestore
) : SessionListenerService {
    private var gameListener: ListenerRegistration? = null

    override fun isListening() = gameListener != null

    override fun removeListener() {
        gameListener?.remove()
        gameListener = null
    }

    override fun addListener(
        sessionUpdater: SessionUpdater,
        session: Session
    ) {
        val gameRef: DocumentReference = db.collection(constants.games).document(session.accessCode)
        gameListener = gameRef.addSnapshotListener { result, error ->
            if (error != null) return@addSnapshotListener

            if (result != null && result.exists()) {
                val updatedGame = result.toObject(Game::class.java) ?: return@addSnapshotListener
                val noUsersInGame = updatedGame.playerList.size == 0
                if (noUsersInGame) {
                    gameRef.delete() // will cause listener to endSession() when updated
                } else {
                    updateSession(updatedGame, sessionUpdater)
                }
            } else {
                endSession(sessionUpdater) // game ref has been deleted
            }
        }
    }

    private fun endSession(sessionUpdater: SessionUpdater) {
        removeListener()
        sessionUpdater.onSessionEnded()
    }

    private fun updateSession(game: Game, sessionUpdater: SessionUpdater) {
        sessionUpdater.onSessionGameUpdates(game)
    }
}