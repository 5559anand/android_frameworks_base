/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources; 
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.ServiceManager;
import android.os.UserHandle; 
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.statusbar.NavigationButtons;

import java.util.ArrayList; 

public class KeyButtonView extends ImageView {
    private static final String TAG = "StatusBar.KeyButtonView";

    final float GLOW_MAX_SCALE_FACTOR = 1.8f;
    static float mButtonAlpha = 0.70f;   

    long mDownTime;
    int mCode;
    boolean mIsSmall;
    int mTouchSlop;
    Drawable mGlowBG;
    static int mGlowBGColor = Integer.MIN_VALUE;  
    int mGlowWidth, mGlowHeight;
    static int mDurationSpeedOn = 500;
    static int mDurationSpeedOff = 50;  
    float mGlowAlpha = 0f, mGlowScale = 1f, mDrawingAlpha = 1f;
    boolean mSupportsLongPress = true;
    protected boolean mHandlingLongpress = false; 
    RectF mRect = new RectF(0f,0f,0f,0f);
    AnimatorSet mPressedAnim;
    boolean mInEditMode;

    private GlobalSettingsObserver mSettingsObserver; 

    Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (isPressed()) {
                setHandlingLongpress(true);
                if (!performLongClick() && (mCode != 0)) {
                    // we tried to do custom long click and failed
                    // do long click on primary 'key' 
                    sendEvent(KeyEvent.ACTION_DOWN, KeyEvent.FLAG_LONG_PRESS);
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                }
            }
        }
    };

    public KeyButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.KeyButtonView,
                defStyle, 0);

        mCode = a.getInteger(R.styleable.KeyButtonView_keyCode, 0);
        
        mSupportsLongPress = a.getBoolean(R.styleable.KeyButtonView_keyRepeat, true);

        mGlowBG = a.getDrawable(R.styleable.KeyButtonView_glowBackground);
        if (mGlowBG != null) {
            setDrawingAlpha(mButtonAlpha); 
            mGlowWidth = mGlowBG.getIntrinsicWidth();
            mGlowHeight = mGlowBG.getIntrinsicHeight();
        }
        
        a.recycle();

        setClickable(true);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
	mSettingsObserver = GlobalSettingsObserver.getInstance(context);

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mSettingsObserver != null) {
            mSettingsObserver.attach(this);
            mSettingsObserver.updateSettings();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mSettingsObserver != null) {
            mSettingsObserver.detach(this);
        } 
    }

    public void setSupportsLongPress(boolean supports) {
        //mSupportsLongpress = supports;
    }

    public void setHandlingLongpress(boolean handling) {
        mHandlingLongpress = handling;
    }

    public void setCode(int code) {
        mCode = code;
    }

    public int getCode() {
        return mCode;
    }

    public void setGlowBackground(int id) {
        mGlowBG = getResources().getDrawable(id);
        if (mGlowBG != null) {
            setDrawingAlpha(mButtonAlpha); 
            mGlowWidth = mGlowBG.getIntrinsicWidth();
            mGlowHeight = mGlowBG.getIntrinsicHeight();
            int defaultColor = mContext.getResources().getColor(
                    com.android.internal.R.color.white);
            ContentResolver resolver = mContext.getContentResolver();
            mGlowBGColor = Settings.System.getIntForUser(resolver,
                    Settings.System.NAVIGATION_BAR_GLOW_TINT, defaultColor, UserHandle.USER_CURRENT); 

            if (mGlowBGColor == Integer.MIN_VALUE) {
                mGlowBGColor = defaultColor;
            }
            mGlowBG.setColorFilter(null);
            mGlowBG.setColorFilter(mGlowBGColor, PorterDuff.Mode.SRC_ATOP);

        } 
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mGlowBG != null) {
            canvas.save();
            final int w = getWidth();
            final int h = getHeight();
            final float aspect = (float)mGlowWidth / mGlowHeight;
            final int drawW = (int)(h*aspect);
            final int drawH = h;
            final int margin = (drawW-w)/2;
            canvas.scale(mGlowScale, mGlowScale, w*0.5f, h*0.5f);
            mGlowBG.setBounds(-margin, 0, drawW-margin, drawH);
            mGlowBG.setAlpha((int)(mDrawingAlpha * mGlowAlpha * 255));
            mGlowBG.draw(canvas);
            canvas.restore();
            mRect.right = w;
            mRect.bottom = h;
        }
        super.onDraw(canvas);
    }

    public float getDrawingAlpha() {
        if (mGlowBG == null) return 0;
        return mDrawingAlpha;
    }

    public void setDrawingAlpha(float x) {
        if (mGlowBG == null) return;
        // Calling setAlpha(int), which is an ImageView-specific
        // method that's different from setAlpha(float). This sets
        // the alpha on this ImageView's drawable directly
        setAlpha((int) (x * 255));
        mDrawingAlpha = x;
	invalidate(); 
    }

    public float getGlowAlpha() {
        if (mGlowBG == null) return 0;
        return mGlowAlpha;
    }

    public void setGlowAlpha(float x) {
        if (mGlowBG == null) return;
        mGlowAlpha = x;
        invalidate();
    }

    public float getGlowScale() {
        if (mGlowBG == null) return 0;
        return mGlowScale;
    }

    public void setGlowScale(float x) {
        if (mGlowBG == null) return;
        mGlowScale = x;
        final float w = getWidth();
        final float h = getHeight();
        if (GLOW_MAX_SCALE_FACTOR <= 1.0f) {
            // this only works if we know the glow will never leave our bounds
            invalidate();
        } else {
            final float rx = (w * (GLOW_MAX_SCALE_FACTOR - 1.0f)) / 2.0f + 1.0f;
            final float ry = (h * (GLOW_MAX_SCALE_FACTOR - 1.0f)) / 2.0f + 1.0f;
            com.android.systemui.SwipeHelper.invalidateGlobalRegion(
                    this,
                    new RectF(getLeft() - rx,
                              getTop() - ry,
                              getRight() + rx,
                              getBottom() + ry));

            // also invalidate our immediate parent to help avoid situations where nearby glows
            // interfere
            ((View)getParent().getParent()).invalidate();
        }
    }

    public void setPressed(boolean pressed) {
        if (mGlowBG != null) {
            if (pressed != isPressed()) {
                if (mPressedAnim != null && mPressedAnim.isRunning()) {
                    mPressedAnim.cancel();
                }
                final AnimatorSet as = mPressedAnim = new AnimatorSet();
                if (pressed) {
                    if (mGlowScale < GLOW_MAX_SCALE_FACTOR) 
                        mGlowScale = GLOW_MAX_SCALE_FACTOR;
                    if (mGlowAlpha < mButtonAlpha)
                        mGlowAlpha = mButtonAlpha; 
                    setDrawingAlpha(1f);
                    as.playTogether(
                        ObjectAnimator.ofFloat(this, "glowAlpha", 1f),
                        ObjectAnimator.ofFloat(this, "glowScale", GLOW_MAX_SCALE_FACTOR)
                    );
                    as.setDuration(mDurationSpeedOff); 
                } else {
                    as.playTogether(
                        ObjectAnimator.ofFloat(this, "glowAlpha", 0f),
                        ObjectAnimator.ofFloat(this, "glowScale", 1f),
                        ObjectAnimator.ofFloat(this, "drawingAlpha", mButtonAlpha) 
                    );
                    as.setDuration(mDurationSpeedOn); 
                }
                as.start();
            }
        }
        super.setPressed(pressed);
    }

    public void setEditMode(boolean editMode) {
        mInEditMode = editMode;
        updateVisibility();
    }

    public void setInfo(NavigationButtons.ButtonInfo buttonInfo, boolean isVertical, boolean isSmall) {
        final Resources res = getResources();
        final int keyDrawableResId;

        mCode = buttonInfo.keyCode;
        mIsSmall = isSmall;

        setTag(buttonInfo);
        setContentDescription(res.getString(buttonInfo.contentDescription));

        if (isSmall) {
            keyDrawableResId = buttonInfo.sideResource;
        } else if (!isVertical) {
            keyDrawableResId = buttonInfo.portResource;
        } else {
            keyDrawableResId = buttonInfo.landResource;
        }

        //Reason for setImageDrawable vs setImageResource is because setImageResource calls relayout() w/o
        //any checks. setImageDrawable performs size checks and only calls relayout if necessary. We rely on this
        //because otherwise the setX/setY attributes which are post layout cause it to mess up the layout.

        setImageDrawable(res.getDrawable(keyDrawableResId));
        updateVisibility();
    }

    private void updateVisibility() {
        if (mInEditMode) {
            setVisibility(View.VISIBLE);
            return;
        }

        NavigationButtons.ButtonInfo buttonInfo = (NavigationButtons.ButtonInfo) getTag();
        if (buttonInfo == NavigationButtons.EMPTY) {
            setVisibility(mIsSmall ? View.INVISIBLE : View.GONE);
        } else if (buttonInfo == NavigationButtons.CONDITIONAL_MENU) {
            setVisibility(View.INVISIBLE);
        }
    }

    private boolean supportsLongPress() {
        return mSupportsLongPress && !NavigationButtons.HOME.equals(getTag());
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (mInEditMode) {
            return false;
        }
        final int action = ev.getAction();
        int x, y;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                //Slog.d("KeyButtonView", "press");
		setHandlingLongpress(false); 
                mDownTime = SystemClock.uptimeMillis();
                setPressed(true);
                if (mCode != 0) {
                    sendEvent(KeyEvent.ACTION_DOWN, 0, mDownTime);
                } else {
                    // Provide the same haptic feedback that the system offers for virtual keys.
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                if (supportsLongPress()) {
                    removeCallbacks(mCheckLongPress);
                    postDelayed(mCheckLongPress, ViewConfiguration.getLongPressTimeout());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                x = (int)ev.getX();
                y = (int)ev.getY();
                setPressed(x >= -mTouchSlop
                        && x < getWidth() + mTouchSlop
                        && y >= -mTouchSlop
                        && y < getHeight() + mTouchSlop);
                break;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                if (mCode != 0) {
                    sendEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED);
                }
                if (supportsLongPress()) {
                    removeCallbacks(mCheckLongPress);
                }
                break;
            case MotionEvent.ACTION_UP:
                final boolean doIt = isPressed();
                setPressed(false);
                if (mCode != 0) {
                    if ((doIt) && (!mHandlingLongpress)) { 
                        sendEvent(KeyEvent.ACTION_UP, 0);
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                        playSoundEffect(SoundEffectConstants.CLICK);
                    } else {
                        sendEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED);
                    }
                } else {
                    // no key code, just a regular ImageView
                    if ((doIt) && (!mHandlingLongpress)) { 
                        performClick();
                    }
                }
                if (supportsLongPress()) {
                    removeCallbacks(mCheckLongPress);
                }
                break;
        }

        return true;
    }

    void sendEvent(int action, int flags) {
        sendEvent(action, flags, SystemClock.uptimeMillis());
    }

    void sendEvent(int action, int flags, long when) {
        final int repeatCount = (flags & KeyEvent.FLAG_LONG_PRESS) != 0 ? 1 : 0;
        final KeyEvent ev = new KeyEvent(mDownTime, when, action, mCode, repeatCount,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags | KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        InputManager.getInstance().injectInputEvent(ev,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    static class GlobalSettingsObserver extends ContentObserver {
        private static GlobalSettingsObserver sInstance;
        private ArrayList<KeyButtonView> mKeyButtonViews = new ArrayList<KeyButtonView>();
        private Context mContext;

        GlobalSettingsObserver(Handler handler, Context context) { 
            super(handler);
	mContext = context.getApplicationContext();
        }

        static GlobalSettingsObserver getInstance(Context context) {
            if (sInstance == null) {
                sInstance = new GlobalSettingsObserver(new Handler(), context);
            }
            return sInstance;
        }

        void attach(KeyButtonView kbv) {
            if (mKeyButtonViews.isEmpty()) {
                observe();
            }
            mKeyButtonViews.add(kbv);
        }

        void detach(KeyButtonView kbv) {
            mKeyButtonViews.remove(kbv);
            if (mKeyButtonViews.isEmpty()) {
                unobserve();
            } 
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_BUTTON_ALPHA),
                    false, this, UserHandle.USER_ALL); 
            resolver.registerContentObserver(
                    Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_GLOW_TINT),
                    false, this, UserHandle.USER_ALL); 
            resolver.registerContentObserver(
                    Settings.System.getUriFor(
                    Settings.System.NAVIGATION_BAR_GLOW_DURATION[1]),
                    false, this, UserHandle.USER_ALL); 
            updateSettings();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        } 

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }

        void updateSettings() {
            ContentResolver resolver = mContext.getContentResolver();
            mDurationSpeedOff = Settings.System.getIntForUser(resolver,
                    Settings.System.NAVIGATION_BAR_GLOW_DURATION[0], 10, UserHandle.USER_CURRENT);
            mDurationSpeedOn = Settings.System.getIntForUser(resolver,
                    Settings.System.NAVIGATION_BAR_GLOW_DURATION[1], 100, UserHandle.USER_CURRENT);
            mButtonAlpha = (1 - (Settings.System.getFloatForUser(
                    resolver, Settings.System.NAVIGATION_BAR_BUTTON_ALPHA, 0.3f, UserHandle.USER_CURRENT)));

            mGlowBGColor = Settings.System.getIntForUser(resolver,
                    Settings.System.NAVIGATION_BAR_GLOW_TINT, -2, UserHandle.USER_CURRENT); 
            if (mGlowBGColor == -2) {
                mGlowBGColor = mContext.getResources().getColor(
                    com.android.internal.R.color.white);
            } 

            for (KeyButtonView kbv : mKeyButtonViews) {

                kbv.setDrawingAlpha(mButtonAlpha); 

                if (kbv.mGlowBG != null) {
                    kbv.mGlowBG.setColorFilter(null);
                    if (mGlowBGColor != -1) {
                        kbv.mGlowBG.setColorFilter(mGlowBGColor, PorterDuff.Mode.SRC_ATOP);
                    }
                }
                kbv.invalidate(); 
            }
        }
    } 
}
