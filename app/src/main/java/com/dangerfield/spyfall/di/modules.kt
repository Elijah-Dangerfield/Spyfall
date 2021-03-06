package com.dangerfield.spyfall.di

import com.dangerfield.spyfall.api.*
import com.dangerfield.spyfall.ui.game.GameViewModel
import com.dangerfield.spyfall.ui.joinGame.JoinGameViewModel
import com.dangerfield.spyfall.models.Session
import com.dangerfield.spyfall.ui.newGame.NewGameViewModel
import com.dangerfield.spyfall.ui.start.StartViewModel
import com.dangerfield.spyfall.ui.waiting.WaitingViewModel
import com.dangerfield.spyfall.util.*
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

val mainModule = module {

    single { Repository(get(), get(), get()) as GameRepository }
    single { RemoveUserTimer(get(), get())}
    single { FirebaseFirestore.getInstance()}
    single { PreferencesHelper(androidApplication()) as PreferencesService}

    //view models
    single { (currentSession: Session) -> WaitingViewModel(get(), currentSession)}
    single { (currentSession: Session) -> GameViewModel(get(), currentSession)}
    viewModel { JoinGameViewModel(get()) }
    viewModel { NewGameViewModel(get()) }
    viewModel { StartViewModel(get(), get()) }

    factory { SessionListenerHelper(get(), get()) as SessionListenerService }
    factory { FireStoreService(get(), get()) as GameService }
    factory { Constants(androidApplication(), get()) }
    factory { ReviewHelper(androidContext()) }
    factory { SavedSessionHelper(get(), get()) }
    factory { FeedbackHelper(get(), get()) }
    factory { DBCleaner(get(), get()) }

}