package io.github.mattpvaughn.chronicle.features.chooselibrary

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.plex.APP_NAME
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.data.plex.PlexRequestSingleton
import io.github.mattpvaughn.chronicle.databinding.ActivityChooseLibraryBinding


class ChooseLibraryActivity : AppCompatActivity() {

    private lateinit var viewModel: ChooseLibraryViewModel
    private lateinit var libraryAdapter: LibraryListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = DataBindingUtil.setContentView<ActivityChooseLibraryBinding>(
            this,
            R.layout.activity_choose_library
        )

        binding.lifecycleOwner = this

        val prefs: PlexPrefsRepo = Injector.get().plexPrefs()
        viewModel = ChooseLibraryViewModel(prefs)

        libraryAdapter = LibraryListAdapter(LibraryClickListener { library ->
            Log.i(APP_NAME, "Library name: $library")
            prefs.putLibrary(library)
            PlexRequestSingleton.libraryId = library.id
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        })
        binding.libraryList.adapter = libraryAdapter

        viewModel.libraries.observe(this, Observer<List<LibraryModel>> { libraries ->
            libraries?.apply {
                libraryAdapter.submitList(libraries)
            }
        })
        binding.chooseLibraryViewModel = viewModel
    }

    override fun onBackPressed() {
        Injector.get().plexPrefs().removeLibraryName()
        super.onBackPressed()
    }
}

class LibraryClickListener(val clickListener: (library: LibraryModel) -> Unit) {
    fun onClick(library: LibraryModel) = clickListener(library)
}
