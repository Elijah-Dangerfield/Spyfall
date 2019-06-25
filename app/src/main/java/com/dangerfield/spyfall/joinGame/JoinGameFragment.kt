package com.dangerfield.spyfall.joinGame

import android.graphics.PorterDuff
import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.dangerfield.spyfall.util.UIHelper
import com.dangerfield.spyfall.R
import com.dangerfield.spyfall.game.GameViewModel
import com.google.firebase.database.FirebaseDatabase
import kotlinx.android.synthetic.main.fragment_join_game.*
import kotlin.collections.ArrayList

class JoinGameFragment : Fragment() {

    private lateinit var viewModel: GameViewModel
    private lateinit var navController: NavController

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_join_game, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        changeAccent()

        btn_join_game_action.setOnClickListener { joinGameClick() }

        //listeners to hide keyboard when user clicks away
        tv_access_code.onFocusChangeListener = UIHelper.keyboardHider
        tv_username.onFocusChangeListener = UIHelper.keyboardHider
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        //scope the view model to the activity so that data can be shared in fragments
        viewModel = ViewModelProviders.of(activity!!).get(GameViewModel::class.java)
        navController = NavHostFragment.findNavController(this)
    }

    private fun joinGameClick(){

        if(!viewModel.hasNetworkConnection){
            UIHelper.customSimpleAlert(context!!, "Something went wrong",
                "We are sorry. Please check your internet connection and try again",
                "Okay",{},"",{}).show()
            return
        }

        val accessCode = tv_access_code.text.toString().trim()
        val userName = tv_username.text.toString().trim()

        if(userName.isEmpty() || accessCode.isEmpty()){
            Toast.makeText(context, "Please fill out both access code and user name", Toast.LENGTH_LONG).show()
            return
        }
        var connectionSuccess = false
        loadMode()

        Handler().postDelayed({
            //if it takes more than 5 seconds to connect, retry
            if(!connectionSuccess){
                UIHelper.errorDialog(context!!).show()
                enterMode()
                FirebaseDatabase.getInstance().purgeOutstandingWrites()
            }
        },8000)

        viewModel.db.collection("games").document(accessCode).get().addOnSuccessListener { game ->

            connectionSuccess = true

            if(game.exists()){
                val list = (game["playerList"] as ArrayList<String>)

                when {
                    list.size >= 8 ->  {
                        Toast.makeText(context, "Sorry, the max for a game is currently 8 players", Toast.LENGTH_LONG).show()
                        enterMode()
                    }
                    game["isStarted"]==true -> {
                        Toast.makeText(context, "Sorry, this game has been started", Toast.LENGTH_LONG).show()
                        enterMode()
                    }
                    list.contains(tv_username.text.toString().trim()) -> {
                        Toast.makeText(context, "Sorry, that name is taken by another player", Toast.LENGTH_LONG).show()
                        enterMode()
                    }
                    userName.length > 25 -> {
                        Toast.makeText(context, "please enter a name less than 25 characters", Toast.LENGTH_LONG).show()
                        enterMode()
                    }
                    else -> joinGame(withAccessCode = accessCode, asPlayer = userName)
                }

            }else{
                Toast.makeText(context, "Sorry, no game was found with that access code", Toast.LENGTH_LONG).show()
                enterMode()
            }
        }
    }

    private fun joinGame(withAccessCode: String, asPlayer: String){
        Handler().postDelayed({
            //if it takes more than 5 seconds to connect, retry
            if(navController.currentDestination?.id == R.id.join_fragment){
                UIHelper.errorDialog(context!!).show()
                enterMode()
                FirebaseDatabase.getInstance().purgeOutstandingWrites()
            }
        },8000)

        viewModel.ACCESS_CODE = withAccessCode
        viewModel.currentUser = asPlayer
        viewModel.addPlayer(asPlayer).addOnCompleteListener {
            enterMode()
            navController.navigate(R.id.action_joinGameFragment_to_waitingFragment)
        }
    }

    private fun changeAccent(){
        btn_join_game_action.background.setTint(UIHelper.accentColor)

        UIHelper.setCursorColor(tv_access_code,UIHelper.accentColor)
        UIHelper.setCursorColor(tv_username,UIHelper.accentColor)

        pb_join_game.indeterminateDrawable
            .setColorFilter(UIHelper.accentColor, PorterDuff.Mode.SRC_IN )

    }

    private fun loadMode(){
        pb_join_game.visibility = View.VISIBLE
        btn_join_game_action.isClickable = false
    }
    private fun enterMode(){
        pb_join_game.visibility = View.INVISIBLE
        btn_join_game_action.isClickable = true
    }

}
