package com.dangerfield.spyfall.api

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.crashlytics.android.BuildConfig
import com.dangerfield.spyfall.ui.joinGame.JoinGameError
import com.dangerfield.spyfall.models.*
import com.dangerfield.spyfall.ui.game.PlayAgainError
import com.dangerfield.spyfall.ui.game.StartGameError
import com.dangerfield.spyfall.ui.newGame.NewGameError
import com.dangerfield.spyfall.ui.newGame.PackDetailsError
import com.dangerfield.spyfall.ui.waiting.LeaveGameError
import com.dangerfield.spyfall.util.*
import com.dangerfield.spyfall.ui.waiting.NameChangeError
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class Repository(
    private var fireStoreService: GameService,
    private val sessionListenerService: SessionListenerService,
    private val preferencesHelper: PreferencesService,
    private val connectivityHelper: ConnectivityHelper = Connectivity(),
    private val dispatcher: CoroutineDispatcher = IO
) : GameRepository, SessionUpdater {

    private var createGameJob: Job? = null
    private var joinGameJob: Job? = null
    private var changeNameJob: Job? = null
    private var startGameJob: Job? = null

    override fun cancelCreateGame() = createGameJob?.cancel()
    override fun cancelJoinGame() = joinGameJob?.cancel()
    override fun cancelChangeName() = changeNameJob?.cancel()
    override fun cancelStartGame() = startGameJob?.cancel()


    /**
     * Job used to tie the coroutine context of cancellable operations to
     */

    ////////////////////////////////////////////////////////////////////////////////////
    /**
     * Events that more than one fragment must listen to are made private global
     */
    private var liveGame: MutableLiveData<Game> = MutableLiveData()
    private var sessionEndedEvent: MutableLiveData<Event<Unit>> = MutableLiveData()
    private var removeInactiveUserEvent = MutableLiveData<Event<Resource<Unit, Unit>>>()
    private var leaveGameEvent = MutableLiveData<Event<Resource<Unit, LeaveGameError>>>()

    /**
     * Provides VMs with access to global events
     */
    override fun getLiveGame(currentSession: Session): MutableLiveData<Game> {
        addListenerIfNewGame(currentSession)
        return liveGame
    }

    override fun getLeaveGameEvent() = leaveGameEvent
    override fun getSessionEnded() = sessionEndedEvent
    override fun getRemoveInactiveUserEvent() = removeInactiveUserEvent

    ////////////////////////////////////////////////////////////////////////////////////

    /**
     * Call back used by snapshot listener when game is null (was deleted on db)
     */
    override fun onSessionEnded() {
        cancelJobs()
        preferencesHelper.removeSavedSession()
        sessionEndedEvent.postValue(Event(Unit))
    }

    /**
     * Call back used by snapshot listener to update game
     */
    override fun onSessionGameUpdates(game: Game) = liveGame.postValue(game)

    /**
     * Set by Receiver to determine network connection
     */
    var hasNetworkConnection: Boolean = false


    /**
     * cancels all operations with context tied to job
     */
    override fun cancelJobs() = listOf(
        createGameJob,
        joinGameJob,
        changeNameJob,
        startGameJob
    ).forEach {
        it?.cancel()
    }


    /**
     * Creates a game node on firebase
     * adds listener to update live data for game
     * @Success Returns current session
     * @Error returns NewGameError
     */
    override fun createGame(
        username: String,
        timeLimit: Long,
        chosenPacks: List<String>
    ): LiveData<Resource<Session, NewGameError>> {
        val result = MutableLiveData<Resource<Session, NewGameError>>()

        createGameJob = CoroutineScope(dispatcher).launch {

            if (!connectivityHelper.isOnline()) {
                result.postValue(Resource.Error(error = NewGameError.NETWORK_ERROR))
            } else {

                try {
                    val accessCode = generateAccessCode()
                    val gameLocations = getGameLocations(chosenPacks as ArrayList<String>)
                    val game = Game(
                        gameLocations.random(),
                        chosenPacks as ArrayList<String>,
                        false,
                        arrayListOf(username),
                        arrayListOf(),
                        timeLimit,
                        gameLocations,
                        (System.currentTimeMillis() + millisecondsInTwoHours) / 1000
                    )

                    fireStoreService.setGame(accessCode, game)
                        .addOnSuccessListener {
                            val currentSession = Session(accessCode, username, game)
                            result.postValue(Resource.Success(currentSession))
                            preferencesHelper.saveSession(currentSession)
                        }.addOnFailureListener {
                            result.postValue(
                                Resource.Error(error = NewGameError.UNKNOWN_ERROR, exception = it)
                            )
                        }
                } catch (e: Exception) {
                    result.postValue(
                        Resource.Error(error = NewGameError.UNKNOWN_ERROR, exception = e)
                    )
                }
            }
        }

        return result
    }

    /**
     * Adds user name to games player list (no need for checks)
     * Adds listener to firebase to update game
     */
    override fun joinGame(
        accessCode: String,
        username: String
    ): LiveData<Event<Resource<Session, JoinGameError>>> {
        val result = MutableLiveData<Event<Resource<Session, JoinGameError>>>()

        joinGameJob = CoroutineScope(dispatcher).launch {

            if (!connectivityHelper.isOnline()) {
                result.postValue(Event(Resource.Error(error = JoinGameError.NETWORK_ERROR)))
            } else {
                try {
                    if(!accessCode.isAlphaNumeric) {
                        result postEvent Event(Resource.Error(error = JoinGameError.CODE_NOT_ALPHANUMERIC))
                        return@launch
                    }
                    fireStoreService.getGame(accessCode).addOnSuccessListener { game ->
                        if (game == null) {
                            result postEvent Event(Resource.Error(error = JoinGameError.GAME_DOES_NOT_EXIST))
                        } else {
                            when {
                                game.playerList.size >= 8 ->
                                    result postEvent Event(Resource.Error(error = JoinGameError.GAME_HAS_MAX_PLAYERS))

                                game.started ->
                                    result postEvent Event(Resource.Error(error = JoinGameError.GAME_HAS_STARTED))

                                game.playerList.contains(username) ->
                                    result postEvent Event(Resource.Error(error = JoinGameError.NAME_TAKEN))

                                else -> {
                                    addPlayer(username, accessCode)
                                        .addOnSuccessListener {
                                            game.playerList.add(username)
                                            val currentSession = Session(accessCode, username, game)
                                            result postEvent Event(Resource.Success(currentSession))
                                            preferencesHelper.saveSession(currentSession)

                                        }.addOnFailureListener {
                                            result postEvent
                                                    Event(
                                                        Resource.Error(
                                                            error = JoinGameError.COULD_NOT_JOIN,
                                                            exception = it
                                                        )
                                                    )
                                        }
                                }
                            }
                        }

                    }.addOnFailureListener {
                        result postEvent Event(Resource.Error(error = JoinGameError.UNKNOWN_ERROR))
                    }
                } catch (e: Exception) {
                    result postEvent Event(Resource.Error(error = JoinGameError.UNKNOWN_ERROR))
                }
            }
        }
        return result
    }

    /**
     * removes user name from games player list on db
     * posts to leave game event that both the waiting screen and game screen listen for
     */
    override fun leaveGame(currentSession: Session) {
        val numberOfPlayersBeforeLeaving = currentSession.copy().game.playerList.size
        fireStoreService.removePlayer(currentSession.accessCode, currentSession.currentUser)
            .addOnSuccessListener {
                sessionListenerService.removeListener()
                preferencesHelper.removeSavedSession()
                if (numberOfPlayersBeforeLeaving > 1) {
                    leaveGameEvent postEvent Event(Resource.Success(Unit))
                }
                //otherwise let the session ended trigger take care of user experience
            }.addOnFailureListener {
                leaveGameEvent postEvent Event(
                    Resource.Error(
                        error = LeaveGameError.UNKNOWN_ERROR,
                        exception = it
                    )
                )
            }
    }

    override fun removeInactiveUser(currentSession: Session) {
        fireStoreService.removePlayer(currentSession.accessCode, currentSession.currentUser)
            .addOnSuccessListener {
                sessionListenerService.removeListener()
                preferencesHelper.removeSavedSession()
                removeInactiveUserEvent postEvent Event(Resource.Success(Unit))
            }.addOnFailureListener {
                removeInactiveUserEvent postEvent
                        Event(Resource.Error(error = Unit, exception = it))
            }
    }

    override fun reassignRoles(currentSession: Session): MutableLiveData<Event<Resource<Unit, StartGameError>>> {
        val result = MutableLiveData<Event<Resource<Unit, StartGameError>>>()
        CoroutineScope(dispatcher).launch {
            try {
                val newLocation =
                    currentSession.game.locationList.filter { it != currentSession.game.chosenLocation }
                        .random()
                val newSession = currentSession.copy()
                newSession.game.chosenLocation = newLocation
                val roles = getRoles(newSession)
                assignRoles(roles.toMutableList(), newSession)
                fireStoreService.updateChosenLocation(currentSession.accessCode, newLocation)
                    .await()
                result.postValue(Event(Resource.Success(Unit)))
            } catch (e: Exception) {
                result.postValue(
                    Event(
                        Resource.Error(
                            error = StartGameError.Unknown,
                            exception = e
                        )
                    )
                )
            }
        }
        return result
    }


    /**
     * removes node on fire store
     * snapshot listener causes session to end
     */
    override fun endGame(currentSession: Session): MutableLiveData<Resource<Unit, Exception>> {
        val result = MutableLiveData<Resource<Unit, Exception>>()
        CoroutineScope(dispatcher).launch {
            try {
                fireStoreService.endGame(currentSession.accessCode).await()
                result.postValue(Resource.Success(Unit))
            } catch (e: Exception) {
                result.postValue(Resource.Error(error = e))
            } finally {
                preferencesHelper.removeSavedSession()
            }
        }

        return result
    }

    /**
     * assigns all players roles in the player objects list
     * increments statistics for games played
     */
    override fun startGame(currentSession: Session): MutableLiveData<Event<Resource<Unit, StartGameError>>> {
        val result = MutableLiveData<Event<Resource<Unit, StartGameError>>>()

        startGameJob = CoroutineScope(dispatcher).launch {
            fireStoreService.setStarted(currentSession.accessCode, true)
            try {
                val roles = getRoles(currentSession)
                assignRoles(roles.toMutableList(), currentSession)
                result.postValue(Event(Resource.Success(Unit)))
                incrementGamesPlayed()
            } catch (e: Exception) {
                result.postValue(
                    Event(
                        Resource.Error(
                            error = StartGameError.Unknown,
                            exception = e
                        )
                    )
                )
            }
        }
        return result
    }

    /**
     * Resets relevant game data to trigger play again action
     */
    override fun resetGame(currentSession: Session): MutableLiveData<Resource<Unit, PlayAgainError>> {
        val result = MutableLiveData<Resource<Unit, PlayAgainError>>()
        currentSession.game.let {
            val newLocation =
                currentSession.game.locationList.filter { location -> location != currentSession.game.chosenLocation }
                    .random()
            val newGame = Game(
                newLocation, it.chosenPacks, false,
                it.playerList, ArrayList(), it.timeLimit, it.locationList, it.expiration
            )
            fireStoreService.setGame(currentSession.accessCode, newGame).addOnSuccessListener {
                result.postValue(Resource.Success(Unit))
            }.addOnFailureListener { e ->
                result.postValue(Resource.Error(error = PlayAgainError.Unknown, exception = e))
            }
        }
        return result
    }

    /**
     * allows a user to update their username
     */
    override fun changeName(
        newName: String,
        currentSession: Session
    ): LiveData<Event<Resource<String, NameChangeError>>> {
        val result = MutableLiveData<Event<Resource<String, NameChangeError>>>()

        changeNameJob = CoroutineScope(dispatcher).launch {
            val copyPlayers = currentSession.game.playerList.toMutableList()
            val copyOldName = currentSession.currentUser
            val index = copyPlayers.indexOf(copyOldName)
            if (index == -1) {
                result.postValue(Event(Resource.Error(error = NameChangeError.UNKNOWN_ERROR)))
                return@launch
            }
            copyPlayers[index] = newName
            if (currentSession.game.started) {
                result.postValue(Event(Resource.Error(error = NameChangeError.GAME_STARTED)))
            } else {
                fireStoreService.setPlayerList(currentSession.accessCode, copyPlayers)
                    .addOnSuccessListener {
                        Log.d("Elijah", "Succesful name change")
                        val updatedSession =
                            Session(
                                accessCode = currentSession.accessCode,
                                previousUserName = copyOldName,
                                currentUser = newName,
                                game = currentSession.game
                            )
                        preferencesHelper.saveSession(updatedSession)
                        result.postValue(Event(Resource.Success(newName)))
                    }.addOnFailureListener {
                        result.postValue(
                            Event(
                                Resource.Error(
                                    error = NameChangeError.UNKNOWN_ERROR,
                                    exception = it
                                )
                            )
                        )
                    }
            }
        }

        return result
    }


    /**
     * fetches all packs and all locations associated with those packs.
     * Makes first element in each list the name of that pack
     * returns result
     */
    override fun getPacksDetails(): LiveData<Resource<List<List<String>>, PackDetailsError>> {
        val result = MutableLiveData<Resource<List<List<String>>, PackDetailsError>>()

        CoroutineScope(dispatcher).launch {

            if (!connectivityHelper.isOnline()) {
                result.postValue(Resource.Error(error = PackDetailsError.NETWORK_ERROR))
            } else {
                fireStoreService.getPackDetails().addOnSuccessListener {
                    if (it != null && packsDetailsIsFull(it)) {
                        result.postValue(Resource.Success(it))
                    } else {
                        result.postValue(Resource.Error(error = PackDetailsError.UNKNOWN_ERROR))
                    }
                }.addOnFailureListener {
                    result.postValue(
                        Resource.Error(error = PackDetailsError.UNKNOWN_ERROR, exception = it)
                    )
                }
            }
        }

        return result
    }

    override fun incrementGamesPlayed() {
        if (!BuildConfig.DEBUG) fireStoreService.incrementNumGamesPlayed()
    }

    override fun incrementAndroidPlayers() {
        if (!BuildConfig.DEBUG) fireStoreService.incrementNumAndroidPlayers()

    }

    override fun getPacks() = arrayListOf(
        GamePack(UIHelper.accentColors[0], "Standard", 1, "Standard Pack 1", false),
        GamePack(UIHelper.accentColors[1], "Standard", 2, "Standard Pack 2", false),
        GamePack(UIHelper.accentColors[2], "Special", 1, "Special Pack 1", false)
    )

    private fun clearGameLiveData() {
        liveGame = MutableLiveData()
    }

    private suspend fun generateAccessCode(): String {
        var newCode = UUID.randomUUID().toString().substring(0, 6).toLowerCase()
        while (fireStoreService.accessCodeExists(newCode).await()) {
            newCode = UUID.randomUUID().toString().substring(0, 6).toLowerCase()
        }
        return newCode
    }

    private suspend fun getRoles(currentSession: Session) =
        fireStoreService.findRolesForLocationInPacks(
            currentSession.game.chosenPacks,
            currentSession.game.chosenLocation
        ).await() ?: listOf()


    private suspend fun assignRoles(
        roles: MutableList<String>,
        session: Session
    ) {
        if (roles.isNullOrEmpty()) {
            throw Exception("Empty Roles in assign roles function")
        }

        val playerNames = session.game.playerList.shuffled()
        val playerObjectList = ArrayList<Player>()
        roles.shuffle()

        for (i in 0 until playerNames.size - 1) {
            playerObjectList.add(Player(roles[i], playerNames[i], 0))
        }

        playerObjectList.add(Player(Constants.GameFields.theSpyRole, playerNames.last(), 0))
        fireStoreService.setPlayerObjectsList(session.accessCode, playerObjectList.shuffled())
            .await()
    }

    private fun addPlayer(username: String, accessCode: String): Task<Void> =
        fireStoreService.addPlayer(accessCode, username)


    private suspend fun getGameLocations(chosenPacks: ArrayList<String>): ArrayList<String> {
        val locationList = arrayListOf<String>()
        //dictates the number locations we grab from each pack
        val numberFromEach = when (chosenPacks.size) {
            1 -> 14
            2 -> 7
            3 -> 5
            else -> 14
        }

        chosenPacks.forEach { pack ->
            val randomLocations =
                fireStoreService.getLocationsFromPack(pack, numberFromEach).await()
            if (randomLocations != null) {
                locationList.addAll(randomLocations)
            }
        }

        return locationList.take(14) as ArrayList<String>
    }

    private fun addListenerIfNewGame(currentSession: Session) {
        val creatingNewGame = !sessionListenerService.isListening()
        if (creatingNewGame) {
            clearGameLiveData()
            sessionListenerService.addListener(this, currentSession)
        }
    }

    private fun packsDetailsIsFull(list: List<List<String>>): Boolean {
        if (list.isEmpty() || list.size < 3) return false
        var result = true
        list.forEach { if (it.isEmpty()) result = false }
        return result
    }

    companion object {
        private const val millisecondsInTwoHours = 7200000
    }
}