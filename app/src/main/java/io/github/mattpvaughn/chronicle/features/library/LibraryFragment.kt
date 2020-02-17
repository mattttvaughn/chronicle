package io.github.mattpvaughn.chronicle.features.library

import android.os.Bundle
import android.view.*
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.databinding.FragmentLibraryBinding

/** TODO: refactor search to reuse code from Library + Home fragments */
class LibraryFragment : Fragment() {

    companion object {
        fun newInstance() = LibraryFragment()
    }

    private lateinit var viewModel: LibraryViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val activity = activity!!

        val plexPrefsRepo = Injector.get().plexPrefs()
        val prefsRepo = Injector.get().prefsRepo()
        viewModel = LibraryViewModel(
            bookRepository = Injector.get().bookRepo(),
            trackRepository = Injector.get().trackRepo(),
            plexPrefsRepo = plexPrefsRepo,
            prefsRepo = prefsRepo,
            cachedFileManager = (activity as MainActivity).activityComponent.cachedFileManager()
        )

        val binding = FragmentLibraryBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.libraryGrid.adapter = AudiobookAdapter(true, object : AudiobookClick {
            override fun onClick(audiobook: Audiobook) {
                openAudiobookDetails(audiobook)
            }

        })
        binding.lifecycleOwner = this
        binding.searchResultsList.adapter = AudiobookSearchAdapter(object : AudiobookClick {
            override fun onClick(audiobook: Audiobook) {
                openAudiobookDetails(audiobook)
            }
        })

        activity.setSupportActionBar(binding.toolbar)

        return binding.root
    }

    private fun openAudiobookDetails(audiobook: Audiobook) {
        val navController = findNavController()
        // Ensure nav controller doesn't queue two actions if a navigation event occurs twice in
        // rapid succession
        if (navController.currentDestination?.id == R.id.nav_library) {
            val action = LibraryFragmentDirections.actionNavLibraryToAudiobookDetailsFragment(
                audiobook.isCached,
                audiobook.id
            )
            navController.navigate(action)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {

        inflater.inflate(R.menu.library_menu, menu)
        val searchView = menu.findItem(R.id.search).actionView as SearchView
        val searchItem = menu.findItem(R.id.search) as MenuItem
        val filterItem = menu.findItem(R.id.filter) as MenuItem
        val cacheItem = menu.findItem(R.id.download_all) as MenuItem

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                filterItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                cacheItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                viewModel.setSearchActive(true)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                filterItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                cacheItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                viewModel.setSearchActive(false)
                return true
            }
        })

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Do nothing
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText != null) {
                    viewModel.search(newText)
                }
                return true
            }

        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.filter -> Toast.makeText(context, "Not supported yet", LENGTH_SHORT).show()
            R.id.download_all -> viewModel.promptDownloadAll()
            R.id.search -> { /* this is handled by listeners set in onCreateOptionsMenu */
            }
            else -> throw NoWhenBranchMatchedException("Unknown menu item selected!")
        }
        return super.onOptionsItemSelected(item)
    }

    interface AudiobookClick {
        fun onClick(audiobook: Audiobook)
    }
}
