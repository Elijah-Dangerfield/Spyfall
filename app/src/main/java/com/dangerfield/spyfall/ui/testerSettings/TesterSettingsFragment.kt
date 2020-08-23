package com.dangerfield.spyfall.ui.testerSettings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.dangerfield.spyfall.R
import com.dangerfield.spyfall.api.Resource
import com.dangerfield.spyfall.util.DBCleaner
import com.dangerfield.spyfall.util.PreferencesService
import kotlinx.android.synthetic.main.fragment_tester_settings.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.coroutines.coroutineContext

class TesterSettingsFragment : Fragment(R.layout.fragment_tester_settings) {

    private val preferencesHelper: PreferencesService by inject()
    private val dbCleaner: DBCleaner by inject()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rg_test_db.check(if (preferencesHelper.getUseTestDbState()) R.id.rb_on else R.id.rb_off)

        rg_test_db.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_off -> preferencesHelper.setUseTestDbState(false)
                R.id.rb_on -> preferencesHelper.setUseTestDbState(true)
            }
        }

        btn_clean_db.setOnClickListener {
            showDbCleanLoading()
            dbCleaner.cleandb()
                .observe(viewLifecycleOwner, Observer {
                    when (it) {
                        is Resource.Success -> it.data?.let { s -> handleCleanDbMessage(s) }
                        is Resource.Error -> it.error?.let { e -> handleCleanDbMessage(e) }
                    }
                })

        }
    }

    private fun handleCleanDbMessage(it: String) {
        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        stopDbCleanLoading()
    }

    private fun showDbCleanLoading() {
        btn_clean_db.isClickable = false
        btn_clean_db.visibility = View.INVISIBLE
        pb_clean_db.visibility = View.VISIBLE
    }

    private fun stopDbCleanLoading() {
        btn_clean_db.isClickable = true
        btn_clean_db.visibility = View.VISIBLE
        pb_clean_db.visibility = View.INVISIBLE
    }
}