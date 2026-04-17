package com.chat.moments.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import com.chat.base.utils.AndroidUtilities

class MomentPullRefreshHelper(
    private val context: Context,
    private val containerView: View,
    private val indicatorView: ImageView
) {
    // 下拉时加载图从顶部开始出现的最小位移。
    // 这里的 0f 是 Float 字面量，本身没有单位；传给 dp(...) 才表示 dp。
    private val pullAppearOffsetPx = AndroidUtilities.dp(0f)

    // 页面最大下拉量，同时也是加载图固定位置阈值和触发刷新阈值。
    // 单位是 px，当前固定成 300f，后面你直接改这个值即可。
    private val fixedAndRefreshOffsetPx = 300f

    // 允许用户继续下拉的最大倍率。
    // 页面位移会固定在 fixedAndRefreshOffsetPx，但继续下拉时仍然会给到更大的 offset 用来驱动旋转。
    private val headerMaxDragRate = 1.35f

    // 加载图在布局里的顶部基准位置，需和 xml 中的 layout_marginTop 保持一致。
    private val indicatorAnchorTopPx = AndroidUtilities.dp(54f).toFloat()

    // 加载图本身大小，对应 xml 里的 40dp。
    private val indicatorSizePx = AndroidUtilities.dp(40f)

    // 承载区底部额外保留的安全距离，避免固定阈值过大时图标被裁掉。
    private val pullContainerBottomExtraPx = AndroidUtilities.dp(24f)

    // 下拉时每 1px 对应的顺时针旋转角度，手指往上回推时会自然按同样比例逆时针回退。
    private val rotateDegreesPerPx = 3.0f

    // 达到阈值后松手，额外执行的逆时针旋转角度。
    private val releaseSpinDegrees = -1720f

    // 松手后追加旋转的时长。
    private val releaseSpinDurationMs = 900L

    // 刷新结束后，加载图自身淡出/复位的时长。
    private val indicatorResetDurationMs = 420L

    // 用户未达到阈值就松手时，加载图回收的时长。
    private val indicatorCancelDurationMs = 180L

    private var isSpinningAfterRelease = false
    private var isRefreshDataReady = false
    private var isReleaseSpinFinished = false
    private var isIndicatorResetting = false
    private var finishRunnable: Runnable? = null

    init {
        (containerView as? ViewGroup)?.apply {
            clipChildren = true
            clipToPadding = true
        }
        ensurePullContainerHeight()
    }

    fun onPull(offset: Int, isDragging: Boolean) {
        if (isSpinningAfterRelease || isIndicatorResetting) return
        if (offset <= 0) {
            if (!isDragging) {
                resetImmediately()
            }
            return
        }
        containerView.visibility = View.VISIBLE
        val visibleOffset = (offset - pullAppearOffsetPx).coerceAtLeast(0)
        val translateY = visibleOffset.coerceAtMost(fixedAndRefreshOffsetPx.toInt()).toFloat()
        indicatorView.translationY = translateY
        indicatorView.rotation = visibleOffset * rotateDegreesPerPx
        val alphaProgress = (visibleOffset / fixedAndRefreshOffsetPx).coerceIn(0f, 1f)
        indicatorView.alpha = 0.25f + alphaProgress * 0.75f
        indicatorView.scaleX = 0.9f + alphaProgress * 0.1f
        indicatorView.scaleY = 0.9f + alphaProgress * 0.1f
    }

    fun startReleaseSpinIfNeeded(offset: Int) {
        if (isSpinningAfterRelease) return
        isSpinningAfterRelease = true
        isRefreshDataReady = false
        isReleaseSpinFinished = false
        isIndicatorResetting = false
        containerView.visibility = View.VISIBLE
        indicatorView.animate().cancel()
        indicatorView.translationY = offset.coerceAtMost(fixedAndRefreshOffsetPx.toInt()).toFloat()
        indicatorView.alpha = 1f
        indicatorView.scaleX = 1f
        indicatorView.scaleY = 1f
        indicatorView.animate()
            .rotationBy(releaseSpinDegrees)
            .setInterpolator(LinearInterpolator())
            .setDuration(releaseSpinDurationMs)
            .withEndAction {
                isReleaseSpinFinished = true
                tryFinishRefreshing()
            }
            .start()
    }

    fun markRefreshDataReady(onReadyToFinish: () -> Unit) {
        if (!isSpinningAfterRelease) {
            onReadyToFinish()
            return
        }
        pendingFinishAction = onReadyToFinish
        isRefreshDataReady = true
        if (isReleaseSpinFinished) {
            tryFinishRefreshing()
        }
    }

    fun finishRefreshing() {
        if (isIndicatorResetting) return
        isIndicatorResetting = true
        indicatorView.animate().cancel()
        indicatorView.animate()
            .alpha(0f)
            .rotation(0f)
            .translationY(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(indicatorResetDurationMs)
            .withEndAction {
                resetImmediately()
            }
            .start()
    }

    fun cancelPull() {
        if (isSpinningAfterRelease || isIndicatorResetting) return
        if (indicatorView.alpha <= 0f && indicatorView.translationY == 0f) {
            resetImmediately()
            return
        }
        isIndicatorResetting = true
        indicatorView.animate().cancel()
        indicatorView.animate()
            .alpha(0f)
            .rotation(0f)
            .translationY(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(indicatorCancelDurationMs)
            .withEndAction {
                resetImmediately()
            }
            .start()
    }

    private var pendingFinishAction: (() -> Unit)? = null

    private fun tryFinishRefreshing() {
        if (!isRefreshDataReady || !isReleaseSpinFinished) return
        finishRunnable?.let { containerView.removeCallbacks(it) }
        val finishAction = Runnable {
            pendingFinishAction?.invoke()
            pendingFinishAction = null
            finishRunnable = null
        }
        finishRunnable = finishAction
        containerView.post(finishAction)
    }

    private fun resetImmediately() {
        finishRunnable?.let { containerView.removeCallbacks(it) }
        finishRunnable = null
        indicatorView.animate().cancel()
        indicatorView.rotation = 0f
        indicatorView.translationY = 0f
        indicatorView.alpha = 0f
        indicatorView.scaleX = 0.9f
        indicatorView.scaleY = 0.9f
        containerView.visibility = View.GONE
        isSpinningAfterRelease = false
        isRefreshDataReady = false
        isReleaseSpinFinished = false
        isIndicatorResetting = false
        pendingFinishAction = null
    }

    private fun ensurePullContainerHeight() {
        val targetHeight = (indicatorAnchorTopPx + fixedAndRefreshOffsetPx + indicatorSizePx + pullContainerBottomExtraPx).toInt()
        val layoutParams = containerView.layoutParams ?: return
        if (layoutParams.height < targetHeight) {
            layoutParams.height = targetHeight
            containerView.layoutParams = layoutParams
        }
    }

    fun getFixedAndRefreshOffsetPx(): Int {
        return fixedAndRefreshOffsetPx.toInt()
    }

    fun getHeaderMaxDragRate(): Float {
        return headerMaxDragRate
    }

    fun resolveContentTranslationY(offset: Int): Float {
        return offset.coerceAtMost(fixedAndRefreshOffsetPx.toInt()).toFloat()
    }
}
