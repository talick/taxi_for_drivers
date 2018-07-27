package com.example.talgat.distancecounter;

import android.app.Activity;
import android.location.Location;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.talgat.distancecounter.model.Request;

import java.text.DecimalFormat;
import java.util.List;

public class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.MyViewHolder> {

    interface OnClickItemListener{
        void onItemClick(int pos);

        void onItemLongClick(int pos);
    }

    private OnClickItemListener listener;
    List<Request> requests;
    Activity activity;

    public RequestAdapter(List<Request> requests, Activity activity) {
        this.requests = requests;
        this.activity = activity;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.request_item, parent, false);

        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {

        Location location = new Location("");
        Location location2 = ((MainActivity)activity).currentLocation;
        if (location2 != null) {

            location.setLatitude(Double.parseDouble(requests.get(position).getLatitude()));
            location.setLongitude(Double.parseDouble(requests.get(position).getLongitude()));
            float distance = location.distanceTo(location2);
            distance = distance/1000;
            DecimalFormat df = new DecimalFormat("#.##");
            String dx=df.format(distance);
            holder.distanceTextView.setText(dx+ "км");
        }else holder.distanceTextView.setText("");
        holder.requestAddress.setText(requests.get(position).getAddress());
    }

    @Override
    public int getItemCount() {
        if (requests == null) {
            return  0;
        }
        return requests.size();
    }

    public void setRequests(List<Request> requests) {
        this.requests = requests;
        notifyDataSetChanged();
    }

    public void setListener(OnClickItemListener listener) {
        this.listener = listener;
    }

    public class MyViewHolder extends RecyclerView.ViewHolder{
        TextView requestAddress;
        TextView distanceTextView;
        public MyViewHolder(View itemView) {
            super(itemView);

            requestAddress = itemView.findViewById(R.id.addressText);
            distanceTextView = itemView.findViewById(R.id.distance);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onItemClick(getAdapterPosition());
                }
            });

            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    listener.onItemLongClick(getAdapterPosition());
                    return false;
                }
            });
        }
    }
}
