package io.github.mattpvaughn.chronicle.features.library

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.databinding.FragmentLibraryBinding
import io.github.mattpvaughn.chronicle.navigation.Navigator
import io.github.mattpvaughn.chronicle.views.FlowableRadioGroup
import io.github.mattpvaughn.chronicle.views.checkRadioButtonWithTag
import timber.log.Timber
import javax.inject.Inject

/** TODO: refactor search to reuse code from Library + Home fragments */
class LibraryFragment : Fragment() {

    companion object {
        fun newInstance() = LibraryFragment()
    }

    @Inject
    lateinit var viewModelFactory: LibraryViewModel.Factory

    private val viewModel: LibraryViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(LibraryViewModel::class.java)
    }

    @Inject
    lateinit var prefsRepo: PrefsRepo

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var plexConfig: PlexConfig

    override fun onAttach(context: Context) {
        (activity as MainActivity).activityComponent.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.i("Lib frag view create")
        val binding = FragmentLibraryBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.plexConfig = plexConfig
        binding.libraryGrid.adapter =
            AudiobookAdapter(prefsRepo.bookCoverStyle == "Square", true, object : AudiobookClick {
                override fun onClick(audiobook: Audiobook) {
                    openAudiobookDetails(audiobook)
                }
            })
        binding.libraryGrid.itemAnimator?.changeDuration = 0
        binding.lifecycleOwner = viewLifecycleOwner
        binding.searchResultsList.adapter = AudiobookSearchAdapter(object : AudiobookClick {
            override fun onClick(audiobook: Audiobook) {
                openAudiobookDetails(audiobook)
            }
        })

        binding.swipeToRefresh.setOnRefreshListener {
            viewModel.refreshData()
        }

        viewModel.isRefreshing.observe(viewLifecycleOwner, Observer {
            binding.swipeToRefresh.isRefreshing = it
        })

        viewModel.scrollToItem.observe(viewLifecycleOwner, Observer {
            if (!it.hasBeenHandled) {
                it.getContentIfNotHandled()
                binding.libraryGrid.postDelayed({
                    binding.libraryGrid.scrollToPosition(0)
                }, 250)
            }
        })

        binding.sortByOptions.checkRadioButtonWithTag(prefsRepo.bookSortKey)
        binding.sortByOptions.setOnCheckedChangeListener { group: FlowableRadioGroup, checkedId ->
            val key = group.findViewById<AppCompatRadioButton>(checkedId).tag as String
            prefsRepo.bookSortKey = key
        }

//        binding.viewByOptions.checkRadioButtonWithTag(prefsRepo.libraryViewTypeKey)
//        binding.viewByOptions.setOnCheckedChangeListener { group, checkedId ->
//            val key = group.findViewById<AppCompatRadioButton>(checkedId).tag as String
//            prefsRepo.libraryViewTypeKey = key
//        }

        viewModel.messageForUser.observe(viewLifecycleOwner, Observer {
            if (!it.hasBeenHandled) {
                Toast.makeText(context, it.getContentIfNotHandled(), LENGTH_SHORT).show()
            }
        })

        val behavior = (binding.filterView.layoutParams) as CoordinatorLayout.LayoutParams
        (behavior.behavior as BottomSheetBehavior).addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // ignore in-between states
                if (newState == STATE_EXPANDED || newState == STATE_HIDDEN) {
                    viewModel.setFilterMenuVisible(newState == STATE_EXPANDED)
                }
            }
        })


        viewModel.isFilterShown.observe(viewLifecycleOwner, Observer { isFilterShown ->
            Timber.i("Showing filter view: $isFilterShown")
            val filterBottomSheetState = if (isFilterShown) {
                STATE_EXPANDED
            } else {
                STATE_HIDDEN
            }

            val params = binding.filterView.layoutParams as CoordinatorLayout.LayoutParams
            val bottomSheetBehavior = params.behavior as BottomSheetBehavior
            bottomSheetBehavior.state = filterBottomSheetState
        })

        (activity as AppCompatActivity).setSupportActionBar(binding.toolbar)

        return binding.root
    }

    private fun openAudiobookDetails(audiobook: Audiobook) {
        navigator.showDetails(audiobook.id, audiobook.isCached)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.library_menu, menu)
        val searchView = menu.findItem(R.id.search).actionView as SearchView
        val searchItem = menu.findItem(R.id.search) as MenuItem
        val filterItem = menu.findItem(R.id.menu_filter) as MenuItem
        val cacheItem = menu.findItem(R.id.download_all) as MenuItem

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                filterItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                cacheItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                viewModel.setSearchActive(true)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                filterItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                cacheItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
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
            R.id.menu_filter -> viewModel.setFilterMenuVisible(
                viewModel.isFilterShown.value?.not() ?: false
            )
            R.id.download_all -> viewModel.promptDownloadAll()
            R.id.search -> {
            } // handled by listeners in onCreateView
            else -> throw NoWhenBranchMatchedException("Unknown menu item selected!")
        }
        return super.onOptionsItemSelected(item)
    }

    interface AudiobookClick {
        fun onClick(audiobook: Audiobook)
    }
}
