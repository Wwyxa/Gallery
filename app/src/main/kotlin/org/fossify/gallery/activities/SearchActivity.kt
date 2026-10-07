package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databinding.ActivitySearchBinding
import org.fossify.gallery.extensions.*
import org.fossify.gallery.helpers.CURRENT_SEARCH_QUERY
import org.fossify.gallery.helpers.GridSpacingItemDecoration
import org.fossify.gallery.helpers.IS_FROM_SEARCH
import org.fossify.gallery.helpers.MOSAIC_TOTAL_SPANS
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.SEARCH_KEYWORD
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.VIDEO_PLAYER_APP
import org.fossify.gallery.helpers.VIDEO_PLAYER_SYSTEM
import org.fossify.gallery.helpers.VIEW_TYPE_MOSAIC
import org.fossify.gallery.helpers.VIEW_TYPE_WATERFALL
import org.fossify.gallery.interfaces.MediaOperationsListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import java.io.File

class SearchActivity : SimpleActivity(), MediaOperationsListener {
    override var isSearchBarEnabled = true
    
    private var mLastSearchedText = ""

    private var mCurrAsyncTask: GetMediaAsynctask? = null
    private var mAllMedia = ArrayList<ThumbnailItem>()

    private val binding by viewBinding(ActivitySearchBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        mLastSearchedText = intent.getStringExtra(CURRENT_SEARCH_QUERY)
            ?: intent.data?.getQueryParameter(SEARCH_KEYWORD).orEmpty()
        setupOptionsMenu()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.searchMenu),
            padBottomImeAndSystem = listOf(binding.searchGrid)
        )
        binding.searchEmptyTextPlaceholder.setTextColor(getProperTextColor())
        getAllMedia()
        binding.searchFastscroller.updateColors(getProperPrimaryColor())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val keyword = intent.data?.getQueryParameter(SEARCH_KEYWORD).orEmpty()
        if (keyword.isNotEmpty()) {
            mLastSearchedText = keyword
            binding.searchMenu.binding.topToolbarSearch.apply {
                setText(keyword)
                setSelection(keyword.length)
            }
            // setting an identical text does not fire the text watcher, re-run the search explicitly
            textChanged(keyword)
        }
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()
    }

    override fun onDestroy() {
        super.onDestroy()
        mCurrAsyncTask?.stopFetching()
    }

    override fun onBackPressedCompat(): Boolean {
        if (isTaskRoot) {
            // deep links can start this activity as the task root, back should reopen the main screen instead of exiting
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return true
        }
        return false
    }

    private fun setupOptionsMenu() {
        // the query must be set before setupMenu attaches the text watcher, otherwise it would
        // trigger a search against the not yet loaded media
        if (mLastSearchedText.isNotEmpty()) {
            binding.searchMenu.binding.topToolbarSearch.apply {
                setText(mLastSearchedText)
                setSelection(mLastSearchedText.length)
            }
        }

        binding.searchMenu.requireToolbar().inflateMenu(R.menu.menu_search)
        binding.searchMenu.toggleHideOnScroll(true)
        binding.searchMenu.setupMenu()
        binding.searchMenu.toggleForceArrowBackIcon(true)
        binding.searchMenu.focusView()
        binding.searchMenu.updateHintText(getString(org.fossify.commons.R.string.search_files))

        binding.searchMenu.onNavigateBackClickListener = {
            if (binding.searchMenu.getCurrentQuery().isEmpty()) {
                // route through the back callback so the task-root case above applies too
                onBackPressedDispatcher.onBackPressed()
            } else {
                binding.searchMenu.closeSearch()
            }
        }

        binding.searchMenu.onSearchTextChangedListener = { text ->
            mLastSearchedText = text
            textChanged(text)
        }

        binding.searchMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.toggle_filename -> toggleFilenameVisibility()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateMenuColors() {
        binding.searchMenu.updateColors()
    }

    private fun textChanged(text: String) {
        ensureBackgroundThread {
            try {
                val filtered = mAllMedia.filter { it is Medium && it.name.contains(text, true) } as ArrayList
                filtered.sortBy { it is Medium && !it.name.startsWith(text, true) }
                val grouped = MediaFetcher(applicationContext).groupMedia(filtered as ArrayList<Medium>, "")
                runOnUiThread {
                    if (grouped.isEmpty()) {
                        binding.searchEmptyTextPlaceholder.text = getString(org.fossify.commons.R.string.no_items_found)
                        binding.searchEmptyTextPlaceholder.beVisible()
                    } else {
                        binding.searchEmptyTextPlaceholder.beGone()
                    }

                    handleGridSpacing(grouped)
                    getMediaAdapter()?.updateMedia(grouped)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun setupAdapter() {
        val currAdapter = binding.searchGrid.adapter
        if (currAdapter == null) {
            MediaAdapter(this, mAllMedia, this, false, false, "", binding.searchGrid) {
                if (it is Medium) {
                    itemClicked(it.path)
                }
            }.apply {
                binding.searchGrid.adapter = this
            }
            setupLayoutManager()
            handleGridSpacing(mAllMedia)
            if (mLastSearchedText.isNotEmpty()) {
                textChanged(mLastSearchedText)
            }
        } else if (mLastSearchedText.isEmpty()) {
            (currAdapter as MediaAdapter).updateMedia(mAllMedia)
            handleGridSpacing(mAllMedia)
        } else {
            textChanged(mLastSearchedText)
        }

        setupScrollDirection()
    }

    private fun handleGridSpacing(media: ArrayList<ThumbnailItem>) {
        val viewType = config.getFolderViewType(SHOW_ALL)
        if (viewType == VIEW_TYPE_GRID) {
            if (binding.searchGrid.itemDecorationCount > 0) {
                binding.searchGrid.removeItemDecorationAt(0)
            }

            val spanCount = config.mediaColumnCnt
            val spacing = config.thumbnailSpacing
            val decoration = GridSpacingItemDecoration(spanCount, spacing, config.scrollHorizontally, config.fileRoundedCorners, media, true)
            binding.searchGrid.addItemDecoration(decoration)
        } else {
            // the waterfall and mosaic views handle spacing inside the item layouts
            var index = binding.searchGrid.itemDecorationCount - 1
            while (index >= 0) {
                val decoration = binding.searchGrid.getItemDecorationAt(index)
                if (decoration is GridSpacingItemDecoration) {
                    binding.searchGrid.removeItemDecoration(decoration)
                }
                index--
            }
        }
    }

    private fun getMediaAdapter() = binding.searchGrid.adapter as? MediaAdapter

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        getMediaAdapter()?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun itemClicked(path: String) {
        if (!path.isVideoFast()) {
            openInViewPager(path)
            return
        }

        when (config.videoPlayerType) {
            VIDEO_PLAYER_SYSTEM -> openPath(path = path, forceChooser = false)
            VIDEO_PLAYER_APP -> if (config.gestureVideoPlayer) launchGesturePlayer(path) else openInViewPager(path)
            else -> openInViewPager(path) // unreachable by design
        }
    }

    private fun openInViewPager(path: String) {
        ViewPagerActivity.searchMedia = getMediaAdapter()?.media?.filterIsInstanceTo(ArrayList<Medium>()) ?: ArrayList()
        Intent(this, ViewPagerActivity::class.java).apply {
            putExtra(PATH, path)
            putExtra(SHOW_ALL, false)
            putExtra(IS_FROM_SEARCH, true)
            startActivity(this)
        }
    }

    private fun setupLayoutManager() {
        val viewType = config.getFolderViewType(SHOW_ALL)
        when (viewType) {
            VIEW_TYPE_GRID -> setupGridLayoutManager()
            VIEW_TYPE_WATERFALL -> setupWaterfallLayoutManager()
            VIEW_TYPE_MOSAIC -> setupMosaicLayoutManager()
            else -> setupListLayoutManager()
        }

        // the fastscroller crashes on the staggered grid, it is neutralized in the waterfall view
        binding.searchFastscroller.updateWaterfallCompat(binding.searchGrid, viewType == VIEW_TYPE_WATERFALL)
    }

    private fun setupGridLayoutManager() {
        val layoutManager = (binding.searchGrid.layoutManager as? MyGridLayoutManager)
            ?: MyGridLayoutManager(this, 1).also { binding.searchGrid.layoutManager = it }
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.searchGrid.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.searchGrid.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        layoutManager.spanCount = config.mediaColumnCnt
        val adapter = getMediaAdapter()
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter?.isASectionTitle(position) == true) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = (binding.searchGrid.layoutManager as? MyGridLayoutManager)
            ?: MyGridLayoutManager(this, 1).also { binding.searchGrid.layoutManager = it }
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
    }

    private fun setupWaterfallLayoutManager() {
        val layoutManager = (binding.searchGrid.layoutManager as? StaggeredGridLayoutManager)
            ?: StaggeredGridLayoutManager(config.mediaColumnCnt, RecyclerView.VERTICAL).also {
                binding.searchGrid.layoutManager = it
            }
        layoutManager.orientation = RecyclerView.VERTICAL
        layoutManager.spanCount = config.mediaColumnCnt
    }

    private fun setupMosaicLayoutManager() {
        val layoutManager = (binding.searchGrid.layoutManager as? MyGridLayoutManager)
            ?: MyGridLayoutManager(this, MOSAIC_TOTAL_SPANS).also { binding.searchGrid.layoutManager = it }
        layoutManager.orientation = RecyclerView.VERTICAL
        layoutManager.spanCount = MOSAIC_TOTAL_SPANS
        val adapter = getMediaAdapter()
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return adapter?.getMosaicSpanSize(position) ?: MOSAIC_TOTAL_SPANS
            }
        }.apply {
            // the spans change with the data and the column count, a stale index cache would break the rows
            isSpanIndexCacheEnabled = false
        }
    }

    private fun setupScrollDirection() {
        val viewType = config.getFolderViewType(SHOW_ALL)
        val scrollHorizontally = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
        binding.searchFastscroller.setScrollVertically(!scrollHorizontally)
    }

    private fun getAllMedia() {
        getCachedMedia("") {
            if (it.isNotEmpty()) {
                mAllMedia = it.clone() as ArrayList<ThumbnailItem>
            }
            runOnUiThread {
                setupAdapter()
            }
            startAsyncTask(mLastSearchedText.isNotEmpty())
        }
    }

    private fun startAsyncTask(updateItems: Boolean) {
        mCurrAsyncTask?.stopFetching()
        mCurrAsyncTask = GetMediaAsynctask(applicationContext, "", showAll = true) {
            mAllMedia = it.clone() as ArrayList<ThumbnailItem>
            if (updateItems) {
                textChanged(mLastSearchedText)
            }
        }

        mCurrAsyncTask!!.execute()
    }

    override fun refreshItems() {
        startAsyncTask(true)
    }

    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>, skipRecycleBin: Boolean) {
        val filtered = fileDirItems.filter { File(it.path).isFile && it.path.isMediaFile() } as ArrayList
        if (filtered.isEmpty()) {
            return
        }

        if (config.useRecycleBin && !skipRecycleBin && !filtered.first().path.startsWith(recycleBinPath)) {
            val movingItems = resources.getQuantityString(org.fossify.commons.R.plurals.moving_items_into_bin, filtered.size, filtered.size)
            toast(movingItems)

            movePathsInRecycleBin(filtered.map { it.path } as ArrayList<String>) {
                if (it) {
                    deleteFilteredFiles(filtered)
                } else {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
            }
        } else {
            val deletingItems = resources.getQuantityString(org.fossify.commons.R.plurals.deleting_items, filtered.size, filtered.size)
            toast(deletingItems)
            deleteFilteredFiles(filtered)
        }
    }

    private fun deleteFilteredFiles(filtered: ArrayList<FileDirItem>) {
        deleteFiles(filtered) {
            if (!it) {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                return@deleteFiles
            }

            mAllMedia.removeAll { filtered.map { it.path }.contains((it as? Medium)?.path) }

            ensureBackgroundThread {
                val useRecycleBin = config.useRecycleBin
                filtered.forEach {
                    if (it.path.startsWith(recycleBinPath) || !useRecycleBin) {
                        deleteDBPath(it.path)
                    }
                }
            }
        }
    }

    override fun selectedPaths(paths: ArrayList<String>) {}

    override fun updateMediaGridDecoration(media: ArrayList<ThumbnailItem>) {}
}
