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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.BOOK_COVER_STYLE_SQUARE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.VIEW_STYLE_COVER_GRID
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.VIEW_STYLE_DETAILS_LIST
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.VIEW_STYLE_TEXT_LIST
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.databinding.FragmentLibraryBinding
import io.github.mattpvaughn.chronicle.navigation.Navigator
import io.github.mattpvaughn.chronicle.views.FlowableRadioGroup
import io.github.mattpvaughn.chronicle.views.checkRadioButtonWithTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    var adapter: AudiobookAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.i("Lib frag view create")
        val binding = FragmentLibraryBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        binding.plexConfig = plexConfig

        adapter = AudiobookAdapter(
            prefsRepo.libraryBookViewStyle,
            true,
            prefsRepo.bookCoverStyle == BOOK_COVER_STYLE_SQUARE,
            object : AudiobookClick {
                override fun onClick(audiobook: Audiobook) {
                    openAudiobookDetails(audiobook)
                }
            }).apply {
            stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }

        binding.libraryGrid.adapter = adapter

        viewModel.books.observe(viewLifecycleOwner) { books ->
            // Adapter is always non-null between view creation and view destruction
            checkNotNull(adapter) { "Adapter must not be null while view exists" }

            // If there are no previous books, submit normally
            if (adapter!!.currentList.isNullOrEmpty()) {
                Timber.i("Updating book list: no previous books")
                adapter!!.submitList(books)
                return@observe
            }

            // Sometimes [books] will be the same as [adapter.currentList] so don't do any
            // submission/diffing if that's the case

            // Check if the new list differs from the current. We really should be using a normal
            // RecyclerView.Adapter and not a ListAdapter for this, as ListAdapter only provides
            // access to an immutable copy of a list, not the list itself.
            //
            // This operation is worst case O(n), which is bad for users with big libraries
            lifecycleScope.launch {
                val isNewList = withContext(Dispatchers.IO) {
                    val currentList = adapter?.currentList ?: return@withContext true
                    if (books.size != currentList.size) {
                        Timber.i("Updating: different size!")
                        return@withContext true
                    }
                    // check if lists are in the same order, faster than doing a full .equals()
                    // comparison
                    for (index in books.indices) {
                        if (books[index].id != currentList[index].id) {
                            Timber.i("Updating: different ids!")
                            return@withContext true
                        }
                    }
                    return@withContext false
                }
                if (isNewList) {
                    // submit an empty list to force a scroll-to-top, then when it is done, submit
                    // the real list
                    Timber.i("Updating book list: scroll to top")
                    adapter!!.submitList(null) { adapter?.submitList(books) }
                }
            }
        }

        plexConfig.isConnected.observe(viewLifecycleOwner) { isConnected ->
            adapter?.setServerConnected(isConnected)
        }

        viewModel.viewStyle.observe(viewLifecycleOwner) { style ->
            Timber.i("View style is: $style")
            val isGrid = when (style) {
                VIEW_STYLE_COVER_GRID -> true
                VIEW_STYLE_DETAILS_LIST, VIEW_STYLE_TEXT_LIST -> false
                else -> throw IllegalStateException("Unknown view style")
            }
            binding.libraryGrid.layoutManager = if (isGrid) {
                GridLayoutManager(requireContext(), 3)
            } else {
                LinearLayoutManager(requireContext())
            }
            adapter!!.viewStyle = style
        }
        binding.searchResultsList.adapter = AudiobookSearchAdapter(object : AudiobookClick {
            override fun onClick(audiobook: Audiobook) {
                openAudiobookDetails(audiobook)
            }
        })

        binding.swipeToRefresh.setOnRefreshListener {
            viewModel.refreshData()
        }

        viewModel.isRefreshing.observe(viewLifecycleOwner) {
            binding.swipeToRefresh.isRefreshing = it
        }

        binding.sortByOptions.checkRadioButtonWithTag(prefsRepo.bookSortKey)
        binding.sortByOptions.setOnCheckedChangeListener { group: FlowableRadioGroup, checkedId ->
            val key = group.findViewById<AppCompatRadioButton>(checkedId).tag as String
            prefsRepo.bookSortKey = key
        }

        binding.viewStyles.checkRadioButtonWithTag(prefsRepo.libraryBookViewStyle)
        binding.viewStyles.setOnCheckedChangeListener { group: FlowableRadioGroup, checkedId ->
            val key = group.findViewById<AppCompatRadioButton>(checkedId).tag as String
            prefsRepo.libraryBookViewStyle = key
        }

        viewModel.messageForUser.observe(viewLifecycleOwner) {
            if (!it.hasBeenHandled) {
                Toast.makeText(context, it.getContentIfNotHandled(), LENGTH_SHORT).show()
            }
        }

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

        viewModel.isFilterShown.observe(viewLifecycleOwner) { isFilterShown ->
            Timber.i("Showing filter view: $isFilterShown")
            val filterBottomSheetState = if (isFilterShown) {
                STATE_EXPANDED
            } else {
                STATE_HIDDEN
            }

            val params = binding.filterView.layoutParams as CoordinatorLayout.LayoutParams
            val bottomSheetBehavior = params.behavior as BottomSheetBehavior
            bottomSheetBehavior.state = filterBottomSheetState
        }

        (activity as AppCompatActivity).setSupportActionBar(binding.toolbar)

        return binding.root
    }

    private fun openAudiobookDetails(audiobook: Audiobook) {
        navigator.showDetails(audiobook.id, audiobook.title, audiobook.isCached)
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

    override fun onAttach(context: Context) {
        (activity as MainActivity).activityComponent!!.inject(this)
        super.onAttach(context)
        Timber.i("Reattached!")
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
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
