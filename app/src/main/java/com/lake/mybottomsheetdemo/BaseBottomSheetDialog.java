package com.lake.mybottomsheetdemo;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import com.lake.mybottomsheetdemo.bottom.BottomSheetDialog2;

public abstract class BaseBottomSheetDialog extends BottomSheetDialog2 implements View.OnClickListener {

    private ImageView closeBtn;

    public BaseBottomSheetDialog(@NonNull Context context) {
        super(context,R.style.BottomSheetDialog2);
    }

    protected abstract int onLayoutInflater();

    protected abstract void initView(View view);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = LayoutInflater.from(getContext()).inflate(R.layout.base_bottom_sheet_layout, null);
        setContentView(view);
        addSubView(view);
        initView(view);
        //虽然设置了style宽度占满，但是底部始终有间隙，通过以下方法占满底部
        getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        setCanceledOnTouchOutside(false);
    }

    private void addSubView(View view) {
        FrameLayout frameLayout = view.findViewById(R.id.content_page);
        frameLayout.removeAllViews();
        View subView = LayoutInflater.from(getContext()).inflate(onLayoutInflater(), null);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        frameLayout.addView(subView,params);

        closeBtn = view.findViewById(R.id.closeBtn);
        closeBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.closeBtn:
                dismiss();
                break;
        }
    }
}
