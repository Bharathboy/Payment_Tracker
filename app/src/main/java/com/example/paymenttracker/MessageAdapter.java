package com.example.paymenttracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private List<Message> messages;

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
    holder.tvDate.setText(msg.date);
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
            default:
                colorRes = R.color.text_secondary;
        }
        holder.chipStatus.setChipBackgroundColorResource(colorRes);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender, tvMessage, tvDate;
        Chip chipStatus;
        MaterialCardView cardView;
        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvDate = itemView.findViewById(R.id.tvDate);
            chipStatus = itemView.findViewById(R.id.chipStatus);
            cardView = (MaterialCardView) itemView;
        }
    }
}
