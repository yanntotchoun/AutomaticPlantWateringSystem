package com.yourteam.plantwatering.ui.dashboard;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

public abstract class BaseFragment extends Fragment {

    protected void applyStatusBarInset(View header) {
        applyStatusBarInset(header, 24);
    }

    protected void applyStatusBarInset(View header, float extraTopDp) {
        int extraTopPaddingPx = (int) (extraTopDp * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(header, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                v.getPaddingLeft(),
                systemBars.top + extraTopPaddingPx,
                v.getPaddingRight(),
                v.getPaddingBottom()
            );
            return windowInsets;
        });
        
        if (header.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(header);
        } else {
            header.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    v.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(v);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {}
            });
        }
    }
}
