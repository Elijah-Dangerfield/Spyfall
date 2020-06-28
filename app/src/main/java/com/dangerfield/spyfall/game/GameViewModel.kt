package com.dangerfield.spyfall.game

import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel;
import com.crashlytics.android.Crashlytics
import com.dangerfield.spyfall.models.Game
import com.dangerfield.spyfall.models.Player
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class GameViewModel : ViewModel() {

    //start off with a uuid but if its changed, so is the reference
    var ACCESS_CODE: String = UUID.randomUUID().toString().substring(0,6).toLowerCase()
        set(value){
            field = value
            gameRef = db.collection("games").document(value)
            //the idea here is that when we have a new access code, we change the get game upadtes gameRef
            getGameUpdates()
        }

    var hasNetworkConnection: MutableLiveData<Boolean> = MutableLiveData()
    var gameExists: MutableLiveData<Boolean> = MutableLiveData()
    var roles= ArrayList<String>()
    var gameObject: MutableLiveData<Game> = MutableLiveData()
    var db = FirebaseFirestore.getInstance()
    var gameRef = db.collection("games").document(ACCESS_CODE)
    private var timeLeft = MutableLiveData<String>()
    private lateinit var gameListener: ListenerRegistration
    var gameTimer : CountDownTimer? = null
    private val millisecondsInSixHours = 21600000

    lateinit var currentUser: String

    fun getGameUpdates(): MutableLiveData<Game> {

        // we need to make it such that when gameref changes, so does the game ref in this snap shot listener
        gameListener = gameRef.addSnapshotListener { game, error ->
            if (error != null) {
                Log.w("View Model", "Listen failed.", error)
                return@addSnapshotListener
            }
            if (game != null && game.exists()) {
                gameObject.value = game.toObject(Game::class.java)
                gameExists.value = true
                Log.d("View Model", "game =  NOT null for gameRef ${gameRef.path}")
            }else {

                Log.d("View Model", "game =  null for gameRef ${gameRef.path}")
                gameExists.value = false
            }
        }
        return gameObject
    }


    /*
    retrieves location list to show in game and sets that value on firebase
    also picks a random location from list to be the chosen location
     */
    fun getLocations(chosenPacks: ArrayList<String>, onComplete: ((locationList: List<String>) -> Unit)? = null) {
        val locationList = arrayListOf<String>()
        val numberFromEach = when(chosenPacks.size) {
            1 -> 14
            2 -> 7
            3 -> 5
            else -> 14
        }

        chosenPacks.forEach {pack ->
            if(locationList.size < 14) {
                db.collection("packs").document(pack).get().addOnSuccessListener {
                    val randomLocations =
                        (it.data?.toList()?.map { field -> field.first } ?: listOf()).shuffled().take(numberFromEach)
                    locationList.addAll(randomLocations)
                    if(locationList.size >= 14){
                        onComplete?.invoke(locationList.take(14))
                        return@addOnSuccessListener
                    }
                }
            }
        }
    }

    fun getRolesAndStartGame() {
        if(gameObject.value?.started == true) return
        gameRef.update("started", true)
        roles.clear()

        //find pack with chosen location
        gameObject.value?.let {
            it.chosenPacks.forEach {pack->
                db.collection("packs").document(pack).get().addOnSuccessListener {document ->
                    val documentLocation = document[gameObject.value!!.chosenLocation]
                    if(documentLocation != null) {
                        val mRoles = documentLocation as ArrayList<String>
                        roles.addAll(mRoles)
                        startGame()
                    }
                }
            }
        }
    }

    fun startGame() {

        //assignes all roles
        if(roles.isNullOrEmpty() or gameObject.value?.playerList.isNullOrEmpty()){ return }

            val playerNames = gameObject.value?.playerList?.shuffled()
            val playerObjectList = ArrayList<Player>()
            roles.shuffle()

            for (i in 0 until playerNames!!.size - 1) {
                //we can guarentee that i will never be out of index for roles as an 8 player max is enforced
                //i is between 0-6
                playerObjectList.add(Player(roles[i], playerNames[i], 0))
            }
            //so we shuffled players and roles and assigned everyone except one a role in order
            //now we assign the last one as the spy
            playerObjectList.add(Player("The Spy!", playerNames.last(), 0))

            //now push to database
            gameRef.update("playerObjectList", playerObjectList.shuffled()) //shuffled so that the last is not always the spy

            incrementGamesPlayed()
    }

    
    fun endGame(): Task<Void> {
        Crashlytics.log("End game function called in view model")
        gameListener.remove()
        //sets to false such that listener in game fragment can be triggered
        gameExists.value = false
        gameTimer?.cancel()
        gameTimer = null
        // delete the game on the server
       return gameRef.delete()
    }

    fun getNewAccessCode(onComplete: ((code: String) -> Unit)?) {
        val newCode = UUID.randomUUID().toString().substring(0, 6).toLowerCase()
        db.collection("games").document(newCode).get().addOnSuccessListener {
            if(it.exists()) {
                //then the document exists and thus the game code has already been taken
                getNewAccessCode(onComplete)
            }else{
                onComplete?.invoke(newCode)
            }
        }.addOnFailureListener {
            Log.d("Elijah", "GOT IN FAILURE")
        }
    }


    fun resetGame()  {
        Crashlytics.log("reset game function called in view model")

        // resets variables on firebase, which will update viewmodel
        val newLocation = gameObject.value!!.locationList.random()
        val newGame = Game(newLocation,gameObject.value!!.chosenPacks,false,
            gameObject.value!!.playerList, ArrayList(),gameObject.value!!.timeLimit,gameObject.value!!.locationList,
            gameObject.value!!.expiration)
        gameRef.set(newGame)
    }

    /**
     * add to create game: the location list retreive and upload.
     */
    fun createGame(game: Game, code: String, onComplete: (() -> Unit)? = null) {

        getLocations(chosenPacks = game.chosenPacks) {locationList ->
            game.chosenLocation = locationList.random()
            game.locationList = locationList as ArrayList<String>
            game.expiration = (System.currentTimeMillis() + millisecondsInSixHours)/1000
            gameObject.value = game
            ACCESS_CODE = code
            gameRef.set(game).addOnCompleteListener {
                onComplete?.invoke()
            }
        }
    }

    fun removePlayer(): Task<Void>? {
        if(!this::currentUser.isInitialized) return null
        Crashlytics.log("remove player function called in view model")
        //when a player leaves a game, you dont want them to hold onto the game data
        gameObject = MutableLiveData()
        gameListener.remove()
        //set to false such that when a user is not timeout removed to a game they already left
        gameExists.postValue(false)
        return gameRef.update("playerList", FieldValue.arrayRemove(currentUser)).addOnSuccessListener {
            if(gameObject.value?.playerList?.size == 0){ endGame()}
        }
    }

    fun addPlayer(player: String) = gameRef.update("playerList", FieldValue.arrayUnion(player))

    fun changeName(newName: String): Task<Void>? {
        val index = gameObject.value!!.playerList.indexOf(currentUser.trim())
        if (index == -1) {
            Crashlytics.log("Could not find name: \"${currentUser.trim()}\" in ${gameObject.value!!.playerList} to change to \"$newName\"")
            return null
        }

        Crashlytics.log("Successfully change name \"${currentUser.trim()}\" to \"${newName}\"")
        gameObject.value!!.playerList[index] = newName
        currentUser = newName
        return gameRef.update("playerList", gameObject.value!!.playerList)
    }

    fun incrementGamesPlayed(){
        //this function is used to keep stats about how many Android games have been played
        db.collection("stats")
            .document("game").update("num_games_played",FieldValue.increment(1))
    }

    fun incrementAndroidPlayers(){
        //this function is used to keep stats about how many Android games have been played
        db.collection("stats")
            .document("game").update("android_num_of_players",FieldValue.increment(1))
    }


    fun startGameTimer() {
        Crashlytics.log("Starting game timer from view model with game: ${gameObject.value}")
        gameObject.value?.timeLimit?.let {time ->
            gameTimer = object : CountDownTimer((60000*time), 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val text = String.format(
                        Locale.getDefault(), "%d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60,
                        TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                    )
                    Log.d("Timer", text)
                    timeLeft.postValue(text)
                }

                override fun onFinish() { timeLeft.postValue("0:00") }
            }.start()
        }

    }

    fun getTimeLeft() = timeLeft
}
