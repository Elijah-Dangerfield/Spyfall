package com.dangerfield.spyfall.game

import android.content.res.Configuration
import android.graphics.Paint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.android.synthetic.main.fragment_game.*
import java.util.*
import kotlin.collections.ArrayList
import androidx.activity.OnBackPressedCallback
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.crashlytics.android.Crashlytics
import com.dangerfield.spyfall.BuildConfig
import com.dangerfield.spyfall.R
import com.dangerfield.spyfall.models.Game
import com.dangerfield.spyfall.util.UIHelper
import com.google.android.gms.ads.AdRequest
import org.koin.android.viewmodel.ext.android.viewModel

class GameFragment : Fragment(R.layout.fragment_game) {

    private val gameViewModel: RealGameViewModel by viewModel()
    private lateinit var locationsAdapter: GameViewsAdapter
    private lateinit var playersAdapter: GameViewsAdapter
    private var changingTheme = false
    private val navController: NavController by lazy {
        NavHostFragment.findNavController(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //this does not get called again, set all the variables you want to keep(not reassign)

        gameViewModel.incrementAndroidPlayers()

        requireActivity().onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true){
                override fun handleOnBackPressed() {
                    //show alert when user presses back
                    UIHelper.customSimpleAlert(context!!,
                        getString(R.string.leave_game_title),
                        getString(R.string.leave_in_game_message),
                        getString(R.string.leave_action_positive),
                        {triggerEndGame()},getString(R.string.leave_action_negative),{}).show()
                }
            })
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        tv_game_role.maxTextSize = 96.0f

        if(BuildConfig.FLAVOR == "free") adView2.loadAd(AdRequest.Builder().build()) else adView2.visibility = View.GONE

        gameViewModel.liveGame?.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            //play again has been triggered
            if(!it.started && navController.currentDestination?.id == R.id.gameFragment){
                navController.popBackStack(R.id.waitingFragment, false)
            }

            if(it.started && navController.currentDestination?.id == R.id.gameFragment){
                //but right now it says: if the game has started, and im still on this screen, and something has changed..
                configurePlayerViews(it)
                configurePlayersAdapter(it.playerObjectList[0].username, it.playerList.shuffled() as ArrayList<String>)
                configureLocationsAdapter(it.locationList)
            }
        })

        gameViewModel.gameExists?.observe(viewLifecycleOwner, androidx.lifecycle.Observer {gameExists ->
            if(!gameExists && navController.currentDestination?.id == R.id.gameFragment){
                endGame()
            }
        } )

        gameViewModel.getTimeLeft().observe(viewLifecycleOwner, androidx.lifecycle.Observer {time ->
            tv_game_timer.text = time
            btn_play_again.visibility = if(time == RealGameViewModel.timeOver) View.VISIBLE else View.GONE
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        changeAccent()
        //we set the listeners once the view has actually been inflated
        btn_end_game.setOnClickListener {
            if(tv_game_timer.text.toString() == RealGameViewModel.timeOver) triggerEndGame()
            else{
                UIHelper.customSimpleAlert(context!!,
                    getString(R.string.end_game_title),
                    getString(R.string.end_game_message),
                    getString(R.string.end_game_positive_action), {triggerEndGame()},
                    getString(R.string.negative_action_standard),{}).show()
            }
        }

        btn_play_again.setOnClickListener{
           gameViewModel.resetGame()
        }
        btn_hide.paintFlags = Paint.UNDERLINE_TEXT_FLAG
        btn_hide.setOnClickListener{ hide()}

        tv_game_timer.text = String.format(
            Locale.getDefault(), "%d:%02d",
            gameViewModel.liveGame?.value?.timeLimit, 0
        )
    }

    override fun onResume() {
        super.onResume()

        if(!gameViewModel.hasStartedGame() && navController.currentDestination?.id == R.id.gameFragment) {
            //then user returned to the game but the game has been reset
            navController.popBackStack(R.id.waitingFragment, false)
        }
    }

    private fun hide(){
        if(tv_game_role.visibility == View.VISIBLE){
            tv_game_role.visibility = View.GONE
            tv_game_location.visibility = View.GONE
            view_role_card.visibility = View.GONE
            btn_hide.text = resources.getString(R.string.string_show)
        }else{
            tv_game_role.visibility = View.VISIBLE
            view_role_card.visibility = View.VISIBLE
            tv_game_location.visibility = View.VISIBLE
            btn_hide.text = resources.getString(R.string.string_hide)
        }
    }


    fun triggerEndGame(){ gameViewModel.triggerEndGame() }

    private fun endGame() {
        gameViewModel.resetTimer()
        navController.popBackStack(R.id.startFragment, false)
    }

    private fun configureLocationsAdapter(locations: ArrayList<String>){
        locationsAdapter = GameViewsAdapter(context!!, ArrayList(), null)
        rv_locations.apply{
            adapter = locationsAdapter
            layoutManager = GridLayoutManager(context, 2)
        }
        locationsAdapter.items = locations
    }

    private fun configurePlayersAdapter(firstPlayer: String, players: ArrayList<String>){
        rv_players.apply{
            layoutManager = GridLayoutManager(context, 2)
            playersAdapter = GameViewsAdapter(context, players, firstPlayer)
            adapter = playersAdapter
            setHasFixedSize(true)
        }
    }

    private fun configurePlayerViews(game: Game) {
       // we enforce that no two users have the same username
        val currentPlayer = (game.playerObjectList).find { it.username == gameViewModel.getCurrentUser() }
        if(currentPlayer == null){
            Crashlytics.log("could not find player \"${gameViewModel.getCurrentUser()}\" in player object list for game: ${gameViewModel.liveGame?.value}")
            navController.popBackStack(R.id.waitingFragment, false)
            Toast.makeText(context, "Something went wrong please check all players internet connection and try again", Toast.LENGTH_LONG).show()
        }

        currentPlayer?.let {
            tv_game_location.text = if(currentPlayer.role.toLowerCase().trim() == "the spy!"){
                "Figure out the location!"
            } else { "Location: ${game.chosenLocation}" }

            tv_game_role.text = "Role: ${currentPlayer.role}"
        }
    }

    private fun changeAccent(){
        btn_end_game.background.setTint(UIHelper.accentColor)
        btn_hide.background.setTint(UIHelper.accentColor)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        changingTheme = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if(!changingTheme) { gameViewModel.resetTimer() }
        changingTheme = false
    }
}
