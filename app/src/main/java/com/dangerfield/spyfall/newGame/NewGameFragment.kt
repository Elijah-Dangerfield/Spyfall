package com.dangerfield.spyfall.newGame

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.crashlytics.android.Crashlytics
import com.dangerfield.spyfall.util.UIHelper
import com.dangerfield.spyfall.R
import com.dangerfield.spyfall.game.GameViewModel
import com.dangerfield.spyfall.models.Game
import com.dangerfield.spyfall.models.GamePack
import com.dangerfield.spyfall.util.Connectivity
import com.dangerfield.spyfall.util.addCharacterMax
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.firebase.database.FirebaseDatabase
import kotlinx.android.synthetic.main.fragment_new_game.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList

class NewGameFragment : Fragment() {

    private lateinit var viewModel: GameViewModel
    private lateinit var packsAdapter: PacksAdapter
    private var hasNetworkConnection = false
    lateinit var navController: NavController
    //private lateinit var mInterstitialAd: InterstitialAd


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_new_game, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //assigned in on create, as this does not need to be assigned again
        viewModel = ViewModelProviders.of(activity!!).get(GameViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //mInterstitialAd = InterstitialAd(context!!)
        //mInterstitialAd.adUnitId = getString(R.string.test_interstitial)
        //mInterstitialAd.loadAd(AdRequest.Builder().build())

        navController = NavHostFragment.findNavController(this)

        //observer updates our value of internet connection
        viewModel.hasNetworkConnection.observe(viewLifecycleOwner,Observer{ hasNetworkConnection->
            this.hasNetworkConnection = hasNetworkConnection
        })

        changeAccent()

        //reference views once the view has been created
        tv_new_game_name.onFocusChangeListener = UIHelper.keyboardHider
        tv_new_game_time.onFocusChangeListener = UIHelper.keyboardHider
        tv_new_game_time.addCharacterMax(2)
        tv_new_game_name.addCharacterMax(25)

        btn_create.setOnClickListener { createGame() }

        btn_packs.setOnClickListener{ showPacksDialog() }

        configurePacksAdapter()
    }

    private fun configurePacksAdapter(){

        var packs = mutableListOf<GamePack>()

        //TODO: make this dynamic by pulling pack names from firebase

        packs.add(GamePack(UIHelper.accentColors[0],"Standard",1,"Standard Pack 1",false))
        packs.add(GamePack(UIHelper.accentColors[1],"Standard",2,"Standard Pack 2",false))
        packs.add(GamePack(UIHelper.accentColors[2],"Special",1,"Special Pack 1",false))

        rv_packs.apply{
            layoutManager = GridLayoutManager(context, 3)
            packsAdapter = PacksAdapter(packs as ArrayList<GamePack>,context!!)
            adapter = packsAdapter
            setHasFixedSize(true)
        }
    }

    private fun createGame(){

        val timeLimit = tv_new_game_time.text.toString().trim()
        val playerName = tv_new_game_name.text.toString().trim()
        //these strings will be used for queries of the firestore database for which locations to include
        val chosenPacks = packsAdapter.packs.filter {it.isSelected}.map { it.queryString } as ArrayList<String>

        when {
            chosenPacks.isEmpty() -> {Toast.makeText(context,getString(R.string.new_game_error_select_pack), Toast.LENGTH_LONG).show()
                return}

            playerName.isEmpty() -> {Toast.makeText(context, getString(R.string.new_game_string_error_name), Toast.LENGTH_LONG).show()
            return}

            playerName.length > 25 -> {Toast.makeText(context, getString(R.string.change_name_character_limit), Toast.LENGTH_LONG).show()
                return}

            timeLimit.isEmpty() || timeLimit.toInt() > 10 || timeLimit.toInt() == 0 -> {
                Toast.makeText(context, getString(R.string.new_game_error_time_limit), Toast.LENGTH_LONG).show()
                return
            }
        }

        //if (mInterstitialAd.isLoaded) mInterstitialAd.show()

        viewModel.currentUser = playerName
        createGame(Game("",chosenPacks,false,
            mutableListOf(playerName) as ArrayList, ArrayList(),timeLimit.toLong(), arrayListOf(), 0))
    }

    private fun createGame(game: Game){
        var connected = false
        if(Connectivity.isOnline) {
            Crashlytics.log("Connectivity is offline in new game screen")
            Handler().postDelayed({
                if(!connected && this.isAdded){
                    //if we havent connected within 10 seconds, stop trying
                    FirebaseDatabase.getInstance().purgeOutstandingWrites()
                    context?.let {
                        UIHelper.errorDialog(it).show()
                        Handler(it.mainLooper).post {
                            enterMode()
                        }
                    }
                }
            }, 10000)

            loadMode()

            viewModel.getNewAccessCode {
                connected = true
                viewModel.createGame(game, it) {
                    Log.d("Elijah", "Called on complete")
                    if(navController.currentDestination?.id == R.id.newGameFragment) {
                        Crashlytics.log("Navigating from new game to waiting for player ${game.playerList[0]} with game $it")
                        navController.navigate(R.id.action_newGameFragment_to_waitingFragment)
                        enterMode()
                    }
                }
            }
        }else{
            Log.d("Elijah", "GOT OFFLINE")
            UIHelper.errorDialog(context!!).show()
            enterMode()
        }
    }

    fun loadMode(){
        pb_new_game.visibility = View.VISIBLE
        btn_create.text = ""
        btn_create.isClickable = false
        btn_packs.isClickable = false
    }
    fun enterMode(){
        pb_new_game.visibility = View.INVISIBLE
        btn_create.text = getString(R.string.string_btn_create)
        btn_create.isClickable = true
        btn_packs.isClickable = true
    }

    private fun changeAccent(){
        btn_create.background.setTint(UIHelper.accentColor)

        val drawable = resources.getDrawable(R.drawable.ic_rules).mutate()
        drawable.setColorFilter(UIHelper.accentColor, PorterDuff.Mode.SRC_ATOP)
        btn_packs.setImageDrawable(drawable)

        UIHelper.setCursorColor(tv_new_game_name,UIHelper.accentColor)

        UIHelper.setCursorColor(tv_new_game_time,UIHelper.accentColor)

        pb_packs.indeterminateDrawable
            .setColorFilter(UIHelper.accentColor, PorterDuff.Mode.SRC_IN )

        pb_new_game.indeterminateDrawable
            .setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN )

    }

    private fun showPacksDialog() {
        pb_packs.visibility = View.VISIBLE
        btn_packs.visibility = View.INVISIBLE
        btn_packs.isClickable = false
        //we also might consider a different structure for the backend where the packs are kept in on collection
        var connected = false
        Handler().postDelayed({
            if(!connected){
                //if we are not connected in 8 seconds, stop trying. Should still work with cache
                UIHelper.errorDialog(context!!).show()
                Handler(context!!.mainLooper).post {
                    pb_packs.visibility = View.INVISIBLE
                    btn_packs.visibility = View.VISIBLE
                    btn_packs.isClickable = true
                }
            }
        }, 8000)

        val list = mutableListOf<List<String>>()
        viewModel.db.collection("packs").get()
            .addOnSuccessListener { collection ->
                if (collection.isEmpty) return@addOnSuccessListener
                connected = true
                collection.documents.forEach { document ->
                //add to the list
                    val pack = listOf(document.id) + document.data!!.keys.toList()
                    list.add(pack)
                }

            UIHelper.packsDialog(context!!, list).show()
            pb_packs.visibility = View.INVISIBLE
            btn_packs.visibility = View.VISIBLE
            btn_packs.isClickable = true
        }.addOnFailureListener {

            }
    }
}

