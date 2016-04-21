package io.relayr.iotsmartphone.ui.rules;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import butterknife.ButterKnife;
import butterknife.InjectView;
import io.relayr.iotsmartphone.R;

public class RuleCondition extends RuleContainer {

    public RuleCondition(Context context) {this(context, null);}

    public RuleCondition(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RuleCondition(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ButterKnife.inject(this, this);
    }
}