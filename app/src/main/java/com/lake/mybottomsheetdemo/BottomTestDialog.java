package com.lake.mybottomsheetdemo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class BottomTestDialog extends BaseBottomSheetDialog {
    private final List<String> datas = new ArrayList<>();

    BottomTestDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected int onLayoutInflater() {
        return R.layout.bottom_sheet_test_layout;
    }

    @Override
    protected void initView(View view) {
        initData();
        RecyclerView recyclerView = view.findViewById(R.id.listview);
        MyAdapter adapter = new MyAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void initData() {
        for(int i = 0;i<20;i++)
            datas.add(i+"-item");
    }

    class MyAdapter extends RecyclerView.Adapter {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext()).inflate(R.layout.item_list_layout, parent, false);
            return new MyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MyViewHolder myViewHolder = (MyViewHolder)holder;
            myViewHolder.setValue(datas.get(position));
        }

        @Override
        public int getItemCount() {
            return datas.size();
        }
    }

    static class MyViewHolder extends RecyclerView.ViewHolder {
        private TextView tv;

        MyViewHolder(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.item_tv);
        }

        void setValue(String s) {
            tv.setText(s);
        }
    }

}
