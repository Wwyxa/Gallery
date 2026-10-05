package org.fossify.gallery.adapters

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.fragments.PhotoFragment
import org.fossify.gallery.fragments.VideoFragment
import org.fossify.gallery.fragments.ViewPagerFragment
import org.fossify.gallery.helpers.MEDIUM
import org.fossify.gallery.helpers.SHOULD_INIT_FRAGMENT
import org.fossify.gallery.models.Medium

class MyPagerAdapter(val activity: ViewPagerActivity, media: MutableList<Medium>) : FragmentStateAdapter(activity) {
    var media: MutableList<Medium> = media
        private set

    // the list hash is mixed into the fragment ids, so any media change gives every fragment a new
    // id and makes them all rebuild from scratch (like ViewPager1's POSITION_NONE did), while the
    // ids stay stable across activity recreations, which lets the restored fragments be reused
    private var itemIds = listOf<Long>()

    private var currentPosition = -1
    private val fragments = HashMap<Int, ViewPagerFragment>()

    init {
        rebuildItemIds()
    }

    override fun getItemCount() = media.size

    override fun getItemId(position: Int): Long = itemIds[position]

    override fun containsItem(itemId: Long): Boolean = itemIds.contains(itemId)

    override fun createFragment(position: Int): Fragment {
        val medium = media[position]
        val bundle = Bundle().apply {
            putSerializable(MEDIUM, medium)
            putBoolean(SHOULD_INIT_FRAGMENT, true)
        }

        val fragment = if (medium.isVideo()) {
            VideoFragment()
        } else {
            PhotoFragment()
        }
        fragment.arguments = bundle
        fragment.listener = activity

        // ViewPager2 never calls setMenuVisibility on its own, emulate the ViewPager1 behavior
        fragment.setMenuVisibility(position == currentPosition)
        fragments[position] = fragment
        return fragment
    }

    fun updateMedia(newMedia: MutableList<Medium>) {
        media = newMedia
        rebuildItemIds()
        fragments.clear()
        notifyDataSetChanged()

        // reused fragments restored by the FragmentManager point at the old activity
        activity.supportFragmentManager.fragments.forEach { (it as? ViewPagerFragment)?.listener = activity }
    }

    fun updateCurrentPosition(position: Int) {
        currentPosition = position
        for ((pos, fragment) in fragments) {
            fragment.setMenuVisibility(pos == position)
        }
    }

    fun getCurrentFragment(position: Int) = fragments[position]

    fun toggleFullscreen(isFullscreen: Boolean) {
        for ((pos, fragment) in fragments) {
            // the fragment view might not be created yet (or not anymore), its state is refreshed on view creation
            if (fragment.view != null) {
                fragment.fullscreenToggled(isFullscreen)
            }
        }
    }

    private fun rebuildItemIds() {
        val listHash = media.hashCode().toLong()
        itemIds = media.map { it.path.hashCode().toLong() xor listHash }
    }
}
