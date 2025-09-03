package com.example.paymenttracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.card.MaterialCardView;
import java.util.List;
import android.util.SparseBooleanArray;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private List<Message> messages;
    private SparseBooleanArray expandedState = new SparseBooleanArray();
    private static final int TRUNCATED_MAX_LINES = 3;
    private static final int MESSAGE_LENGTH_THRESHOLD = 100;

    public MessageAdapter(List<Message> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message_card, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message msg = messages.get(position);
        holder.tvSender.setText(msg.sender);
        holder.tvMessage.setText(msg.content);
        holder.tvDate.setText(msg.getFormattedTimestamp());
        holder.chipStatus.setText(msg.status);

        // Set chip color based on status
        int colorRes;
        switch (msg.status) {
            case "SUBMITTED":
                colorRes = R.color.text_accent;
                break;
            case "IGNORED":
                colorRes = R.color.text_pink;
                break;
            case "WEBHOOK NOT SET": // NEW CASE
                colorRes = R.color.text_secondary;
                break;
            default:
                colorRes = R.color.text_secondary;
        }
        holder.chipStatus.setChipBackgroundColorResource(colorRes);

        // Logic to handle truncated messages
        if (msg.content.length() > MESSAGE_LENGTH_THRESHOLD) {
            holder.ivExpandIcon.setVisibility(View.VISIBLE);
            if (expandedState.get(position, false)) {
                holder.tvMessage.setMaxLines(Integer.MAX_VALUE);
                holder.ivExpandIcon.setImageResource(R.drawable.ic_collapse_less);
            } else {
                holder.tvMessage.setMaxLines(TRUNCATED_MAX_LINES);
                holder.ivExpandIcon.setImageResource(R.drawable.ic_expand_more);
            }

            holder.ivExpandIcon.setOnClickListener(v -> {
                boolean isExpanded = expandedState.get(position, false);
                expandedState.put(position, !isExpanded);
                notifyItemChanged(position);
            });
        } else {
            holder.ivExpandIcon.setVisibility(View.GONE);
            holder.tvMessage.setMaxLines(Integer.MAX_VALUE);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender, tvMessage, tvDate;
        Chip chipStatus;
        MaterialCardView cardView;
        ImageView ivExpandIcon;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvDate = itemView.findViewById(R.id.tvDate);
            chipStatus = itemView.findViewById(R.id.chipStatus);
            cardView = (MaterialCardView) itemView;
            ivExpandIcon = itemView.findViewById(R.id.ivExpandIcon);
        }
    }
}