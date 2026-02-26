package com.dp.loganalysis_2;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dp.logcat.Log;

import java.util.List;

public class LogsAdapter extends RecyclerView.Adapter<LogsAdapter.VH> {
    private final List<Log> data;

    public LogsAdapter(List<com.dp.logcat.Log> data) {
        this.data = data;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView tv = new TextView(parent.getContext());
        tv.setPadding(16, 16, 16, 16);
        tv.setTextSize(12);
        return new VH(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.tv.setText(data.get(position).toString());
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tv;
        VH(@NonNull View itemView) {
            super(itemView);
            tv = (TextView) itemView;
        }
    }
}