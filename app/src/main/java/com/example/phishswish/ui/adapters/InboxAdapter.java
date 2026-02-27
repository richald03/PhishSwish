package com.example.phishswish.ui.adapters;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.phishswish.R;
import com.example.phishswish.model.InboxMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InboxAdapter extends RecyclerView.Adapter<InboxAdapter.VH> {

    public interface OnItemClick {
        void onClick(InboxMessage msg);
    }

    private ArrayList<InboxMessage> items;
    private final ArrayList<InboxMessage> fullList;
    private final OnItemClick listener;
    private String currentQuery = "";
    private boolean isLinkFilterActive = false;

    public InboxAdapter(ArrayList<InboxMessage> items, OnItemClick listener) {
        this.items = items;
        this.fullList = new ArrayList<>(items);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_inbox_message, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        InboxMessage m = items.get(position);

        String sender = m.address == null ? "Unknown" : m.address;
        String body = m.body == null ? "" : m.body;

        highlightText(h.tvSender, sender, currentQuery, false);
        highlightText(h.tvBodyPreview, body, currentQuery, isLinkFilterActive);

        String dateStr = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                .format(new Date(m.date));
        h.tvDate.setText(dateStr);

        // Show/hide unread dot
        if (h.vUnreadDot != null) {
            h.vUnreadDot.setVisibility(m.isRead ? View.GONE : View.VISIBLE);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(m));
    }

    private void highlightText(TextView textView, String originalText, String query, boolean highlightLinks) {
        SpannableString spannable = new SpannableString(originalText);
        boolean changed = false;

        // Highlight Search Query
        if (query != null && !query.isEmpty()) {
            String lowerText = originalText.toLowerCase();
            String lowerQuery = query.toLowerCase();
            int start = 0;
            while ((start = lowerText.indexOf(lowerQuery, start)) != -1) {
                int end = start + lowerQuery.length();
                spannable.setSpan(new BackgroundColorSpan(Color.YELLOW), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = end;
                changed = true;
            }
        }

        // Highlight Links if filter is active - Use YELLOW as requested
        if (highlightLinks) {
            Pattern urlPattern = Pattern.compile("(https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = urlPattern.matcher(originalText);
            while (matcher.find()) {
                spannable.setSpan(new BackgroundColorSpan(Color.YELLOW), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                changed = true;
            }
        }

        if (changed) {
            textView.setText(spannable);
        } else {
            textView.setText(originalText);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateList(ArrayList<InboxMessage> newList) {
        this.fullList.clear();
        this.fullList.addAll(newList);
        this.items = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        this.currentQuery = query;
        applyFilters();
    }

    public void setFilteredList(ArrayList<InboxMessage> filteredList, boolean linkFilterActive) {
        this.isLinkFilterActive = linkFilterActive;
        this.items = filteredList;
        notifyDataSetChanged();
    }

    public ArrayList<InboxMessage> getFullList() {
        return fullList;
    }

    private void applyFilters() {
        ArrayList<InboxMessage> filtered = new ArrayList<>();
        String lowerQuery = currentQuery.toLowerCase().trim();
        
        for (InboxMessage msg : fullList) {
            boolean matchesSearch = lowerQuery.isEmpty() || 
                (msg.address != null && msg.address.toLowerCase().contains(lowerQuery)) ||
                (msg.body != null && msg.body.toLowerCase().contains(lowerQuery));
            
            if (matchesSearch) {
                filtered.add(msg);
            }
        }
        this.items = filtered;
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvSender, tvBodyPreview, tvDate;
        View vUnreadDot;

        public VH(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvBodyPreview = itemView.findViewById(R.id.tvBodyPreview);
            tvDate = itemView.findViewById(R.id.tvDate);
            vUnreadDot = itemView.findViewById(R.id.vUnreadDot);
        }
    }
}
