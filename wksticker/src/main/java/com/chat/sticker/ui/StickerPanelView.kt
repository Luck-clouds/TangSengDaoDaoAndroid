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
    }

    private val binding = ViewStickerPanelBinding.inflate(LayoutInflater.from(context), this, true)
    private val tabAdapter = StickerTabAdapter()
    private val stickerAdapter = StickerGridAdapter()
    private var currentTabKey = "favorites"
    private var currentPage = Page.EMOJI
    private var currentTabs = mutableListOf<StickerTabItem>()
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var pageProgress = 0f
    private var isDraggingPage = false
    private var pageAnimator: ValueAnimator? = null

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
    }

    private fun initSticker() {
        binding.tabRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.tabRecyclerView.adapter = tabAdapter
        binding.stickerRecyclerView.layoutManager = GridLayoutManager(context, 4)
        stickerAdapter.showTitles = false
        binding.stickerRecyclerView.adapter = stickerAdapter
        tabAdapter.setOnItemClickListener { _, _, position ->
            currentTabs.forEachIndexed { index, item -> item.selected = index == position }
            tabAdapter.notifyDataSetChanged()
            currentTabKey = currentTabs[position].key
            loadStickerTab(currentTabKey)
        }
        stickerAdapter.setOnItemClickListener { _, _, position ->
            val item = stickerAdapter.getItem(position) ?: return@setOnItemClickListener
            if (item.isAddCell) {
                val intent = android.content.Intent(context, StickerCustomActivity::class.java)
                context.startActivity(intent)
                return@setOnItemClickListener
            }
            sendSticker(item)
        }
    }

    private fun initBottomBar() {
        val pressedColor = ContextCompat.getColor(context, com.chat.base.R.color.layoutColorSelected)
        binding.emojiTabLayout.background = Theme.createSelectorDrawable(pressedColor, 3)
        binding.stickerTabLayout.background = Theme.createSelectorDrawable(pressedColor, 3)
        binding.actionLayout.background = Theme.createSelectorDrawable(pressedColor, 3)
        binding.emojiTabLayout.setOnClickListener { switchPage(Page.EMOJI) }
        binding.stickerTabLayout.setOnClickListener { switchPage(Page.STICKER) }
        binding.actionLayout.setOnClickListener {
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
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pageAnimator?.cancel()
                    swipeDownX = event.rawX
                    swipeDownY = event.rawY
                    isDraggingPage = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - swipeDownX
                    val dy = event.rawY - swipeDownY
                    if (!isDraggingPage && abs(dx) > AndroidUtilities.dp(8f) && abs(dx) > abs(dy) * 1.15f) {
                        isDraggingPage = true
                    }
                    if (isDraggingPage) {
                        val width = pageWidth()
                        val baseProgress = if (currentPage == Page.EMOJI) 0f else 1f
                        setPageProgress((baseProgress - dx / width).coerceIn(0f, 1f))
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDraggingPage) {
                        switchPage(if (pageProgress >= 0.5f) Page.STICKER else Page.EMOJI)
                        isDraggingPage = false
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingPage) {
                        switchPage(currentPage)
                        isDraggingPage = false
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun switchPage(page: Page) {
        currentPage = page
        StickerTrace.d("STICKER_TRACE_PANEL_SWITCH page=$page")
        animateToPage(page)
        val activeColor = ContextCompat.getColor(context, com.chat.base.R.color.colorAccent)
        val inactiveColor = ContextCompat.getColor(context, com.chat.base.R.color.color999)
        binding.emojiTabIv.colorFilter = PorterDuffColorFilter(if (page == Page.EMOJI) activeColor else inactiveColor, PorterDuff.Mode.MULTIPLY)
        binding.stickerTabIv.colorFilter = PorterDuffColorFilter(if (page == Page.STICKER) activeColor else inactiveColor, PorterDuff.Mode.MULTIPLY)
        binding.actionIv.setImageResource(if (page == Page.EMOJI) R.mipmap.sticker_delete_icon else R.mipmap.sticker_settings_icon)
        binding.actionIv.colorFilter = PorterDuffColorFilter(inactiveColor, PorterDuff.Mode.MULTIPLY)
    }

    private fun animateToPage(page: Page) {
        val targetProgress = if (page == Page.EMOJI) 0f else 1f
        if (binding.pageContainer.width <= 0) {
            binding.pageContainer.post { setPageProgress(targetProgress) }
            return
        }
        pageAnimator?.cancel()
        pageAnimator = ValueAnimator.ofFloat(pageProgress, targetProgress).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
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
            StickerTrace.d("STICKER_TRACE_PANEL_RESPONSE favoriteCount=${panel.favoriteCount} customCount=${panel.customCount} myPackages=${panel.myPackages.size}")
            currentTabs = mutableListOf(
                StickerTabItem("favorites", context.getString(R.string.sticker_favorites), currentTabKey == "favorites"),
                StickerTabItem("custom", context.getString(R.string.sticker_custom), currentTabKey == "custom"),
            )
            panel.myPackages.forEach {
                currentTabs += StickerTabItem("package:${it.packageId}", it.name, currentTabKey == "package:${it.packageId}")
            }
            StickerTrace.d("STICKER_TRACE_PANEL_TABS keys=${currentTabs.joinToString { it.key }}")
            if (currentTabs.none { it.selected }) {
                currentTabs.firstOrNull()?.selected = true
                currentTabKey = currentTabs.firstOrNull()?.key ?: "favorites"
            }
            tabAdapter.setList(currentTabs)
            loadStickerTab(currentTabKey)
        }
    }

    private fun loadStickerTab(tabKey: String) {
        StickerTrace.d("STICKER_TRACE_TAB_LOAD key=$tabKey")
        when {
            tabKey == "favorites" -> {
                StickerModel.instance.getFavorites { code, msg, list ->
                    if (code == com.chat.base.net.HttpResponseCode.success.toInt()) {
                        StickerTrace.d("STICKER_TRACE_TAB_DATA key=$tabKey count=${list.size} first=${StickerTrace.itemSummary(list.firstOrNull())}")
                        setStickerData(list)
                    } else {
                        StickerTrace.e("STICKER_TRACE_TAB_FAIL key=$tabKey code=$code msg=$msg")
                        showLoadFail(msg)
                    }
                }
            }
            tabKey == "custom" -> {
                StickerModel.instance.getCustom { code, msg, list ->
                    if (code == com.chat.base.net.HttpResponseCode.success.toInt()) {
                        val data = mutableListOf<StickerItem>()
                        data += StickerItem(isAddCell = true)
                        data += list
                        StickerTrace.d("STICKER_TRACE_TAB_DATA key=$tabKey count=${data.size} first=${StickerTrace.itemSummary(list.firstOrNull())}")
                        setStickerData(data)
                    } else {
                        StickerTrace.e("STICKER_TRACE_TAB_FAIL key=$tabKey code=$code msg=$msg")
                        showLoadFail(msg)
                    }
                }
            }
            tabKey.startsWith("package:") -> {
                val packageId = tabKey.removePrefix("package:")
                StickerModel.instance.getPackageDetail(packageId) { code, msg, _, items ->
                    if (code == com.chat.base.net.HttpResponseCode.success.toInt()) {
                        StickerTrace.d("STICKER_TRACE_TAB_DATA key=$tabKey count=${items.size} first=${StickerTrace.itemSummary(items.firstOrNull())}")
                        setStickerData(items)
                    } else {
                        StickerTrace.e("STICKER_TRACE_TAB_FAIL key=$tabKey code=$code msg=$msg")
                        showLoadFail(msg)
                    }
                }
            }
        }
    }

    private fun setStickerData(list: MutableList<StickerItem>) {
        stickerAdapter.editMode = false
        stickerAdapter.setList(list)
        StickerTrace.d("STICKER_TRACE_PANEL_RENDER count=${list.size} first=${StickerTrace.itemSummary(list.firstOrNull())}")
        binding.emptyTv.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showLoadFail(msg: String) {
        stickerAdapter.setList(mutableListOf())
        StickerTrace.e("STICKER_TRACE_PANEL_RENDER fail msg=$msg")
        binding.emptyTv.visibility = View.VISIBLE
        if (msg.isNotEmpty()) {
            WKToastUtils.getInstance().showToastNormal(msg)
        }
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
