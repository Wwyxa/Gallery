package org.fossify.gallery.extensions

import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible

// the fastscroller hard-casts the recyclerview's layout manager to a LinearLayoutManager in its scroll
// listener, which crashes with the staggered grid of the waterfall view. The listener cannot be swapped
// out reflectively (final field with optimized reads) and the library also invokes it directly from its
// own posted alignment runnables, so neither detaching it from the recyclerview nor hiding the track is
// enough on its own. The scroll listener bails out early while the handle is engaged, so that state is
// what gets faked here, on top of hiding the track and detaching the recyclerview listener
fun RecyclerViewFastScroller.updateWaterfallCompat(recyclerView: RecyclerView, isWaterfall: Boolean) {
    try {
        val listenerField = RecyclerViewFastScroller::class.java.getDeclaredField("onScrollListener")
        listenerField.isAccessible = true
        val scrollListener = listenerField.get(this) as RecyclerView.OnScrollListener

        val engagedField = RecyclerViewFastScroller::class.java.getDeclaredField("isEngaged")
        engagedField.isAccessible = true

        val trackField = RecyclerViewFastScroller::class.java.getDeclaredField("trackView")
        trackField.isAccessible = true
        val track = trackField.get(this) as LinearLayout

        recyclerView.removeOnScrollListener(scrollListener)
        track.beGoneIf(isWaterfall)
        if (isWaterfall) {
            isFastScrollEnabled = true
            engagedField.set(this, true)
        } else {
            engagedField.set(this, false)
            recyclerView.addOnScrollListener(scrollListener)
        }
    } catch (ignored: Exception) {
    }
}
