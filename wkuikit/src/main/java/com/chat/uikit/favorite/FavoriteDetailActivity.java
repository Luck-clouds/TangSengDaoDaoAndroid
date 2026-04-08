package com.chat.uikit.favorite;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.chat.base.base.WKBaseActivity;
import com.chat.base.config.WKApiConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatChooseContacts;
import com.chat.base.endpoint.entity.ChooseChatMenu;
import com.chat.base.entity.ImagePopupBottomSheetItem;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.glide.GlideUtils;
import com.chat.base.utils.ImageUtils;
import com.chat.base.utils.WKDialogUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKToastUtils;
import com.chat.uikit.R;
import com.chat.uikit.databinding.ActFavoriteDetailLayoutBinding;
import com.google.android.material.snackbar.Snackbar;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.msgmodel.WKImageContent;
import com.xinbida.wukongim.msgmodel.WKMessageContent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FavoriteDetailActivity extends WKBaseActivity<ActFavoriteDetailLayoutBinding> {
    public static final String EXTRA_FAVORITE = "favorite";

    private FavoriteEntity entity;

    @Override
    protected ActFavoriteDetailLayoutBinding getViewBinding() {
        return ActFavoriteDetailLayoutBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initPresenter() {
        entity = (FavoriteEntity) getIntent().getSerializableExtra(EXTRA_FAVORITE);
    }

    @Override
    protected void setTitle(TextView titleTv) {
        titleTv.setText(R.string.favorite_detail_title);
    }

    @Override
    protected void initView() {
        if (entity == null) {
            finish();
            return;
        }
        if (entity.isImageType()) {
            wkVBinding.contentTv.setVisibility(View.GONE);
            wkVBinding.imageView.setVisibility(View.VISIBLE);
            String showUrl = buildImageShowUrl(entity);
            int[] size = ImageUtils.getInstance().getImageWidthAndHeightToTalk(entity.width, entity.height);
            ViewGroup.LayoutParams layoutParams = wkVBinding.imageView.getLayoutParams();
            layoutParams.width = size[0];
            layoutParams.height = size[1];
            wkVBinding.imageView.setLayoutParams(layoutParams);
            GlideUtils.getInstance().showImg(this, showUrl, size[0], size[1], wkVBinding.imageView);
            wkVBinding.imageView.setOnClickListener(v -> showImagePopup(showUrl));
            List<PopupMenuItem> list = new ArrayList<>();
            list.add(new PopupMenuItem(getString(R.string.forward), R.mipmap.msg_forward, this::forwardFavorite));
            WKDialogUtils.getInstance().setViewLongClickPopup(wkVBinding.imageView, list);
        } else {
            wkVBinding.imageView.setVisibility(View.GONE);
            wkVBinding.contentTv.setVisibility(View.VISIBLE);
            wkVBinding.contentTv.setText(entity.getDisplayContent());
            List<PopupMenuItem> list = new ArrayList<>();
            list.add(new PopupMenuItem(getString(R.string.copy), R.mipmap.msg_copy, this::copyText));
            list.add(new PopupMenuItem(getString(R.string.forward), R.mipmap.msg_forward, this::forwardFavorite));
            WKDialogUtils.getInstance().setViewLongClickPopup(wkVBinding.contentTv, list);
        }
    }

    public static String buildImageShowUrl(FavoriteEntity entity) {
        if (entity == null) {
            return "";
        }
        String showUrl = entity.getDisplayContent();
        boolean localFile = false;
        if (!TextUtils.isEmpty(showUrl) && !showUrl.startsWith("http") && !showUrl.startsWith("content://")) {
            File file = new File(showUrl);
            localFile = file.exists();
            if (!localFile) {
                showUrl = "";
            }
        }
        if (TextUtils.isEmpty(showUrl)) {
            WKMessageContent messageContent = FavoriteModel.getInstance().buildForwardContent(entity);
            if (messageContent instanceof WKImageContent) {
                WKImageContent imageContent = (WKImageContent) messageContent;
                showUrl = imageContent.localPath;
                localFile = !TextUtils.isEmpty(showUrl);
                if (TextUtils.isEmpty(showUrl)) {
                    showUrl = imageContent.url;
                    localFile = false;
                }
            }
        }
        if (!TextUtils.isEmpty(showUrl) && !showUrl.startsWith("content://") && !localFile) {
            showUrl = WKApiConfig.getShowUrl(showUrl);
        }
        return showUrl;
    }

    private void showImagePopup(String showUrl) {
        List<Object> tempImgList = new ArrayList<>();
        tempImgList.add(showUrl);
        List<ImageView> imgList = new ArrayList<>();
        imgList.add(wkVBinding.imageView);
        List<ImagePopupBottomSheetItem> bottomItems = new ArrayList<>();
        bottomItems.add(new ImagePopupBottomSheetItem(getString(R.string.forward), R.mipmap.msg_forward, position -> forwardFavorite()));
        WKDialogUtils.getInstance().showImagePopup(this, tempImgList, imgList, wkVBinding.imageView, 0, bottomItems, null, null);
    }

    private void forwardFavorite() {
        WKMessageContent messageContent = FavoriteModel.getInstance().buildForwardContent(entity);
        if (messageContent == null) {
            WKToastUtils.getInstance().showToastNormal(getString(R.string.favorite_forward_failed));
            return;
        }
        EndpointManager.getInstance().invoke(EndpointSID.showChooseChatView, new ChooseChatMenu(new ChatChooseContacts(list -> {
            if (WKReader.isEmpty(list)) {
                return;
            }
            for (WKChannel channel : list) {
                WKIM.getInstance().getMsgManager().send(messageContent, channel);
            }
            ViewGroup viewGroup = (ViewGroup) findViewById(android.R.id.content).getRootView();
            Snackbar.make(viewGroup, getString(R.string.is_forward), 1000).setAction("", v -> {
            }).show();
        }), messageContent));
    }

    private void deleteFavorite() {
        FavoriteModel.getInstance().deleteFavorite(this, entity, (code, msg) -> {
            if (code != com.chat.base.net.HttpResponseCode.success) {
                if (!TextUtils.isEmpty(msg)) {
                    showToast(msg);
                }
                return;
            }
            finish();
        });
    }

    private void copyText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            return;
        }
        cm.setPrimaryClip(ClipData.newPlainText("Label", entity.getDisplayContent()));
        WKToastUtils.getInstance().showToastNormal(getString(R.string.copyed));
    }
}
