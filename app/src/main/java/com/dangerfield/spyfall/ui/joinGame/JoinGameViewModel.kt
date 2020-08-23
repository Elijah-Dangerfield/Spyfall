package com.dangerfield.spyfall.ui.joinGame

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import com.dangerfield.spyfall.R
import com.dangerfield.spyfall.api.GameRepository
import com.dangerfield.spyfall.api.Resource
import com.dangerfield.spyfall.models.Session
import com.dangerfield.spyfall.util.Event

enum class JoinGameError(val resId: Int? = null) {
    FIELD_ERROR(R.string.join_game_error_fields),
    GAME_DOES_NOT_EXIST(R.string.join_game_error_access_code),
    GAME_HAS_MAX_PLAYERS(R.string.join_game_error_max_players),
    GAME_HAS_STARTED(R.string.join_game_error_started_game),
    NAME_TAKEN(R.string.join_game_error_taken_name),
    NAME_CHARACTER_LIMIT(R.string.change_name_character_limit),
    COULD_NOT_JOIN(R.string.join_game_error_could_not_join),
    CODE_NOT_ALPHANUMERIC(R.string.join_game_error_code_alphanumberic),
    UNKNOWN_ERROR(R.string.unknown_error),
    NETWORK_ERROR
}

class JoinGameViewModel(private val repository: GameRepository) : ViewModel() {

    private val joinGameEvent = MediatorLiveData<Event<Resource<Session, JoinGameError>>>()
    fun getJoinGameEvent() = joinGameEvent

    fun triggerJoinGame(accessCode: String, username: String) {
        when {
            username.isEmpty() || accessCode.isEmpty() -> joinGameEvent.value =
                Event(Resource.Error(error = JoinGameError.FIELD_ERROR))
            username.length > 25 -> joinGameEvent.value =
                Event(Resource.Error(error = JoinGameError.NAME_CHARACTER_LIMIT))
            else -> {
                val repoResults = repository.joinGame(accessCode, username)
                joinGameEvent.addSource(repoResults) {
                    joinGameEvent.postValue(it)
                    joinGameEvent.removeSource(repoResults)
                }
            }
        }
    }

    fun cancelJoinGame() = repository.cancelJoinGame()
}