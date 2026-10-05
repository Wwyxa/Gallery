package org.fossify.gallery.fragments

import android.view.MotionEvent
import androidx.fragment.app.Fragment
import org.fossify.commons.extensions.*
import org.fossify.gallery.R
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.*
import org.fossify.gallery.models.Medium

abstract class ViewPagerFragment : Fragment() {
    var listener: FragmentListener? = null

    private var mTouchDownTime = 0L
    private var mTouchDownX = 0f
    private var mTouchDownY = 0f
    private var mCloseDownThreshold = 100f
    private var mIgnoreCloseDown = false

    abstract fun fullscreenToggled(isFullscreen: Boolean)

    interface FragmentListener {
        fun fragmentClicked()

        fun videoEnded(): Boolean

        fun goToPrevItem()

        fun goToNextItem()

        fun launchViewVideoIntent(path: String)

        fun isSlideShowActive(): Boolean

        fun isFullScreen(): Boolean
    }

    fun getPathToLoad(medium: Medium): String {
        val context = context ?: return medium.path
        return if (context.isPathOnOTG(medium.path)) {
            medium.path.getOTGPublicPath(context)
        } else {
            medium.path
        }
    }

    protected fun handleEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownTime = System.currentTimeMillis()
                mTouchDownX = event.rawX
                mTouchDownY = event.rawY
            }

            MotionEvent.ACTION_POINTER_DOWN -> mIgnoreCloseDown = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val diffX = mTouchDownX - event.rawX
                val diffY = mTouchDownY - event.rawY

                val downGestureDuration = System.currentTimeMillis() - mTouchDownTime
                // with vertical swiping enabled the down gesture goes to the previous item, the
                // view is then closed with a right swipe (a left swipe keeps doing nothing)
                val currentActivity = activity
                val isVerticalSwiping = currentActivity is ViewPagerActivity && currentActivity.config.swipeDirection == SWIPE_DIRECTION_VERTICAL
                val isClosingGesture = if (isVerticalSwiping) {
                    diffX < -mCloseDownThreshold && Math.abs(diffX) > Math.abs(diffY)
                } else {
                    Math.abs(diffY) > Math.abs(diffX) && diffY < -mCloseDownThreshold
                }
                if (!mIgnoreCloseDown && isClosingGesture && downGestureDuration < MAX_CLOSE_DOWN_GESTURE_DURATION && context?.config?.allowDownGesture == true) {
                    val exitAnimation = if (isVerticalSwiping) {
                        R.anim.slide_out_right
                    } else {
                        org.fossify.commons.R.anim.slide_down
                    }
                    currentActivity?.finish()
                    currentActivity?.overridePendingTransition(0, exitAnimation)
                }
                mIgnoreCloseDown = false
            }
        }
    }
}
