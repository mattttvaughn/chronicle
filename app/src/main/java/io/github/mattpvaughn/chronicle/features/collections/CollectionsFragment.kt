package io.github.mattpvaughn.chronicle.features.collections

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.BOOK_COVER_STYLE_SQUARE
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.VIEW_STYLE_COVER_GRID
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.VIEW_STYLE_DETAILS_LIST
import io.github.mattpvaughn.chronicle.data.local.PrefsRepo.Companion.VIEW_STYLE_TEXT_LIST
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.Collection
import io.github.mattpvaughn.chronicle.data.sources.plex.PlexConfig
import io.github.mattpvaughn.chronicle.databinding.FragmentCollectionsBinding
import io.github.mattpvaughn.chronicle.features.library.AudiobookSearchAdapter
import io.github.mattpvaughn.chronicle.features.library.LibraryFragment
import io.github.mattpvaughn.chronicle.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** TODO: refactor search to reuse code from Library + Home fragments */
class CollectionsFragment : Fragment() {

    companion object {
        fun newInstance() = CollectionsFragment()
    }

    @Inject
    lateinit var viewModelFactory: CollectionsViewModel.Factory

    private val viewModel: CollectionsViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(CollectionsViewModel::class.java)
    }

    @Inject
    lateinit var prefsRepo: PrefsRepo

    @Inject
    lateinit var navigator: Navigator

    @Inject
    lateinit var plexConfig: PlexConfig

    var adapter: CollectionsAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.i("Lib frag view create")
        val binding = FragmentCollectionsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        binding.plexConfig = plexConfig

        adapter = CollectionsAdapter(
            prefsRepo.libraryBookViewStyle,
            true,
            prefsRepo.bookCoverStyle == BOOK_COVER_STYLE_SQUARE,
            object : CollectionClick {
                override fun onClick(collection: Collection) {
                    openCollectionDetails(collection)
                }
            }).apply {
            stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }

        binding.collectionsGrid.adapter = adapter

        viewModel.collections.observe(viewLifecycleOwner) { collections ->
            // Adapter is always non-null between view creation and view destruction
            if (adapter == null) {
                return@observe
            }

            // If there are no previous books, submit normally
            if (adapter!!.currentList.isNullOrEmpty()) {
                Timber.i("Updating book list: no previous books")
                adapter!!.submitList(collections)
                return@observe
            }

            // Sometimes [books] will be the same as [adapter.currentList] so don't do any
            // submission/diffing if that's the case
            //
            // Check if the new list differs from the current. We really should be using a normal
            // RecyclerView.Adapter and not a ListAdapter for this, as ListAdapter only provides
            // access to an immutable copy of a list, not the list itself.
            //
            // This operation is worst case O(n), which is bad for users with huge libraries
            lifecycleScope.launch {
                val isNewList = withContext(Dispatchers.IO) {
                    val currentList = adapter?.currentList ?: return@withContext true
                    if (collections.size != currentList.size) {
                        Timber.i("Updating: different size!")
                        return@withContext true
                    }
                    // compare lists by id, faster than doing a full .equals() comparison
                    for (index in collections.indices) {
                        if (collections[index].id != currentList[index].id) {
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
                    adapter!!.submitList(null) { adapter?.submitList(collections) }
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
            binding.collectionsGrid.layoutManager = if (isGrid) {
                GridLayoutManager(requireContext(), 3)
            } else {
                LinearLayoutManager(requireContext())
            }
            adapter!!.viewStyle = style
        }
        binding.searchResultsList.adapter = AudiobookSearchAdapter(object : LibraryFragment.AudiobookClick {
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

        viewModel.messageForUser.observe(viewLifecycleOwner) {
            if (!it.hasBeenHandled) {
                Toast.makeText(context, it.getContentIfNotHandled(), LENGTH_SHORT).show()
            }
        }

        (activity as AppCompatActivity).setSupportActionBar(binding.toolbar)

        return binding.root
    }

    private fun openCollectionDetails(collection: Collection) {
        navigator.showCollectionDetails(collection.id)
    }

    private fun openAudiobookDetails(audiobook: Audiobook) {
        navigator.showDetails(audiobook.id, audiobook.title, audiobook.isCached)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.collections_menu, menu)
        val searchView = menu.findItem(R.id.search).actionView as SearchView
        val searchItem = menu.findItem(R.id.search) as MenuItem

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                viewModel.setSearchActive(true)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
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
            R.id.search -> {
            } // handled by listeners in onCreateView
            else -> throw NoWhenBranchMatchedException("Unknown menu item selected!")
        }
        return super.onOptionsItemSelected(item)
    }


    interface CollectionClick {
        fun onClick(collection: Collection)
    }
}
