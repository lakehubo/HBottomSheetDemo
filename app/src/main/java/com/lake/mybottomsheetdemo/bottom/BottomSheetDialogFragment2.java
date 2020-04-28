package com.lake.mybottomsheetdemo.bottom;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class BottomSheetDialogFragment2 extends AppCompatDialogFragment {
    /**
     * Tracks if we are waiting for a dismissAllowingStateLoss or a regular dismiss once the
     * BottomSheet is hidden and onStateChanged() is called.
     */
    private boolean waitingForDismissAllowingStateLoss;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog2(getContext(), getTheme());
    }

    @Override
    public void dismiss() {
        if (!tryDismissWithAnimation(false)) {
            super.dismiss();
        }
    }

    @Override
    public void dismissAllowingStateLoss() {
        if (!tryDismissWithAnimation(true)) {
            super.dismissAllowingStateLoss();
        }
    }

    /**
     * Tries to dismiss the dialog fragment with the bottom sheet animation. Returns true if possible,
     * false otherwise.
     */
    private boolean tryDismissWithAnimation(boolean allowingStateLoss) {
        Dialog baseDialog = getDialog();
        if (baseDialog instanceof BottomSheetDialog2) {
            BottomSheetDialog2 dialog = (BottomSheetDialog2) baseDialog;
            BottomSheetBehavior2<?> behavior = dialog.getBehavior();
            if (behavior.isHideable() && dialog.getDismissWithAnimation()) {
                dismissWithAnimation(behavior, allowingStateLoss);
                return true;
            }
        }

        return false;
    }

    private void dismissWithAnimation(
            @NonNull BottomSheetBehavior2<?> behavior, boolean allowingStateLoss) {
        waitingForDismissAllowingStateLoss = allowingStateLoss;

        if (behavior.getState() == BottomSheetBehavior2.STATE_HIDDEN) {
            dismissAfterAnimation();
        } else {
            if (getDialog() instanceof BottomSheetDialog2) {
                ((BottomSheetDialog2) getDialog()).removeDefaultCallback();
            }
            behavior.addBottomSheetCallback(new BottomSheetDialogFragment2.BottomSheetDismissCallback());
            behavior.setState(BottomSheetBehavior2.STATE_HIDDEN);
        }
    }

    private void dismissAfterAnimation() {
        if (waitingForDismissAllowingStateLoss) {
            super.dismissAllowingStateLoss();
        } else {
            super.dismiss();
        }
    }

    private class BottomSheetDismissCallback extends BottomSheetBehavior2.BottomSheetCallback {

        @Override
        public void onStateChanged(@NonNull View bottomSheet, int newState) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                dismissAfterAnimation();
            }
        }

        @Override
        public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
    }
}
