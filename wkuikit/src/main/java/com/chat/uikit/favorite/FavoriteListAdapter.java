package com.chat.uikit.favorite;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.base.entity.PopupMenuItem;
import com.chat.base.glide.GlideUtils;
import com.chat.base.ui.components.AvatarView;
import com.chat.base.utils.WKDialogUtils;
import com.chat.uikit.R;

import java.util.ArrayList;
import java.util.List;

public class FavoriteListAdapter extends BaseQuickAdapter<FavoriteEntity, BaseViewHolder> {
    private final FavoriteModel favoriteModel = FavoriteModel.getInstance();
    private final IClick iClick;

    public FavoriteListAdapter(IClick iClick) {
        super(R.layout.item_favorite_layout, new ArrayList<>());
        this.iClick = iClick;
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, FavoriteEntity item) {
        TextView contentTv = holder.getView(R.id.contentTv);
        ImageView imageView = holder.getView(R.id.imageView);
        AvatarView avatarView = holder.getView(R.id.avatarView);
        avatarView.setSize(14f);
        if (item.isImageType()) {
            imageView.setVisibility(View.VISIBLE);
            contentTv.setVisibility(View.GONE);
            GlideUtils.getInstance().showImg(getContext(), FavoriteDetailActivity.buildImageShowUrl(item), imageView);
            imageView.setOnClickListener(v -> {
                if (iClick != null) {
                    iClick.onPreview(item, imageView);
                }
            });
        } else {
            imageView.setVisibility(View.GONE);
            contentTv.setVisibility(View.VISIBLE);
            contentTv.setText(item.getDisplayContent());
            imageView.setOnClickListener(null);
        }
        if (item.buildAuthorChannel() != null) {
            avatarView.showAvatar(item.buildAuthorChannel(), true);
        } else {
            avatarView.showAvatar("", (byte) 1);
        }
        holder.setText(R.id.nameTv, TextUtils.isEmpty(item.getDisplayName()) ? item.authorUid : item.getDisplayName());
        holder.setText(R.id.timeTv, favoriteModel.formatTime(item.createdAt));
        List<PopupMenuItem> list = new ArrayList<>();
        list.add(new PopupMenuItem(getContext().getString(R.string.delete), R.mipmap.msg_delete, () -> {
            if (iClick != null) {
                iClick.onDelete(item);
            }
        }));
        WKDialogUtils.getInstance().setViewLongClickPopup(holder.getView(R.id.contentLayout), list);
        holder.getView(R.id.contentLayout).setOnClickListener(v -> {
            if (iClick != null && item.isImageType()) {
                iClick.onPreview(item, imageView);
            } else if (iClick != null) {
                iClick.onClick(item);
            }
        });
    }

    public interface IClick {
        void onClick(FavoriteEntity entity);

        void onDelete(FavoriteEntity entity);

        void onPreview(FavoriteEntity entity, ImageView imageView);
    }
}
