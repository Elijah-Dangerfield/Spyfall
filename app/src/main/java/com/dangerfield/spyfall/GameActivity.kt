package com.dangerfield.spyfall

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.TableRow
import android.widget.TextView
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_game.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

import android.view.LayoutInflater
import android.view.View
import android.widget.TableLayout
import android.widget.Toast

import androidx.constraintlayout.widget.ConstraintLayout
import com.dangerfield.spyfall.WaitingActivity.WaitingGame
import com.dangerfield.spyfall.data.Game
import com.dangerfield.spyfall.data.Player
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_join_game.*


class GameActivity : AppCompatActivity() {

    var db = FirebaseFirestore.getInstance()

    lateinit var ACCESS_CODE: String
    val TAG = "Game Activity"
    var game: Game? = null
    lateinit var playerName: String
    lateinit var chosenLocation: String



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        ACCESS_CODE = intent.getStringExtra("ACCESS_CODE")
        //getGameFromFireBase()
        playerName = intent.getStringExtra("PLAYER_NAME")

        getGameData()
        listenForEnd()
    }

    fun listenForEnd(){
        var gameRef = db.collection("games").document(ACCESS_CODE)
        gameRef.addSnapshotListener(EventListener<DocumentSnapshot> { game, e ->
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
                return@EventListener
            }


            if (game != null && game.exists()) {
               if(game["isStarted"] == false){
                   //Start Intent
                   val intent = Intent(this,MainActivity::class.java)
                   startActivity(intent)
                   finish()
               }
            } else {
                Log.d(TAG, "Current data: null")
            }
        })
    }
    fun getGameData(){

        db.collection("games").document(ACCESS_CODE).get().addOnSuccessListener { game ->

            var gameObject =game.toObject(Game::class.java)

            if(gameObject != null) {
                var timeLimit = gameObject.timeLimit
                var playerList = gameObject.playerList
                var chosenPacks = gameObject.chosenPacks
                var currentPlayer = gameObject.playerObjectList.filter { it.username == playerName }
                if(currentPlayer.size ==1){
                    tv_role.text = currentPlayer[0].role
                    if(currentPlayer[0].role != "The Spy!"){
                        tv_chosen_location.text = "Location: ${gameObject?.chosenLocation}"
                    }else{
                        tv_chosen_location.text = "Figure out the location!"
                    }
                }
                //we might be able to load these last views on different threads
                loadLocationView(chosenPacks)
                loadViews(playerList, tbl_players)
                startTimer(timeLimit.toLong())
            }
        }

    }

    fun startTimer(timeLimit : Long){

        object : CountDownTimer((60000*timeLimit), 1000) {

            override fun onTick(millisUntilFinished: Long) {
                val text = String.format(
                    Locale.getDefault(), "%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60,
                    TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                )
                tv_timer.text = text
            }

            override fun onFinish() {
                tv_timer.text = "done!"
            }

        }.start()
    }

    fun loadLocationView(chosenPacks: ArrayList<String>){

        var locations = ArrayList<String>()

        db.collection(chosenPacks[0]).get().addOnSuccessListener {location ->
            location.documents.forEach { locations.add(it.id)}

            loadViews(locations,tbl_locations)
        }

    }

    fun loadViews(list: ArrayList<String>, table: TableLayout){

        //TODO considering we will have 20 or 30 every time we can probably avoid this
        var params = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT)
        params.setMargins(10,10,10,10)


        for( i in 0 until list.size step 2) {
            val row = TableRow(this).apply {
                layoutParams = params
            }
            for(j in 0..1) {
                val player_tv = LayoutInflater.from(this)
                    .inflate(R.layout.simple_card, row, false) as ConstraintLayout
                var tv = player_tv.getViewById(R.id.tv_in_game_player_name) as TextView
                if(i+j < list.size){
                    tv.text = list[i + j]
                    row.addView(player_tv)
                }

            }
            table.addView(row)
        }

    }
    fun endGame(view: View){
        //called when end button game is clicked
        //a listener is set checking for isStarted = false and if it does it goes back to the home screen
        //and calls finish()
        var gameRef = db.collection("games").document(ACCESS_CODE)
        gameRef.update("isStarted", false)

    }

    override fun onDestroy() {
        super.onDestroy()
        val gameRef = db.collection("games").document(ACCESS_CODE)
        gameRef.get().addOnSuccessListener { game ->
            if(game.exists()){
                gameRef.delete()
            }
        }
    }
}
