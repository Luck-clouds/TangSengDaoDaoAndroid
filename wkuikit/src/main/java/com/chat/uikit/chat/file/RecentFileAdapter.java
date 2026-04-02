package com.chat.uikit.chat.file;

import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.chat.uikit.R;

public class RecentFileAdapter extends BaseQuickAdapter<RecentFileEntity, BaseViewHolder> {
    public interface IListener {
        void onOpenDetail(@NonNull RecentFileEntity item);

        void onSelect(@NonNull RecentFileEntity item, boolean selected);
    }

    private String selectedIdentity;
    private final IListener listener;

    public RecentFileAdapter(@NonNull IListener listener) {
        super(R.layout.item_recent_file_layout);
        this.listener = listener;
    }

    public void setSelectedIdentity(String selectedIdentity) {
        this.selectedIdentity = selectedIdentity;
        notifyDataSetChanged();
    }

    @Override
    protected void convert(@NonNull BaseViewHolder holder, RecentFileEntity item) {
        String badgeText = FileMessageUtils.getFileBadgeText(item.name);
        holder.setText(R.id.fileBadgeTv, badgeText);
        holder.setText(R.id.fileNameTv, item.name);
        holder.setText(R.id.fileSizeTv, FileMessageUtils.formatFileSize(item.size));
        holder.setText(R.id.fileTimeTv, FileMessageUtils.formatFileTime(item.modifiedAt));
        TextView badgeTv = holder.getView(R.id.fileBadgeTv);
        CheckBox checkBox = holder.getView(R.id.selectCb);
        boolean isSelected = !TextUtils.isEmpty(selectedIdentity) && selectedIdentity.equals(item.getIdentity());
        checkBox.setChecked(isSelected);
        badgeTv.setText(badgeText);

        View rootView = holder.getView(R.id.rootView);
        rootView.setOnClickListener(v -> listener.onOpenDetail(item));
        checkBox.setOnClickListener(v -> listener.onSelect(item, checkBox.isChecked()));
    }
}
