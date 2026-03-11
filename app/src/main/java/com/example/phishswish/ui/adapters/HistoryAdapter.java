package com.example.phishswish.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.phishswish.R;
import com.example.phishswish.model.FirestoreScan;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    public interface OnHistoryClickListener {
        void onClick(FirestoreScan item);
        void onLongClick(FirestoreScan item);
    }

    private List<FirestoreScan> scanList;
    private OnHistoryClickListener listener;

    public HistoryAdapter(List<FirestoreScan> scanList, OnHistoryClickListener listener) {
        this.scanList = scanList;
        this.listener = listener;
    }

    public void setScanList(List<FirestoreScan> scanList) {
        this.scanList = scanList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        FirestoreScan item = scanList.get(position);
        holder.bind(item);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(item);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongClick(item);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return scanList != null ? scanList.size() : 0;
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvLabel, tvConfidence, tvSnippet, tvTimestamp;
        ImageView ivCategoryIcon;
        FrameLayout flCategoryIcon;
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tvHistoryLabel);
            tvConfidence = itemView.findViewById(R.id.tvHistoryConfidence);
            tvSnippet = itemView.findViewById(R.id.tvHistorySnippet);
            tvTimestamp = itemView.findViewById(R.id.tvHistoryTimestamp);
            ivCategoryIcon = itemView.findViewById(R.id.ivCategoryIcon);
            flCategoryIcon = itemView.findViewById(R.id.flCategoryIcon);
        }

        public void bind(FirestoreScan scan) {
            String label = scan.label != null ? scan.label.toUpperCase() : "SAFE";
            
            if ("SAFE".equals(label)) {
                tvLabel.setText(itemView.getContext().getString(R.string.label_secure_message));
                tvLabel.setTextColor(Color.parseColor("#111827"));
                ivCategoryIcon.setImageResource(R.drawable.ic_safe);
                flCategoryIcon.setBackgroundResource(R.drawable.bg_circle_safe);
                tvConfidence.setTextColor(Color.parseColor("#16A34A"));
                tvConfidence.setBackgroundResource(R.drawable.bg_chip_online);
            } else if ("SPAM".equals(label)) {
                tvLabel.setText(itemView.getContext().getString(R.string.label_suspicious_content));
                tvLabel.setTextColor(Color.parseColor("#111827"));
                ivCategoryIcon.setImageResource(R.drawable.ic_spam);
                flCategoryIcon.setBackgroundResource(R.drawable.bg_circle_spam);
                tvConfidence.setTextColor(Color.parseColor("#F59E0B"));
                tvConfidence.setBackgroundResource(R.drawable.bg_chip_online);
            } else {
                tvLabel.setText(itemView.getContext().getString(R.string.label_high_threat));
                tvLabel.setTextColor(Color.parseColor("#111827"));
                ivCategoryIcon.setImageResource(R.drawable.ic_scam);
                flCategoryIcon.setBackgroundResource(R.drawable.bg_circle_scam);
                tvConfidence.setTextColor(Color.parseColor("#DC2626"));
                tvConfidence.setBackgroundResource(R.drawable.bg_chip_offline);
            }

            tvConfidence.setText(scan.confidence + "%");
            tvSnippet.setText(scan.snippet);
            tvTimestamp.setText(dateFormat.format(new Date(scan.timestamp)));
        }
    }
}
