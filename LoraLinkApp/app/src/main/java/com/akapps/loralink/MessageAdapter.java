package com.akapps.loralink;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import io.realm.Realm;
import io.realm.RealmResults;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private RealmResults<Message> mMessages;
    private Context mContext;

    // constants
    private final int ITEM_TYPE_SENT = 0;
    private final int ITEM_TYPE_RECEIVED = 1;

    public MessageAdapter(RealmResults<Message> messages, Context mContext) {
        mMessages = messages;
        this.mContext = mContext;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView;
        if (viewType == ITEM_TYPE_SENT)
            itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_sent, parent, false);
        else
            itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_received, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        Message message = mMessages.get(position);

        holder.messageTextView.setText(message.getMessage());
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US);
        String formattedDate = dateFormat.format(message.getDate());
        holder.timestampTextView.setText(formattedDate);
        holder.timestampTextView.setVisibility(View.GONE);

        holder.background.setOnClickListener(view -> {
            if(holder.timestampTextView.getVisibility() == View.VISIBLE)
                holder.timestampTextView.setVisibility(View.GONE);
            else
                holder.timestampTextView.setVisibility(View.VISIBLE);
        });
    }

    public void addMessage(final String message, int isSender) {
        Realm realm = Realm.getDefaultInstance();
        realm.executeTransaction(realm1 -> {
            Message newMessage = realm1.createObject(Message.class, getNextId());
            newMessage.setMessage(message.trim());
            newMessage.setDate(new Date());
            newMessage.setSenderID(isSender);

            notifyDataSetChanged();
        });
        realm.close();
    }

    private long getNextId() {
        Realm realm = Realm.getDefaultInstance();
        Number currentIdNum = realm.where(Message.class).max("id");
        long nextId = (currentIdNum == null) ? 1 : currentIdNum.longValue() + 1;
        return nextId;
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    @Override
    public int getItemViewType(int position) {
        Message message = (Message) mMessages.get(position);
        if (message.getSenderID() == LocalData.getSenderID(mContext))
            return ITEM_TYPE_SENT;
        else
            return ITEM_TYPE_RECEIVED;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView messageTextView;
        public TextView timestampTextView;
        public MaterialCardView background;

        public ViewHolder(View itemView) {
            super(itemView);

            messageTextView = itemView.findViewById(R.id.text_view_message);
            timestampTextView = itemView.findViewById(R.id.message_time);
            background = itemView.findViewById(R.id.background);
        }
    }
}
