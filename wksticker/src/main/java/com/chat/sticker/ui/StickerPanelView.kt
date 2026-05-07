package com.chat.sticker.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.chat.base.config.WKApiConfig
import com.chat.base.emoji.EmojiAdapter
import com.chat.base.emoji.EmojiEntry
import com.chat.base.emoji.EmojiManager
import com.chat.base.endpoint.EndpointManager
import com.chat.base.msg.IConversationContext
import com.chat.base.msg.model.WKGifContent
import com.chat.base.ui.Theme
import com.xinbida.wukongim.msgmodel.WKImageContent
import com.chat.base.utils.AndroidUtilities
import com.chat.base.utils.WKToastUtils
import com.chat.sticker.R
import com.chat.sticker.WKStickerApplication
import com.chat.sticker.databinding.ViewStickerPanelBinding
import com.chat.sticker.entity.StickerItem
import com.chat.sticker.service.StickerModel
import com.chat.sticker.ui.adapter.StickerGridAdapter
import com.chat.sticker.ui.adapter.StickerTabAdapter
import com.chat.sticker.ui.adapter.StickerTabItem
import com.chat.sticker.utils.StickerTrace
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * 聊天贴纸面板
 * Created by Luckclouds .
 */
class StickerPanelView @JvmOverloads constructor(
    context: Context,
    private val conversationContext: IConversationContext,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    companion object {
        private val listeners = CopyOnWriteArrayList<WeakReference<StickerPanelView>>()

        fun notifyDataChanged() {
            listeners.removeAll { it.get() == null }
            StickerTrace.d("STICKER_TRACE_PANEL_NOTIFY activeViews=${listeners.size}")
            listeners.forEach { it.get()?.refreshTabsAndData() }
        }

        private const val PAGE_SWITCH_PROGRESS_THRESHOLD = 0.28f
    }

    private val binding = ViewStickerPanelBinding.inflate(LayoutInflater.from(context), this, true)
    private val tabAdapter = StickerTabAdapter()
    private val stickerAdapter = StickerGridAdapter()
    private var currentTabKey = "favorites"
    private var currentPage = Page.EMOJI
    private var currentTabs = mutableListOf<StickerTabItem>()
    private val sectionPositions = linkedMapOf<String, Int>()
    private var ignoreStickerScroll = false
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var pageProgress = 0f
    private var isDraggingPage = false
    private var pageAnimator: ValueAnimator? = null
    private var bottomBarVisible = true
    private var swipeStartPage = Page.EMOJI

    private enum class Page {
        EMOJI, STICKER
    }

    init {
        orientation = VERTICAL
        listeners += WeakReference(this)
        StickerTrace.d("STICKER_TRACE_PANEL_INIT conversation=${conversationContext.chatChannelInfo.channelID} type=${conversationContext.chatChannelInfo.channelType}")
        initEmoji()
        initSticker()
        initBottomBar()
        initSwipeSwitch()
        switchPage(Page.EMOJI)
        binding.pageContainer.post { setPageProgress(0f) }
        refreshTabsAndData()
    }

    private fun initEmoji() {
        val normalList = EmojiManager.getInstance().getEmojiWithType("0_")
        val naturelList = EmojiManager.getInstance().getEmojiWithType("1_")
        val symbolsList = EmojiManager.getInstance().getEmojiWithType("2_")
        val width = AndroidUtilities.getScreenWidth() - AndroidUtilities.dp(30f) * 8
        val list = ArrayList<EmojiEntry>()
        list.addAll(normalList)
        list.addAll(naturelList)
        list.addAll(symbolsList)
        val adapter = EmojiAdapter(list, width)
        binding.emojiRecyclerView.layoutManager = GridLayoutManager(context, 8)
        binding.emojiRecyclerView.adapter = adapter
        adapter.setOnItemClickListener { adapter1, _, position ->
            val emojiEntry = adapter1.getItem(position) as? EmojiEntry ?: return@setOnItemClickListener
            EndpointManager.getInstance().invoke("emoji_click", emojiEntry.text)
        }
        binding.emojiRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                handleContentScroll(dy)
            }
        })
    }

    private fun initSticker() {
        binding.tabRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.tabRecyclerView.adapter = tabAdapter
        val stickerLayoutManager = GridLayoutManager(context, 5)
        stickerLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (stickerAdapter.getItem(position)?.isSectionHeader == true) stickerLayoutManager.spanCount else 1
            }
        }
        binding.stickerRecyclerView.layoutManager = stickerLayoutManager
        stickerAdapter.showTitles = false
        stickerAdapter.previewMoveResolver = ::findStickerItemUnder
        binding.stickerRecyclerView.adapter = stickerAdapter
        attachStickerPreviewTouchListener()
        tabAdapter.setOnItemClickListener { _, _, position ->
            if (StickerFullScreenPreview.isShowing()) return@setOnItemClickListener
            val key = currentTabs.getOrNull(position)?.key ?: return@setOnItemClickListener
            selectStickerSection(key, true)
        }
        stickerAdapter.setOnItemClickListener { _, _, position ->
            val item = stickerAdapter.getItem(position) ?: return@setOnItemClickListener
            if (item.isSectionHeader) return@setOnItemClickListener
            if (item.isAddCell) {
                val intent = android.content.Intent(context, StickerCustomActivity::class.java)
                context.startActivity(intent)
                return@setOnItemClickListener
            }
            sendSticker(item)
        }
        binding.stickerRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (ignoreStickerScroll) return
                updateTabByScroll()
                handleContentScroll(dy)
            }
        })
    }

    private fun handleContentScroll(dy: Int) {
        when {
            dy > AndroidUtilities.dp(2f) -> setBottomBarVisible(false)
            dy < -AndroidUtilities.dp(2f) -> setBottomBarVisible(true)
        }
    }

    private fun attachStickerPreviewTouchListener() {
        binding.stickerRecyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (!StickerFullScreenPreview.isShowing()) return false
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> StickerFullScreenPreview.handleMove(e.rawX, e.rawY)
                    MotionEvent.ACTION_UP -> StickerFullScreenPreview.handleRelease(true)
                    MotionEvent.ACTION_CANCEL -> StickerFullScreenPreview.handleRelease(false)
                }
                return true
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (!StickerFullScreenPreview.isShowing()) return
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> StickerFullScreenPreview.handleMove(e.rawX, e.rawY)
                    MotionEvent.ACTION_UP -> StickerFullScreenPreview.handleRelease(true)
                    MotionEvent.ACTION_CANCEL -> StickerFullScreenPreview.handleRelease(false)
                }
            }
        })
    }

    private fun initBottomBar() {
        val pressedColor = ContextCompat.getColor(context, com.chat.base.R.color.layoutColorSelected)
        binding.emojiTabLayout.background = Theme.createSelectorDrawable(pressedColor, 3)
        binding.stickerTabLayout.background = Theme.createSelectorDrawable(pressedColor, 3)
        binding.actionLayout.background = Theme.createSelectorDrawable(pressedColor, 3)
        binding.emojiTabLayout.setOnClickListener {
            if (!StickerFullScreenPreview.isShowing()) switchPage(Page.EMOJI)
        }
        binding.stickerTabLayout.setOnClickListener {
            if (!StickerFullScreenPreview.isShowing()) switchPage(Page.STICKER)
        }
        binding.actionLayout.setOnClickListener {
            if (StickerFullScreenPreview.isShowing()) return@setOnClickListener
            if (currentPage == Page.EMOJI) {
                EndpointManager.getInstance().invoke("emoji_click", "")
            } else {
                WKStickerApplication.getInstance().openStore(context)
            }
        }
    }

    private fun initSwipeSwitch() {
        attachSwipeSwitch(binding.pageContainer)
        attachSwipeSwitch(binding.emojiRecyclerView)
        attachSwipeSwitch(binding.stickerPage)
        attachSwipeSwitch(binding.stickerRecyclerView)
    }

    private fun attachSwipeSwitch(view: View) {
        view.setOnTouchListener { _, event ->
            if (StickerFullScreenPreview.isShowing()) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> StickerFullScreenPreview.handleMove(event.rawX, event.rawY)
                    MotionEvent.ACTION_UP -> StickerFullScreenPreview.handleRelease(true)
                    MotionEvent.ACTION_CANCEL -> StickerFullScreenPreview.handleRelease(false)
                }
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pageAnimator?.cancel()
                    swipeDownX = event.rawX
                    swipeDownY = event.rawY
                    isDraggingPage = false
                    swipeStartPage = currentPage
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - swipeDownX
                    val dy = event.rawY - swipeDownY
                    if (!isDraggingPage && abs(dx) > AndroidUtilities.dp(4f) && abs(dx) > abs(dy) * 0.65f) {
                        isDraggingPage = true
                    }
                    if (isDraggingPage) {
                        val width = pageWidth()
                        val baseProgress = if (swipeStartPage == Page.EMOJI) 0f else 1f
                        setPageProgress((baseProgress - dx / width).coerceIn(0f, 1f))
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDraggingPage) {
                        switchPage(resolveSwipeTargetPage())
                        isDraggingPage = false
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingPage) {
                        switchPage(swipeStartPage)
                        isDraggingPage = false
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun resolveSwipeTargetPage(): Page {
        return when (swipeStartPage) {
            Page.EMOJI -> if (pageProgress >= PAGE_SWITCH_PROGRESS_THRESHOLD) Page.STICKER else Page.EMOJI
            Page.STICKER -> if (pageProgress <= 1f - PAGE_SWITCH_PROGRESS_THRESHOLD) Page.EMOJI else Page.STICKER
        }
    }

    private fun switchPage(page: Page) {
        currentPage = page
        StickerTrace.d("STICKER_TRACE_PANEL_SWITCH page=$page")
        animateToPage(page)
        val activeColor = ContextCompat.getColor(context, com.chat.base.R.color.colorAccent)
        val inactiveColor = ContextCompat.getColor(context, com.chat.base.R.color.color999)
        binding.emojiTabIv.colorFilter = PorterDuffColorFilter(if (page == Page.EMOJI) activeColor else inactiveColor, PorterDuff.Mode.SRC_IN)
        binding.stickerTabIv.colorFilter = PorterDuffColorFilter(if (page == Page.STICKER) activeColor else inactiveColor, PorterDuff.Mode.SRC_IN)
        binding.actionIv.setImageResource(if (page == Page.EMOJI) R.mipmap.sticker_delete_icon else R.mipmap.sticker_settings_icon)
        binding.actionIv.colorFilter = PorterDuffColorFilter(inactiveColor, PorterDuff.Mode.SRC_IN)
    }

    private fun animateToPage(page: Page) {
        val targetProgress = if (page == Page.EMOJI) 0f else 1f
        if (binding.pageContainer.width <= 0) {
            binding.pageContainer.post { setPageProgress(targetProgress) }
            return
        }
        pageAnimator?.cancel()
        pageAnimator = ValueAnimator.ofFloat(pageProgress, targetProgress).apply {
            val remaining = abs(pageProgress - targetProgress)
            duration = (260 + remaining * 360).toLong().coerceIn(260L, 620L)
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { animator ->
                setPageProgress(animator.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    setPageProgress(targetProgress)
                    pageAnimator = null
                }

                override fun onAnimationCancel(animation: Animator) {
                    pageAnimator = null
                }
            })
            start()
        }
    }

    private fun setPageProgress(progress: Float) {
        pageProgress = progress.coerceIn(0f, 1f)
        val width = pageWidth()
        binding.emojiRecyclerView.visibility = View.VISIBLE
        binding.stickerPage.visibility = View.VISIBLE
        binding.emojiRecyclerView.translationX = -pageProgress * width
        binding.stickerPage.translationX = (1f - pageProgress) * width
        binding.emojiRecyclerView.alpha = (1f - pageProgress * 0.18f).coerceIn(0.82f, 1f)
        binding.stickerPage.alpha = (0.82f + pageProgress * 0.18f).coerceIn(0.82f, 1f)
    }

    private fun pageWidth(): Float {
        return (binding.pageContainer.width.takeIf { it > 0 } ?: AndroidUtilities.getScreenWidth()).toFloat()
    }

    fun refreshTabsAndData() {
        StickerTrace.d("STICKER_TRACE_PANEL_REFRESH currentTab=$currentTabKey currentPage=$currentPage")
        StickerModel.instance.getPanel { code, msg, panel ->
            if (code != com.chat.base.net.HttpResponseCode.success.toInt()) {
                StickerTrace.e("STICKER_TRACE_PANEL_REFRESH fail code=$code msg=$msg")
                WKToastUtils.getInstance().showToastNormal(if (msg.isEmpty()) context.getString(R.string.sticker_request_failed) else msg)
                return@getPanel
            }
            StickerTrace.d("STICKER_TRACE_PANEL_RESPONSE favoriteCount=${panel.favoriteCount} customCount=${panel.customCount} panelMyPackages=${panel.myPackages.size}")
            // 面板 tab 以“我的表情包”接口为准，避免商店添加成功后 panel 接口延迟导致面板不刷新。
            StickerModel.instance.getMyPackages { packageCode, packageMsg, packages ->
                if (packageCode == com.chat.base.net.HttpResponseCode.success.toInt()) {
                    StickerTrace.d("STICKER_TRACE_PANEL_MY_PACKAGES count=${packages.size}")
                    applyPanelPackages(packages)
                } else {
                    StickerTrace.e("STICKER_TRACE_PANEL_MY_PACKAGES_FAIL code=$packageCode msg=$packageMsg fallbackCount=${panel.myPackages.size}")
                    applyPanelPackages(panel.myPackages)
                }
            }
        }
    }

    private fun applyPanelPackages(packages: MutableList<com.chat.sticker.entity.StickerPackage>) {
        currentTabs = mutableListOf(
            StickerTabItem("favorites", context.getString(R.string.sticker_favorites), iconRes = R.drawable.ic_sticker_favorite_nav, selected = currentTabKey == "favorites"),
        )
        packages.forEach {
            currentTabs += StickerTabItem(
                "package:${it.packageId}",
                it.name,
                iconUrl = if (it.icon.isNotEmpty()) it.icon else it.cover,
                selected = currentTabKey == "package:${it.packageId}"
            )
        }
        StickerTrace.d("STICKER_TRACE_PANEL_TABS keys=${currentTabs.joinToString { it.key }}")
        if (currentTabs.none { it.selected }) {
            currentTabs.firstOrNull()?.selected = true
            currentTabKey = currentTabs.firstOrNull()?.key ?: "favorites"
        }
        tabAdapter.setList(currentTabs)
        loadStickerSections(packages)
    }

    private fun loadStickerSections(packages: MutableList<com.chat.sticker.entity.StickerPackage>) {
        val favorites = mutableListOf<StickerItem>()
        val customItems = mutableListOf<StickerItem>()
        val packageItems = linkedMapOf<String, MutableList<StickerItem>>()
        var pending = 2 + packages.size
        fun finishOne() {
            pending--
            if (pending == 0) {
                renderStickerSections(packages, favorites, customItems, packageItems)
            }
        }
        StickerModel.instance.getFavorites { code, msg, list ->
            if (code == com.chat.base.net.HttpResponseCode.success.toInt()) {
                favorites += list
            } else {
                StickerTrace.e("STICKER_TRACE_SECTION_FAIL key=favorites code=$code msg=$msg")
            }
            finishOne()
        }
        StickerModel.instance.getCustom { code, msg, list ->
            if (code == com.chat.base.net.HttpResponseCode.success.toInt()) {
                customItems += list
            } else {
                StickerTrace.e("STICKER_TRACE_SECTION_FAIL key=custom code=$code msg=$msg")
            }
            finishOne()
        }
        packages.forEach { pkg ->
            val key = "package:${pkg.packageId}"
            StickerModel.instance.getPackageDetail(pkg.packageId) { code, msg, _, items ->
                if (code == com.chat.base.net.HttpResponseCode.success.toInt()) {
                    packageItems[key] = items
                } else {
                    StickerTrace.e("STICKER_TRACE_SECTION_FAIL key=$key code=$code msg=$msg")
                    packageItems[key] = mutableListOf()
                }
                finishOne()
            }
        }
    }

    private fun renderStickerSections(
        packages: MutableList<com.chat.sticker.entity.StickerPackage>,
        favorites: MutableList<StickerItem>,
        customItems: MutableList<StickerItem>,
        packageItems: LinkedHashMap<String, MutableList<StickerItem>>,
    ) {
        val data = mutableListOf<StickerItem>()
        sectionPositions.clear()
        sectionPositions["favorites"] = data.size
        data += StickerItem(
            sectionKey = "favorites",
            sectionName = context.getString(R.string.sticker_custom_title),
            isSectionHeader = true,
            showAddButton = true
        )
        data += favorites.map { it.copy(sectionKey = "favorites") }
        data += customItems.map { it.copy(sectionKey = "favorites") }
        packages.forEach { pkg ->
            val key = "package:${pkg.packageId}"
            sectionPositions[key] = data.size
            data += StickerItem(
                sectionKey = key,
                sectionName = pkg.name,
                isSectionHeader = true
            )
            data += (packageItems[key] ?: mutableListOf()).map { it.copy(sectionKey = key) }
        }
        stickerAdapter.editMode = false
        stickerAdapter.setList(data)
        binding.emptyTv.visibility = if (data.size <= sectionPositions.size) View.VISIBLE else View.GONE
        StickerTrace.d("STICKER_TRACE_PANEL_RENDER_SECTIONS count=${data.size} sections=${sectionPositions.keys.joinToString()} first=${StickerTrace.itemSummary(data.firstOrNull { !it.isSectionHeader })}")
        selectStickerSection(currentTabKey.takeIf { sectionPositions.containsKey(it) } ?: "favorites", false)
    }

    private fun selectStickerSection(key: String, scroll: Boolean) {
        currentTabKey = key
        currentTabs.forEach { it.selected = it.key == key }
        tabAdapter.notifyDataSetChanged()
        if (scroll) {
            val position = sectionPositions[key] ?: return
            ignoreStickerScroll = true
            val smoothScroller = object : LinearSmoothScroller(context) {
                override fun getVerticalSnapPreference(): Int = SNAP_TO_START
                override fun calculateTimeForScrolling(dx: Int): Int {
                    return super.calculateTimeForScrolling(dx) * 2
                }
            }
            smoothScroller.targetPosition = position
            binding.stickerRecyclerView.layoutManager?.startSmoothScroll(smoothScroller)
            binding.stickerRecyclerView.postDelayed({ ignoreStickerScroll = false }, 840)
        }
    }

    private fun setBottomBarVisible(visible: Boolean) {
        if (bottomBarVisible == visible) return
        bottomBarVisible = visible
        val barHeight = binding.bottomBarLayout.height.takeIf { it > 0 } ?: AndroidUtilities.dp(45f)
        val hideOffset = (barHeight + AndroidUtilities.dp(20f)).toFloat()
        binding.bottomBarLayout.animate().cancel()
        if (visible) {
            binding.bottomBarLayout.visibility = View.VISIBLE
            binding.bottomBarLayout.translationY = hideOffset
            binding.bottomBarLayout.alpha = 0f
        }
        binding.bottomBarLayout.animate()
            .translationY(if (visible) 0f else hideOffset)
            .alpha(if (visible) 1f else 0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!bottomBarVisible) {
                        binding.bottomBarLayout.visibility = View.GONE
                    }
                    binding.bottomBarLayout.animate().setListener(null)
                }
            })
            .start()
    }

    private fun updateTabByScroll() {
        val layoutManager = binding.stickerRecyclerView.layoutManager as? GridLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION) return
        val activeKey = sectionPositions.entries.lastOrNull { it.value <= firstVisible }?.key ?: return
        if (activeKey != currentTabKey) {
            selectStickerSection(activeKey, false)
        }
    }

    private fun findStickerItemUnder(rawX: Float, rawY: Float): StickerItem? {
        val location = IntArray(2)
        binding.stickerRecyclerView.getLocationOnScreen(location)
        val localX = rawX - location[0]
        val localY = rawY - location[1]
        val child = binding.stickerRecyclerView.findChildViewUnder(localX, localY) ?: return null
        val position = binding.stickerRecyclerView.getChildAdapterPosition(child)
        if (position == RecyclerView.NO_POSITION) return null
        val item = stickerAdapter.getItem(position) ?: return null
        return item.takeUnless { it.isSectionHeader || it.isAddCell }
    }

    private fun sendSticker(item: StickerItem) {
        val mediaUrl = resolveMediaUrl(item)
        val previewUrl = when {
            item.thumbUrl.isNotEmpty() -> item.thumbUrl
            item.gifUrl.isNotEmpty() -> item.gifUrl
            item.originUrl.isNotEmpty() -> item.originUrl
            else -> mediaUrl
        }
        if (mediaUrl.isEmpty()) {
            StickerTrace.e("STICKER_TRACE_SEND abort reason=empty_url item=${StickerTrace.itemSummary(item)}")
            WKToastUtils.getInstance().showToastNormal(context.getString(R.string.sticker_request_failed))
            return
        }
        val width = if (item.width > 0) item.width else 120
        val height = if (item.height > 0) item.height else 120
        val format = item.originExt.ifEmpty { inferFormat(mediaUrl, item.sourceMediaType) }.lowercase()
        val isGif = item.gifUrl.isNotEmpty() || item.sourceMediaType.contains("gif", true) || format.contains("gif", true) || mediaUrl.contains(".gif", true)
        val category = resolveCategory(item)
        val favoriteTargetType = resolveFavoriteTargetType(item)
        val favoriteTargetId = resolveFavoriteTargetId(item, favoriteTargetType)
        val placeholder = buildStickerPlaceholder(previewUrl, width, height, favoriteTargetType, favoriteTargetId, format)
        StickerTrace.d("STICKER_TRACE_SEND start isGif=$isGif tab=$currentTabKey mediaUrl=$mediaUrl format=$format favoriteTargetType=$favoriteTargetType favoriteTargetId=$favoriteTargetId item=${StickerTrace.itemSummary(item)}")
        if (isGif) {
            val content = WKGifContent()
            content.url = mediaUrl
            content.width = width
            content.height = height
            content.category = category
            content.placeholder = placeholder
            content.title = item.name.ifEmpty { item.keyword }
            content.format = format
            StickerTrace.d("STICKER_TRACE_SEND contentType=WK_GIF url=${content.url} category=${content.category} title=${content.title} placeholder=${content.placeholder} format=${content.format}")
            conversationContext.sendMessage(content)
        } else {
            val content = WKImageContent("")
            content.url = mediaUrl
            content.width = width
            content.height = height
            StickerTrace.d("STICKER_TRACE_SEND contentType=WK_IMAGE url=${content.url} size=${content.width}x${content.height}")
            conversationContext.sendMessage(content)
        }
    }

    private fun resolveMediaUrl(item: StickerItem): String {
        val directUrl = when {
            item.gifUrl.isNotEmpty() -> item.gifUrl
            item.originUrl.isNotEmpty() -> item.originUrl
            item.thumbUrl.isNotEmpty() -> item.thumbUrl
            else -> ""
        }
        if (directUrl.isNotEmpty()) return directUrl
        return item.itemId.takeIf { looksLikeMediaPath(it) }.orEmpty()
    }

    private fun looksLikeMediaPath(value: String): Boolean {
        val cleanValue = value.trim()
        if (cleanValue.length <= 5 || cleanValue.startsWith(".")) return false
        return cleanValue.startsWith("http://", true) ||
            cleanValue.startsWith("https://", true) ||
            cleanValue.startsWith("/") ||
            cleanValue.contains("/")
    }

    private fun inferFormat(url: String, sourceMediaType: String): String {
        if (sourceMediaType.isNotEmpty()) {
            return sourceMediaType.substringAfterLast('/', "")
        }
        val cleanUrl = url.substringBefore('?')
        return cleanUrl.substringAfterLast('.', "")
    }

    private fun resolveCategory(item: StickerItem): String {
        if (item.packageId.isNotEmpty()) return item.packageId
        if (item.customId.isNotEmpty()) return "custom"
        if (item.groupNo.isNotEmpty()) return item.groupNo
        return when {
            currentTabKey == "custom" -> "custom"
            currentTabKey.startsWith("package:") -> currentTabKey.removePrefix("package:")
            else -> currentTabKey
        }
    }

    private fun resolveFavoriteTargetType(item: StickerItem): String {
        if (item.targetType.isNotEmpty()) return item.targetType
        if (item.emojiId.isNotEmpty()) return "dynamic_emoji"
        if (item.customId.isNotEmpty() || currentTabKey == "custom") return "custom_item"
        return "platform_item"
    }

    private fun resolveFavoriteTargetId(item: StickerItem, targetType: String): String {
        if (item.targetId.isNotEmpty()) return item.targetId
        return when (targetType) {
            "custom_item" -> item.customId.ifEmpty { item.itemId }
            "dynamic_emoji" -> item.emojiId.ifEmpty { item.itemId }
            else -> item.itemId
        }
    }

    private fun buildStickerPlaceholder(
        previewPath: String,
        width: Int,
        height: Int,
        favoriteTargetType: String,
        favoriteTargetId: String,
        format: String,
    ): String {
        if (previewPath.isEmpty()) return ""
        val safeHref = previewPath
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
        val targetTypeAttr = favoriteTargetType.takeIf { it.isNotEmpty() }?.let { """ data-sticker-target-type="${escapeXmlAttr(it)}"""" }.orEmpty()
        val targetIdAttr = favoriteTargetId.takeIf { it.isNotEmpty() }?.let { """ data-sticker-target-id="${escapeXmlAttr(it)}"""" }.orEmpty()
        val formatAttr = format.takeIf { it.isNotEmpty() }?.let { """ data-sticker-format="${escapeXmlAttr(it)}"""" }.orEmpty()
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height"$targetTypeAttr$targetIdAttr$formatAttr><image href="$safeHref" width="$width" height="$height" preserveAspectRatio="xMidYMid meet" /></svg>"""
    }

    private fun escapeXmlAttr(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
