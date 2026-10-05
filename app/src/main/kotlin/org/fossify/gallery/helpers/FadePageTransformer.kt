package org.fossify.gallery.helpers

import android.view.View
import androidx.viewpager2.widget.ViewPager2

class FadePageTransformer(private val isVertical: Boolean = false) : ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        // cancel the native page movement, so the pages fade in place
        if (isVertical) {
            view.translationY = view.height * -position
        } else {
            view.translationX = view.width * -position
        }

        view.alpha = if (position <= -1f || position >= 1f) {
            0f
        } else if (position == 0f) {
            1f
        } else {
            1f - Math.abs(position)
        }
    }
}
