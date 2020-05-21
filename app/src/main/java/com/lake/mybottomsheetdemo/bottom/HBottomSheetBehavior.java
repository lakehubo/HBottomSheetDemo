package com.lake.mybottomsheetdemo.bottom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityViewCommand;
import androidx.customview.view.AbsSavedState;
import androidx.customview.widget.ViewDragHelper;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.lake.mybottomsheetdemo.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

public class HBottomSheetBehavior<V extends View> extends CoordinatorLayout.Behavior<V> {
    /**
     * Callback for monitoring events about bottom sheets.
     */
    public abstract static class BottomSheetCallback {

        /**
         * Called when the bottom sheet changes its state.
         *
         * @param bottomSheet The bottom sheet view.
         * @param newState    The new state. This will be one of {@link #STATE_DRAGGING}, {@link
         *                    #STATE_SETTLING}, {@link #STATE_EXPANDED}, {@link #STATE_COLLAPSED}, {@link
         *                    #STATE_HIDDEN}, or {@link #STATE_HALF_EXPANDED}.
         */
        public abstract void onStateChanged(@NonNull View bottomSheet, @HBottomSheetBehavior.State int newState);

        /**
         * Called when the bottom sheet is being dragged.
         *
         * @param bottomSheet The bottom sheet view.
         * @param slideOffset The new offset of this bottom sheet within [-1,1] range. Offset increases
         *                    as this bottom sheet is moving upward. From 0 to 1 the sheet is between collapsed and
         *                    expanded states and from -1 to 0 it is between hidden and collapsed states.
         */
        public abstract void onSlide(@NonNull View bottomSheet, float slideOffset);
    }

    /**
     * The bottom sheet is dragging.
     */
    public static final int STATE_DRAGGING = 1;

    /**
     * The bottom sheet is settling.
     */
    public static final int STATE_SETTLING = 2;

    /**
     * The bottom sheet is expanded.
     */
    public static final int STATE_EXPANDED = 3;

    /**
     * The bottom sheet is collapsed.
     */
    public static final int STATE_COLLAPSED = 4;

    /**
     * The bottom sheet is hidden.
     */
    public static final int STATE_HIDDEN = 5;

    /**
     * The bottom sheet is half-expanded (used when mFitToContents is false).
     */
    public static final int STATE_HALF_EXPANDED = 6;

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef({
            STATE_EXPANDED,
            STATE_COLLAPSED,
            STATE_DRAGGING,
            STATE_SETTLING,
            STATE_HIDDEN,
            STATE_HALF_EXPANDED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    /**
     * Peek at the 16:9 ratio keyline of its parent.
     *
     * <p>This can be used as a parameter for {@link #setPeekHeight(int)}. {@link #getPeekHeight()}
     * will return this when the value is set.
     */
    public static final int PEEK_HEIGHT_AUTO = -1;

    /**
     * This flag will preserve the peekHeight int value on configuration change.
     */
    public static final int SAVE_PEEK_HEIGHT = 0x1;

    /**
     * This flag will preserve the fitToContents boolean value on configuration change.
     */
    public static final int SAVE_FIT_TO_CONTENTS = 1 << 1;

    /**
     * This flag will preserve the hideable boolean value on configuration change.
     */
    public static final int SAVE_HIDEABLE = 1 << 2;

    /**
     * This flag will preserve the skipCollapsed boolean value on configuration change.
     */
    public static final int SAVE_SKIP_COLLAPSED = 1 << 3;

    /**
     * This flag will preserve all aforementioned values on configuration change.
     */
    public static final int SAVE_ALL = -1;

    /**
     * This flag will not preserve the aforementioned values set at runtime if the view is destroyed
     * and recreated. The only value preserved will be the positional state, e.g. collapsed, hidden,
     * expanded, etc. This is the default behavior.
     */
    public static final int SAVE_NONE = 0;

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef(
            flag = true,
            value = {
                    SAVE_PEEK_HEIGHT,
                    SAVE_FIT_TO_CONTENTS,
                    SAVE_HIDEABLE,
                    SAVE_SKIP_COLLAPSED,
                    SAVE_ALL,
                    SAVE_NONE,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SaveFlags {
    }

    private static final String TAG = "HBottomSheetBehavior";

    @HBottomSheetBehavior.SaveFlags
    private int saveFlags = SAVE_NONE;

    private static final int SIGNIFICANT_VEL_THRESHOLD = 500;//速度阈值

    private static final float HIDE_THRESHOLD = 0.5f;//隐藏阈值

    private static final float HIDE_FRICTION = 0.01f;//隐藏阻尼

    private static final int CORNER_ANIMATION_DURATION = 500;//动画时长

    private boolean fitToContents = false;//适配容器

    private boolean updateImportantForAccessibilityOnSiblings = false;

    private float maximumVelocity;//最大速度

    /**
     * Peek height set by the user.
     */
    private int peekHeight;//可视高度

    /**
     * Whether or not to use automatic peek height.
     */
    private boolean peekHeightAuto;//是否使用自动可视高度

    /**
     * Minimum peek height permitted.
     */
    private int peekHeightMin;//允许的最小可视高度

    /**
     * True if Behavior has a non-null value for the @shapeAppearance attribute
     */
    private boolean shapeThemingEnabled;//shape样式开关

    private MaterialShapeDrawable materialShapeDrawable;//materialShape可绘对象

    /**
     * Default Shape Appearance to be used in bottomsheet
     */
    private ShapeAppearanceModel shapeAppearanceModelDefault;//默认shape样式

    private boolean isShapeExpanded;//shape是否展开

    private HBottomSheetBehavior.SettleRunnable settleRunnable = null;//计算任务

    @Nullable
    private ValueAnimator interpolatorAnimator;//插值器动画

    private static final int DEF_STYLE_RES = R.style.Widget_Design_BottomSheet_Modal;//默认style

    int expandedOffset;//展开偏移

    int fitToContentsOffset;//适配容器偏移

    int halfExpandedOffset;//半展偏移

    float halfExpandedRatio = 0.5f;//半展比例

    int collapsedOffset;//折叠偏移

    float elevation = -1;//图层级别

    boolean hideable;//是否可折叠

    private boolean skipCollapsed;//跳过折叠

    private boolean draggable = true;//是否可拖拽

    @HBottomSheetBehavior.State
    int state = STATE_COLLAPSED;//当前状态-折叠

    @Nullable
    ViewDragHelper viewDragHelper;//拖拽帮助类

    private boolean ignoreEvents;//是否忽略事件

    private int lastNestedScrollDy;//最后嵌套滑动的y值

    private boolean nestedScrolled;//是否嵌套滑动

    int parentWidth;//父容器宽度
    int parentHeight;//父容器高度

    @Nullable
    WeakReference<V> viewRef;//弱引用

    @Nullable
    WeakReference<View> nestedScrollingChildRef;//弱嵌套引用

    @NonNull
    private final ArrayList<BottomSheetCallback> callbacks = new ArrayList<>();//状态回调集合

    @Nullable
    private VelocityTracker velocityTracker;//滑动速度跟踪

    int activePointerId;//活动触点id

    private int initialY;//起始y值

    boolean touchingScrollingChild;//是否触摸滑动子view

    @Nullable
    private Map<View, Integer> importantForAccessibilityMap;//无障碍功能

    public HBottomSheetBehavior() {
    }

    public HBottomSheetBehavior(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BottomSheetBehavior_Layout);
        this.shapeThemingEnabled = a.hasValue(R.styleable.BottomSheetBehavior_Layout_shapeAppearance);
        boolean hasBackgroundTint = false;
        createMaterialShapeDrawable(context, attrs, hasBackgroundTint);
        createShapeValueAnimator();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.elevation = a.getDimension(R.styleable.BottomSheetBehavior_Layout_android_elevation, -1);
        }

        TypedValue value = a.peekValue(R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight);
        if (value != null && value.data == PEEK_HEIGHT_AUTO) {
            setPeekHeight(value.data);
        } else {
            setPeekHeight(
                    a.getDimensionPixelSize(
                            R.styleable.BottomSheetBehavior_Layout_behavior_peekHeight, PEEK_HEIGHT_AUTO));
        }
        setHideable(a.getBoolean(R.styleable.BottomSheetBehavior_Layout_behavior_hideable, false));
        setFitToContents(
                a.getBoolean(R.styleable.BottomSheetBehavior_Layout_behavior_fitToContents, true));
        setSkipCollapsed(
                a.getBoolean(R.styleable.BottomSheetBehavior_Layout_behavior_skipCollapsed, false));
        setDraggable(a.getBoolean(R.styleable.BottomSheetBehavior_Layout_behavior_draggable, true));
        setSaveFlags(a.getInt(R.styleable.BottomSheetBehavior_Layout_behavior_saveFlags, SAVE_NONE));
        setHalfExpandedRatio(
                a.getFloat(R.styleable.BottomSheetBehavior_Layout_behavior_halfExpandedRatio, 0.5f));
        setExpandedOffset(a.getInt(R.styleable.BottomSheetBehavior_Layout_behavior_expandedOffset, 0));
        a.recycle();
        ViewConfiguration configuration = ViewConfiguration.get(context);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    @NonNull
    @Override//保存状态
    public Parcelable onSaveInstanceState(@NonNull CoordinatorLayout parent, @NonNull V child) {
        return new HBottomSheetBehavior.SavedState(super.onSaveInstanceState(parent, child), this);
    }

    @Override//恢复状态
    public void onRestoreInstanceState(
            @NonNull CoordinatorLayout parent, @NonNull V child, @NonNull Parcelable state) {
        HBottomSheetBehavior.SavedState ss = (HBottomSheetBehavior.SavedState) state;
        super.onRestoreInstanceState(parent, child, ss.getSuperState());
        // Restore Optional State values designated by saveFlags
        restoreOptionalState(ss);
        // Intermediate states are restored as collapsed state
        if (ss.state == STATE_DRAGGING || ss.state == STATE_SETTLING) {
            this.state = STATE_COLLAPSED;
        } else {
            this.state = ss.state;
        }
    }

    @Override
    public void onAttachedToLayoutParams(@NonNull CoordinatorLayout.LayoutParams layoutParams) {
        super.onAttachedToLayoutParams(layoutParams);
        // These may already be null, but just be safe, explicitly assign them. This lets us know the
        // first time we layout with this behavior by checking (viewRef == null).
        viewRef = null;
        viewDragHelper = null;
    }

    @Override
    public void onDetachedFromLayoutParams() {
        super.onDetachedFromLayoutParams();
        // Release references so we don't run unnecessary codepaths while not attached to a view.
        viewRef = null;
        viewDragHelper = null;
    }

    @Override
    public boolean onLayoutChild(
            @NonNull CoordinatorLayout parent, @NonNull V child, int layoutDirection) {
        if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
            child.setFitsSystemWindows(true);//适配系统窗口
        }

        if (viewRef == null) {
            // First layout with this behavior.
            peekHeightMin = parent.getResources().getDimensionPixelSize(R.dimen.h_design_bottom_sheet_peek_height_min);
            viewRef = new WeakReference<>(child);
            // Only set MaterialShapeDrawable as background if shapeTheming is enabled, otherwise will
            // default to android:background declared in styles or layout.
            if (shapeThemingEnabled && materialShapeDrawable != null) {
                ViewCompat.setBackground(child, materialShapeDrawable);
            }
            // Set elevation on MaterialShapeDrawable
            if (materialShapeDrawable != null) {//可绘制drawable图层层级设置
                // Use elevation attr if set on bottomsheet; otherwise, use elevation of child view.
                materialShapeDrawable.setElevation(
                        elevation == -1 ? ViewCompat.getElevation(child) : elevation);
                // Update the material shape based on initial state.
                isShapeExpanded = state == STATE_EXPANDED;//初始状态是否为全展开
                materialShapeDrawable.setInterpolation(isShapeExpanded ? 0f : 1f);//设置缩放，全展开时缩放0，否则缩放1
            }
            updateAccessibilityActions();//更新当前可执行状态
            if (ViewCompat.getImportantForAccessibility(child)
                    == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {//是否开启无障碍功能
                ViewCompat.setImportantForAccessibility(child, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
        }
        if (viewDragHelper == null) {//初始化拖拽工具
            viewDragHelper = ViewDragHelper.create(parent, dragCallback);
        }

        int savedTop = child.getTop();//子view的顶部坐标
        // First let the parent lay it out
        parent.onLayoutChild(child, layoutDirection);//调用显示子视图
        // Offset the bottom sheet
        parentWidth = parent.getWidth();//保存当前容器宽度
        parentHeight = parent.getHeight();//保存当前容器高度
        fitToContentsOffset = Math.max(0, parentHeight - child.getHeight());//留白偏移
        calculateHalfExpandedOffset();//计算半展偏移
        calculateCollapsedOffset();//计算折叠偏移

        //设置当前状态
        if (state == STATE_EXPANDED) {//展开
            ViewCompat.offsetTopAndBottom(child, getExpandedOffset());
        } else if (state == STATE_HALF_EXPANDED) {//半展
            ViewCompat.offsetTopAndBottom(child, halfExpandedOffset);
        } else if (hideable && state == STATE_HIDDEN) {//隐藏
            ViewCompat.offsetTopAndBottom(child, parentHeight);
        } else if (state == STATE_COLLAPSED) {//折叠
            ViewCompat.offsetTopAndBottom(child, collapsedOffset);
        } else if (state == STATE_DRAGGING || state == STATE_SETTLING) {//拖拽
            ViewCompat.offsetTopAndBottom(child, savedTop - child.getTop());
        }

        nestedScrollingChildRef = new WeakReference<>(findScrollingChild(child));//可滑动嵌套的子view
        return true;
    }

    @Override//手势拦截触摸事件
    public boolean onInterceptTouchEvent(
            @NonNull CoordinatorLayout parent, @NonNull V child, @NonNull MotionEvent event) {
        if (!child.isShown() || !draggable) {
            ignoreEvents = true;
            return false;
        }
        int action = event.getActionMasked();
        // Record the velocity 记录速度
        if (action == MotionEvent.ACTION_DOWN) {
            reset();//重置速度追踪器
        }
        if (velocityTracker == null) {//初始化一个速度追踪器
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);//速度追踪器绑定事件
        switch (action) {
            case MotionEvent.ACTION_UP://手势抬起或者取消时
            case MotionEvent.ACTION_CANCEL:
                touchingScrollingChild = false;
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                // Reset the ignore flag
                if (ignoreEvents) {
                    ignoreEvents = false;
                    return false;
                }
                break;
            case MotionEvent.ACTION_DOWN://手势按下时
                int initialX = (int) event.getX();//手势起点坐标x值
                initialY = (int) event.getY();//手势起点坐标y值
                // Only intercept nested scrolling events here if the view not being moved by the
                // ViewDragHelper.
                if (state != STATE_SETTLING) {//当前非自动滚动状态
                    View scroll = nestedScrollingChildRef != null ? nestedScrollingChildRef.get() : null;//获取正在滑动的view
                    if (scroll != null && parent.isPointInChildBounds(scroll, initialX, initialY)) {//确定触摸点是在正在滑动的view上
                        activePointerId = event.getPointerId(event.getActionIndex());//记录活动的触摸id
                        touchingScrollingChild = true;
                    }
                }
                ignoreEvents =
                        activePointerId == MotionEvent.INVALID_POINTER_ID
                                && !parent.isPointInChildBounds(child, initialX, initialY);//是否忽略事件
                break;
            default: // fall out
        }
        if (!ignoreEvents
                && viewDragHelper != null
                && viewDragHelper.shouldInterceptTouchEvent(event)) {//触发子view拖拽位移
            return true;
        }
        // We have to handle cases that the ViewDragHelper does not capture the bottom sheet because
        // it is not the top most view of its parent. This is not necessary when the touch event is
        // happening over the scrolling content as nested scrolling logic handles that case.
        View scroll = nestedScrollingChildRef != null ? nestedScrollingChildRef.get() : null;
        return action == MotionEvent.ACTION_MOVE
                && scroll != null
                && !ignoreEvents
                && state != STATE_DRAGGING
                && !parent.isPointInChildBounds(scroll, (int) event.getX(), (int) event.getY())
                && viewDragHelper != null
                && Math.abs(initialY - event.getY()) > viewDragHelper.getTouchSlop();//最小拖拽位移
    }

    @Override
    public boolean onTouchEvent(@NonNull CoordinatorLayout parent, @NonNull V child, @NonNull MotionEvent event) {
        if (!child.isShown()) {
            return false;
        }
        int action = event.getActionMasked();
        if (state == STATE_DRAGGING && action == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (viewDragHelper != null) {
            viewDragHelper.processTouchEvent(event);
        }
        // Record the velocity
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);
        // The ViewDragHelper tries to capture only the top-most View. We have to explicitly tell it
        // to capture the bottom sheet in case it is not captured and the touch slop is passed.
        if (action == MotionEvent.ACTION_MOVE && !ignoreEvents) {
            if (Math.abs(initialY - event.getY()) > viewDragHelper.getTouchSlop()) {//拖拽距离大于最小阈值
                viewDragHelper.captureChildView(child, event.getPointerId(event.getActionIndex()));//对view进行拖拽计算
            }
        }
        return !ignoreEvents;
    }

    @Override
    public boolean onStartNestedScroll(
            @NonNull CoordinatorLayout coordinatorLayout,
            @NonNull V child,
            @NonNull View directTargetChild,
            @NonNull View target,
            int axes,
            int type) {
        lastNestedScrollDy = 0;
        nestedScrolled = false;
        return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override//开始滑动
    public void onNestedPreScroll(
            @NonNull CoordinatorLayout coordinatorLayout,
            @NonNull V child,
            @NonNull View target,
            int dx,
            int dy,
            @NonNull int[] consumed,
            int type) {
        if (type == ViewCompat.TYPE_NON_TOUCH) {
            // Ignore fling here. The ViewDragHelper handles it.
            return;
        }
        View scrollingChild = nestedScrollingChildRef != null ? nestedScrollingChildRef.get() : null;
        if (target != scrollingChild) {
            return;
        }
        int currentTop = child.getTop();
        int newTop = currentTop - dy;
        if (dy > 0) { // Upward
            if (newTop < getExpandedOffset()) {
                consumed[1] = currentTop - getExpandedOffset();
                ViewCompat.offsetTopAndBottom(child, -consumed[1]);
                setStateInternal(STATE_EXPANDED);
            } else {
                if (!draggable) {
                    // Prevent dragging
                    return;
                }

                consumed[1] = dy;
                ViewCompat.offsetTopAndBottom(child, -dy);
                setStateInternal(STATE_DRAGGING);
            }
        } else if (dy < 0) { // Downward
            if (!target.canScrollVertically(-1)) {
                if (newTop <= collapsedOffset || hideable) {
                    if (!draggable) {
                        // Prevent dragging
                        return;
                    }

                    consumed[1] = dy;
                    ViewCompat.offsetTopAndBottom(child, -dy);
                    setStateInternal(STATE_DRAGGING);
                } else {
                    consumed[1] = currentTop - collapsedOffset;
                    ViewCompat.offsetTopAndBottom(child, -consumed[1]);
                    setStateInternal(STATE_COLLAPSED);
                }
            }
        }
        dispatchOnSlide(child.getTop());
        lastNestedScrollDy = dy;
        nestedScrolled = true;
    }

    @Override//停止滑动
    public void onStopNestedScroll(
            @NonNull CoordinatorLayout coordinatorLayout,
            @NonNull V child,
            @NonNull View target,
            int type) {
        if (child.getTop() == getExpandedOffset()) {
            setStateInternal(STATE_EXPANDED);
            return;
        }
        if (nestedScrollingChildRef == null
                || target != nestedScrollingChildRef.get()
                || !nestedScrolled) {
            return;
        }
        int top;
        int targetState;
        if (lastNestedScrollDy > 0) {
            if (fitToContents) {
                top = fitToContentsOffset;
                targetState = STATE_EXPANDED;
            } else {
                int currentTop = child.getTop();
                if (currentTop > halfExpandedOffset) {
                    top = halfExpandedOffset;
                    targetState = STATE_HALF_EXPANDED;
                } else {
                    top = expandedOffset;
                    targetState = STATE_EXPANDED;
                }
            }
        } else if (hideable && shouldHide(child, getYVelocity())) {
            top = parentHeight;
            targetState = STATE_HIDDEN;
        } else if (lastNestedScrollDy == 0) {
            int currentTop = child.getTop();
            if (fitToContents) {
                if (Math.abs(currentTop - fitToContentsOffset) < Math.abs(currentTop - collapsedOffset)) {
                    top = fitToContentsOffset;
                    targetState = STATE_EXPANDED;
                } else {
                    top = collapsedOffset;
                    targetState = STATE_COLLAPSED;
                }
            } else {
                if (currentTop < halfExpandedOffset) {
                    if (currentTop < Math.abs(currentTop - collapsedOffset)) {
                        top = expandedOffset;
                        targetState = STATE_EXPANDED;
                    } else {
                        top = halfExpandedOffset;
                        targetState = STATE_HALF_EXPANDED;
                    }
                } else {
                    if (Math.abs(currentTop - halfExpandedOffset) < Math.abs(currentTop - collapsedOffset)) {
                        top = halfExpandedOffset;
                        targetState = STATE_HALF_EXPANDED;
                    } else {
                        top = collapsedOffset;
                        targetState = STATE_COLLAPSED;
                    }
                }
            }
        } else {
            if (fitToContents) {
                top = collapsedOffset;
                targetState = STATE_COLLAPSED;
            } else {
                // Settle to nearest height.
                int currentTop = child.getTop();
                if (Math.abs(currentTop - halfExpandedOffset) < Math.abs(currentTop - collapsedOffset)) {
                    top = halfExpandedOffset;
                    targetState = STATE_HALF_EXPANDED;
                } else {
                    top = collapsedOffset;
                    targetState = STATE_COLLAPSED;
                }
            }
        }
        startSettlingAnimation(child, targetState, top, false);
        nestedScrolled = false;
    }

    @Override
    public void onNestedScroll(
            @NonNull CoordinatorLayout coordinatorLayout,
            @NonNull V child,
            @NonNull View target,
            int dxConsumed,
            int dyConsumed,
            int dxUnconsumed,
            int dyUnconsumed,
            int type,
            @NonNull int[] consumed) {
        // Overridden to prevent the default consumption of the entire scroll distance.
    }

    @Override
    public boolean onNestedPreFling(
            @NonNull CoordinatorLayout coordinatorLayout,
            @NonNull V child,
            @NonNull View target,
            float velocityX,
            float velocityY) {
        if (nestedScrollingChildRef != null) {
            return target == nestedScrollingChildRef.get()
                    && (state != STATE_EXPANDED
                    || super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY));
        } else {
            return false;
        }
    }

    /**
     * @return whether the height of the expanded sheet is determined by the height of its contents,
     * or if it is expanded in two stages (half the height of the parent container, full height of
     * parent container).
     */
    public boolean isFitToContents() {
        return fitToContents;
    }

    /**
     * Sets whether the height of the expanded sheet is determined by the height of its contents, or
     * if it is expanded in two stages (half the height of the parent container, full height of parent
     * container). Default value is true.
     *
     * @param fitToContents whether or not to fit the expanded sheet to its contents.
     */
    public void setFitToContents(boolean fitToContents) {
        if (this.fitToContents == fitToContents) {
            return;
        }
        this.fitToContents = fitToContents;

        // If sheet is already laid out, recalculate the collapsed offset based on new setting.
        // Otherwise, let onLayoutChild handle this later.
        if (viewRef != null) {
            calculateCollapsedOffset();
        }
        // Fix incorrect expanded settings depending on whether or not we are fitting sheet to contents.
        setStateInternal((this.fitToContents && state == STATE_HALF_EXPANDED) ? STATE_EXPANDED : state);

        updateAccessibilityActions();
    }

    /**
     * Sets the height of the bottom sheet when it is collapsed.
     *
     * @param peekHeight The height of the collapsed bottom sheet in pixels, or {@link
     *                   #PEEK_HEIGHT_AUTO} to configure the sheet to peek automatically at 16:9 ratio keyline.
     * @attr ref
     * com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_peekHeight
     */
    public void setPeekHeight(int peekHeight) {
        setPeekHeight(peekHeight, false);
    }

    /**
     * Sets the height of the bottom sheet when it is collapsed while optionally animating between the
     * old height and the new height.
     *
     * @param peekHeight The height of the collapsed bottom sheet in pixels, or {@link
     *                   #PEEK_HEIGHT_AUTO} to configure the sheet to peek automatically at 16:9 ratio keyline.
     * @param animate    Whether to animate between the old height and the new height.
     * @attr ref
     * com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_peekHeight
     */
    public final void setPeekHeight(int peekHeight, boolean animate) {
        boolean layout = false;
        if (peekHeight == PEEK_HEIGHT_AUTO) {
            if (!peekHeightAuto) {
                peekHeightAuto = true;
                layout = true;
            }
        } else if (peekHeightAuto || this.peekHeight != peekHeight) {
            peekHeightAuto = false;
            this.peekHeight = Math.max(0, peekHeight);
            layout = true;
        }
        // If sheet is already laid out, recalculate the collapsed offset based on new setting.
        // Otherwise, let onLayoutChild handle this later.
        if (layout && viewRef != null) {
            calculateCollapsedOffset();
            if (state == STATE_COLLAPSED) {
                V view = viewRef.get();
                if (view != null) {
                    if (animate) {
                        settleToStatePendingLayout(state);
                    } else {
                        view.requestLayout();
                    }
                }
            }
        }
    }

    /**
     * Gets the height of the bottom sheet when it is collapsed.
     *
     * @return The height of the collapsed bottom sheet in pixels, or {@link #PEEK_HEIGHT_AUTO} if the
     * sheet is configured to peek automatically at 16:9 ratio keyline
     * @attr ref
     * com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_peekHeight
     */
    public int getPeekHeight() {
        return peekHeightAuto ? PEEK_HEIGHT_AUTO : peekHeight;
    }

    /**
     * Determines the height of the BottomSheet in the {@link #STATE_HALF_EXPANDED} state. The
     * material guidelines recommended a value of 0.5, which results in the sheet filling half of the
     * parent. The height of the BottomSheet will be smaller as this ratio is decreased and taller as
     * it is increased. The default value is 0.5.
     *
     * @param ratio a float between 0 and 1, representing the {@link #STATE_HALF_EXPANDED} ratio.
     * @attr com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_halfExpandedRatio
     */
    public void setHalfExpandedRatio(@FloatRange(from = 0.0f, to = 1.0f) float ratio) {

        if ((ratio <= 0) || (ratio >= 1)) {
            throw new IllegalArgumentException("ratio must be a float value between 0 and 1");
        }
        this.halfExpandedRatio = ratio;
        // If sheet is already laid out, recalculate the half expanded offset based on new setting.
        // Otherwise, let onLayoutChild handle this later.
        if (viewRef != null) {
            calculateHalfExpandedOffset();
        }
    }

    /**
     * Determines the top offset of the BottomSheet in the {@link #STATE_EXPANDED} state when
     * fitsToContent is false. The default value is 0, which results in the sheet matching the
     * parent's top.
     *
     * @param offset an integer value greater than equal to 0, representing the {@link
     *               #STATE_EXPANDED} offset. Value must not exceed the offset in the half expanded state.
     * @attr com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_expandedOffset
     */
    public void setExpandedOffset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be greater than or equal to 0");
        }
        this.expandedOffset = offset;
    }

    /**
     * Gets the ratio for the height of the BottomSheet in the {@link #STATE_HALF_EXPANDED} state.
     *
     * @attr com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_halfExpandedRatio
     */
    @FloatRange(from = 0.0f, to = 1.0f)
    public float getHalfExpandedRatio() {
        return halfExpandedRatio;
    }

    /**
     * Sets whether this bottom sheet can hide when it is swiped down.
     *
     * @param hideable {@code true} to make this bottom sheet hideable.
     * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_hideable
     */
    public void setHideable(boolean hideable) {
        if (this.hideable != hideable) {
            this.hideable = hideable;
            if (!hideable && state == STATE_HIDDEN) {
                // Lift up to collapsed state
                setState(STATE_COLLAPSED);
            }
            updateAccessibilityActions();
        }
    }

    /**
     * Gets whether this bottom sheet can hide when it is swiped down.
     *
     * @return {@code true} if this bottom sheet can hide.
     * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_hideable
     */
    public boolean isHideable() {
        return hideable;
    }

    /**
     * Sets whether this bottom sheet should skip the collapsed state when it is being hidden after it
     * is expanded once. Setting this to true has no effect unless the sheet is hideable.
     *
     * @param skipCollapsed True if the bottom sheet should skip the collapsed state.
     * @attr ref
     * com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_skipCollapsed
     */
    public void setSkipCollapsed(boolean skipCollapsed) {
        this.skipCollapsed = skipCollapsed;
    }

    /**
     * Sets whether this bottom sheet should skip the collapsed state when it is being hidden after it
     * is expanded once.
     *
     * @return Whether the bottom sheet should skip the collapsed state.
     * @attr ref
     * com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_skipCollapsed
     */
    public boolean getSkipCollapsed() {
        return skipCollapsed;
    }

    /**
     * Sets whether this bottom sheet is can be collapsed/expanded by dragging. Note: When disabling
     * dragging, an app will require to implement a custom way to expand/collapse the bottom sheet
     *
     * @param draggable {@code false} to prevent dragging the sheet to collapse and expand
     * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_draggable
     */
    public void setDraggable(boolean draggable) {
        this.draggable = draggable;
    }

    public boolean isDraggable() {
        return draggable;
    }

    /**
     * Sets save flags to be preserved in bottomsheet on configuration change.
     *
     * @param flags bitwise int of {@link #SAVE_PEEK_HEIGHT}, {@link #SAVE_FIT_TO_CONTENTS}, {@link
     *              #SAVE_HIDEABLE}, {@link #SAVE_SKIP_COLLAPSED}, {@link #SAVE_ALL} and {@link #SAVE_NONE}.
     * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_saveFlags
     * @see #getSaveFlags()
     */
    public void setSaveFlags(@HBottomSheetBehavior.SaveFlags int flags) {
        this.saveFlags = flags;
    }

    /**
     * Returns the save flags.
     *
     * @attr ref com.google.android.material.R.styleable#BottomSheetBehavior_Layout_behavior_saveFlags
     * @see #setSaveFlags(int)
     */
    @HBottomSheetBehavior.SaveFlags
    public int getSaveFlags() {
        return this.saveFlags;
    }

    /**
     * Sets a callback to be notified of bottom sheet events.
     *
     * @param callback The callback to notify when bottom sheet events occur.
     * @deprecated use {@link #addBottomSheetCallback(HBottomSheetBehavior.BottomSheetCallback)} and {@link
     * #removeBottomSheetCallback(HBottomSheetBehavior.BottomSheetCallback)} instead
     */
    @Deprecated
    public void setBottomSheetCallback(HBottomSheetBehavior.BottomSheetCallback callback) {
        Log.w(
                TAG,
                "BottomSheetBehavior now supports multiple callbacks. `setBottomSheetCallback()` removes"
                        + " all existing callbacks, including ones set internally by library authors, which"
                        + " may result in unintended behavior. This may change in the future. Please use"
                        + " `addBottomSheetCallback()` and `removeBottomSheetCallback()` instead to set your"
                        + " own callbacks.");
        callbacks.clear();
        if (callback != null) {
            callbacks.add(callback);
        }
    }

    /**
     * Adds a callback to be notified of bottom sheet events.
     *
     * @param callback The callback to notify when bottom sheet events occur.
     */
    public void addBottomSheetCallback(@NonNull HBottomSheetBehavior.BottomSheetCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    /**
     * Removes a previously added callback.
     *
     * @param callback The callback to remove.
     */
    public void removeBottomSheetCallback(@NonNull HBottomSheetBehavior.BottomSheetCallback callback) {
        callbacks.remove(callback);
    }

    /**
     * Sets the state of the bottom sheet. The bottom sheet will transition to that state with
     * animation.
     *
     * @param state One of {@link #STATE_COLLAPSED}, {@link #STATE_EXPANDED}, {@link #STATE_HIDDEN},
     *              or {@link #STATE_HALF_EXPANDED}.
     */
    public void setState(@HBottomSheetBehavior.State int state) {
        if (state == this.state) {
            return;
        }
        if (viewRef == null) {
            // The view is not laid out yet; modify mState and let onLayoutChild handle it later
            if (state == STATE_COLLAPSED
                    || state == STATE_EXPANDED
                    || state == STATE_HALF_EXPANDED
                    || (hideable && state == STATE_HIDDEN)) {
                this.state = state;
            }
            return;
        }
        settleToStatePendingLayout(state);
    }

    private void settleToStatePendingLayout(@HBottomSheetBehavior.State int state) {
        final V child = viewRef.get();
        if (child == null) {
            return;
        }
        // Start the animation; wait until a pending layout if there is one.
        ViewParent parent = child.getParent();
        if (parent != null && parent.isLayoutRequested() && ViewCompat.isAttachedToWindow(child)) {
            final int finalState = state;
            child.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            settleToState(child, finalState);
                        }
                    });
        } else {
            settleToState(child, state);
        }
    }

    /**
     * Gets the current state of the bottom sheet.
     *
     * @return One of {@link #STATE_EXPANDED}, {@link #STATE_HALF_EXPANDED}, {@link #STATE_COLLAPSED},
     * {@link #STATE_DRAGGING}, {@link #STATE_SETTLING}, or {@link #STATE_HALF_EXPANDED}.
     */
    @HBottomSheetBehavior.State
    public int getState() {
        return state;
    }

    /**
     * 设置内部状态
     *
     * @param state
     */
    void setStateInternal(@HBottomSheetBehavior.State int state) {
        if (this.state == state) {
            return;
        }
        this.state = state;

        if (viewRef == null) {
            return;
        }

        View bottomSheet = viewRef.get();
        if (bottomSheet == null) {
            return;
        }

        if (state == STATE_EXPANDED) {
            updateImportantForAccessibility(true);
        } else if (state == STATE_HALF_EXPANDED || state == STATE_HIDDEN || state == STATE_COLLAPSED) {
            updateImportantForAccessibility(false);
        }

        updateDrawableForTargetState(state);
        for (int i = 0; i < callbacks.size(); i++) {
            callbacks.get(i).onStateChanged(bottomSheet, state);
        }
        updateAccessibilityActions();
    }

    private void updateDrawableForTargetState(@HBottomSheetBehavior.State int state) {
        if (state == STATE_SETTLING) {
            // Special case: we want to know which state we're settling to, so wait for another call.
            return;
        }

        boolean expand = state == STATE_EXPANDED;
        if (isShapeExpanded != expand) {
            isShapeExpanded = expand;
            if (materialShapeDrawable != null && interpolatorAnimator != null) {
                if (interpolatorAnimator.isRunning()) {
                    interpolatorAnimator.reverse();
                } else {
                    float to = expand ? 0f : 1f;
                    float from = 1f - to;
                    interpolatorAnimator.setFloatValues(from, to);
                    interpolatorAnimator.start();
                }
            }
        }
    }

    /**
     * 计算可视高度（可视的距离）
     *
     * @return
     */
    private int calculatePeekHeight() {
        if (peekHeightAuto) {
            return Math.max(peekHeightMin, parentHeight - parentWidth * 9 / 16);
        }
        return peekHeight;
    }

    /**
     * 计算折叠偏移（折叠的距离）
     */
    private void calculateCollapsedOffset() {
        int peek = calculatePeekHeight();

        if (fitToContents) {//是否适配内容
            collapsedOffset = Math.max(parentHeight - peek, fitToContentsOffset);
        } else {
            collapsedOffset = parentHeight - peek;
        }
    }

    /**
     * 计算半展偏移（半展的距离）
     */
    private void calculateHalfExpandedOffset() {
        this.halfExpandedOffset = (int) (parentHeight * (1 - halfExpandedRatio));
    }

    /**
     * 重置触摸点
     */
    private void reset() {
        activePointerId = ViewDragHelper.INVALID_POINTER;
        if (velocityTracker != null) {//销毁存在的速度跟踪器
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void restoreOptionalState(@NonNull HBottomSheetBehavior.SavedState ss) {
        if (this.saveFlags == SAVE_NONE) {
            return;
        }
        if (this.saveFlags == SAVE_ALL || (this.saveFlags & SAVE_PEEK_HEIGHT) == SAVE_PEEK_HEIGHT) {
            this.peekHeight = ss.peekHeight;
        }
        if (this.saveFlags == SAVE_ALL
                || (this.saveFlags & SAVE_FIT_TO_CONTENTS) == SAVE_FIT_TO_CONTENTS) {
            this.fitToContents = ss.fitToContents;
        }
        if (this.saveFlags == SAVE_ALL || (this.saveFlags & SAVE_HIDEABLE) == SAVE_HIDEABLE) {
            this.hideable = ss.hideable;
        }
        if (this.saveFlags == SAVE_ALL
                || (this.saveFlags & SAVE_SKIP_COLLAPSED) == SAVE_SKIP_COLLAPSED) {
            this.skipCollapsed = ss.skipCollapsed;
        }
    }

    /**
     * 是否隐藏
     *
     * @param child
     * @param yvel
     * @return
     */
    boolean shouldHide(@NonNull View child, float yvel) {
        if (skipCollapsed) {//跳过折叠判断，则直接折叠
            return true;
        }
        if (child.getTop() < collapsedOffset) {//高于折叠线不折叠
            // It should not hide, but collapse.
            return false;
        }
        int peek = calculatePeekHeight();//可视高度
        final float newTop = child.getTop() + yvel * HIDE_FRICTION;//目标新高度
        return Math.abs(newTop - collapsedOffset) / (float) peek > HIDE_THRESHOLD;//新高度距离折叠线大于半个可视高度则隐藏
    }

    @Nullable
    @VisibleForTesting
    View findScrollingChild(View view) {
        if (ViewCompat.isNestedScrollingEnabled(view)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                View scrollingChild = findScrollingChild(group.getChildAt(i));
                if (scrollingChild != null) {
                    return scrollingChild;
                }
            }
        }
        return null;
    }

    private void createMaterialShapeDrawable(
            @NonNull Context context, AttributeSet attrs, boolean hasBackgroundTint) {
        this.createMaterialShapeDrawable(context, attrs, hasBackgroundTint, null);
    }

    private void createMaterialShapeDrawable(
            @NonNull Context context,
            AttributeSet attrs,
            boolean hasBackgroundTint,
            @Nullable ColorStateList bottomSheetColor) {
        if (this.shapeThemingEnabled) {
            this.shapeAppearanceModelDefault =
                    ShapeAppearanceModel.builder(context, attrs, R.attr.bottomSheetStyle, DEF_STYLE_RES)
                            .build();

            this.materialShapeDrawable = new MaterialShapeDrawable(shapeAppearanceModelDefault);
            this.materialShapeDrawable.initializeElevationOverlay(context);

            if (hasBackgroundTint && bottomSheetColor != null) {
                materialShapeDrawable.setFillColor(bottomSheetColor);
            } else {
                // If the tint isn't set, use the theme default background color.
                TypedValue defaultColor = new TypedValue();
                context.getTheme().resolveAttribute(android.R.attr.colorBackground, defaultColor, true);
                materialShapeDrawable.setTint(defaultColor.data);
            }
        }
    }

    private void createShapeValueAnimator() {
        interpolatorAnimator = ValueAnimator.ofFloat(0f, 1f);
        interpolatorAnimator.setDuration(CORNER_ANIMATION_DURATION);
        interpolatorAnimator.addUpdateListener(
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                        float value = (float) animation.getAnimatedValue();
                        if (materialShapeDrawable != null) {
                            materialShapeDrawable.setInterpolation(value);
                        }
                    }
                });
    }

    private float getYVelocity() {
        if (velocityTracker == null) {
            return 0;
        }
        velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
        return velocityTracker.getYVelocity(activePointerId);
    }

    private int getExpandedOffset() {
        return fitToContents ? fitToContentsOffset : expandedOffset;
    }

    void settleToState(@NonNull View child, int state) {
        int top;
        if (state == STATE_COLLAPSED) {
            top = collapsedOffset;
        } else if (state == STATE_HALF_EXPANDED) {
            top = halfExpandedOffset;
            if (fitToContents && top <= fitToContentsOffset) {
                // Skip to the expanded state if we would scroll past the height of the contents.
                state = STATE_EXPANDED;
                top = fitToContentsOffset;
            }
        } else if (state == STATE_EXPANDED) {
            top = getExpandedOffset();
        } else if (hideable && state == STATE_HIDDEN) {
            top = parentHeight;
        } else {
            throw new IllegalArgumentException("Illegal state argument: " + state);
        }
        startSettlingAnimation(child, state, top, false);
    }

    void startSettlingAnimation(View child, int state, int top, boolean settleFromViewDragHelper) {
        boolean startedSettling =
                settleFromViewDragHelper
                        ? viewDragHelper.settleCapturedViewAt(child.getLeft(), top)
                        : viewDragHelper.smoothSlideViewTo(child, child.getLeft(), top);
        if (startedSettling) {
            setStateInternal(STATE_SETTLING);
            // STATE_SETTLING won't animate the material shape, so do that here with the target state.
            updateDrawableForTargetState(state);
            if (settleRunnable == null) {
                // If the singleton SettleRunnable instance has not been instantiated, create it.
                settleRunnable = new HBottomSheetBehavior.SettleRunnable(child, state);
            }
            // If the SettleRunnable has not been posted, post it with the correct state.
            if (settleRunnable.isPosted == false) {
                settleRunnable.targetState = state;
                ViewCompat.postOnAnimation(child, settleRunnable);
                settleRunnable.isPosted = true;
            } else {
                // Otherwise, if it has been posted, just update the target state.
                settleRunnable.targetState = state;
            }
        } else {
            setStateInternal(state);
        }
    }

    /**
     * 拖拽事件回调
     */
    private final ViewDragHelper.Callback dragCallback =
            new ViewDragHelper.Callback() {
                /**
                 * 是否捕获当前view
                 * @param child
                 * @param pointerId
                 * @return
                 */
                @Override
                public boolean tryCaptureView(@NonNull View child, int pointerId) {
                    if (state == STATE_DRAGGING) {
                        return false;
                    }
                    if (touchingScrollingChild) {
                        return false;
                    }
                    if (state == STATE_EXPANDED && activePointerId == pointerId) {
                        View scroll = nestedScrollingChildRef != null ? nestedScrollingChildRef.get() : null;
                        if (scroll != null && scroll.canScrollVertically(-1)) {//检查此视图是否可以在y方向上垂直滚动
                            // Let the content scroll up先滚动嵌套的子view
                            return false;
                        }
                    }
                    return viewRef != null && viewRef.get() == child;
                }

                /**
                 * 拖拽view的位置变化
                 * @param changedView
                 * @param left
                 * @param top
                 * @param dx
                 * @param dy
                 */
                @Override
                public void onViewPositionChanged(
                        @NonNull View changedView, int left, int top, int dx, int dy) {
                    dispatchOnSlide(top);
                }

                /**
                 * 拖拽状态回调
                 * @param state
                 */
                @Override
                public void onViewDragStateChanged(int state) {
                    if (state == ViewDragHelper.STATE_DRAGGING && draggable) {
                        setStateInternal(STATE_DRAGGING);
                    }
                }

                /**
                 * 高度是否是低于父容器以及全展高度和的一半
                 * @param child
                 * @return
                 */
                private boolean releasedLow(@NonNull View child) {
                    // Needs to be at least half way to the bottom.至少要到底部一半
                    return child.getTop() > (parentHeight + getExpandedOffset()) / 2;
                }

                /**
                 * 释放拖拽的view
                 * @param releasedChild
                 * @param xvel x方向上的速度
                 * @param yvel y方向上的速度
                 */
                @Override
                public void onViewReleased(@NonNull View releasedChild, float xvel, float yvel) {
                    int top;//目标高度位置
                    @HBottomSheetBehavior.State int targetState;
                    if (yvel < 0) { // Moving up 向上移动
                        if (fitToContents) {//适配内容
                            top = fitToContentsOffset;
                            targetState = STATE_EXPANDED;//全展
                        } else {
                            int currentTop = releasedChild.getTop();//当前高度
                            if (currentTop > halfExpandedOffset) {//低于半展高度则半展
                                top = halfExpandedOffset;
                                targetState = STATE_HALF_EXPANDED;
                            } else {//否则全展
                                top = expandedOffset;
                                targetState = STATE_EXPANDED;
                            }
                        }
                        Log.e(TAG, "onViewReleased: yvel < 0 ,top=" + top + ",state=" + getStateStr(targetState));
                    } else if (hideable && shouldHide(releasedChild, yvel)) {//是否进行隐藏操作
                        // Hide if the view was either released low or it was a significant vertical swipe
                        // otherwise settle to closest expanded state.
                        if ((Math.abs(xvel) < Math.abs(yvel) && yvel > SIGNIFICANT_VEL_THRESHOLD)//y方向速度大于x方向速度且，y速度大于阈值速度
                                || releasedLow(releasedChild)) {//高度低于父容器+全展偏移的一半则隐藏
                            top = parentHeight;
                            targetState = STATE_HIDDEN;
                        } else if (fitToContents) {//如适配内容则全展
                            top = fitToContentsOffset;
                            targetState = STATE_EXPANDED;
                        } else if (Math.abs(releasedChild.getTop() - expandedOffset)
                                < Math.abs(releasedChild.getTop() - halfExpandedOffset)) {//如果高度离全展偏移近就全展，离半展近就半展
                            top = expandedOffset;
                            targetState = STATE_EXPANDED;
                        } else {
                            top = halfExpandedOffset;
                            targetState = STATE_HALF_EXPANDED;
                        }
                        Log.e(TAG, "onViewReleased: shouldHide ,top=" + top + ",state=" + getStateStr(targetState));
                    } else if (yvel == 0.f || Math.abs(xvel) > Math.abs(yvel)) {//如y方向速度等于0或者x方向速度大于y方向速度
                        // If the Y velocity is 0 or the swipe was mostly horizontal indicated by the X velocity
                        // being greater than the Y velocity, settle to the nearest correct height.
                        int currentTop = releasedChild.getTop();
                        if (fitToContents) {//适配内容高度
                            if (Math.abs(currentTop - fitToContentsOffset)//离适配的展开高度近就展开，离折叠偏移位置近就折叠
                                    < Math.abs(currentTop - collapsedOffset)) {
                                top = fitToContentsOffset;
                                targetState = STATE_EXPANDED;
                            } else {
                                top = collapsedOffset;
                                targetState = STATE_COLLAPSED;
                            }
                        } else {//不适配内容高度
                            if (currentTop < halfExpandedOffset) {//高于半展偏移
                                if (currentTop < Math.abs(currentTop - collapsedOffset)) {//离顶部近就全展，离半展近就半展
                                    top = expandedOffset;
                                    targetState = STATE_EXPANDED;
                                } else {
                                    top = halfExpandedOffset;
                                    targetState = STATE_HALF_EXPANDED;
                                }
                            } else {//低于或等于半展
                                if (Math.abs(currentTop - halfExpandedOffset)
                                        < Math.abs(currentTop - collapsedOffset)) {//离半展近就半展，离折叠近就折叠
                                    top = halfExpandedOffset;
                                    targetState = STATE_HALF_EXPANDED;
                                } else {
                                    top = collapsedOffset;
                                    targetState = STATE_COLLAPSED;
                                }
                            }
                        }
                        Log.e(TAG, "onViewReleased: yvel == 0.f ,top=" + top + ",state=" + getStateStr(targetState));
                    } else { // Moving Down 其他情况y向下时且不进行隐藏时
                        if (fitToContents) {//适配内容高度，则直接折叠
                            top = collapsedOffset;
                            targetState = STATE_COLLAPSED;
                        } else {
                            // Settle to the nearest correct height.计算最近的正确的高度
                            int currentTop = releasedChild.getTop();
                            if (Math.abs(currentTop - halfExpandedOffset)
                                    < Math.abs(currentTop - collapsedOffset)) {//离半展近就半展，离折叠近就折叠
                                top = halfExpandedOffset;
                                targetState = STATE_HALF_EXPANDED;
                            } else {
                                top = collapsedOffset;
                                targetState = STATE_COLLAPSED;
                            }
                        }
                        Log.e(TAG, "onViewReleased: other ,top=" + top + ",state=" + getStateStr(targetState));
                    }
                    startSettlingAnimation(releasedChild, targetState, top, true);//进行结算距离后的动画
                }

                /**
                 * 垂直方向的操作判断
                 * @param child
                 * @param top 将要到达的高度的值
                 * @param dy 相对于当前位置的偏移量
                 * @return 所处的高度
                 */
                @Override
                public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
                    return MathUtils.clamp(
                            top, getExpandedOffset(), hideable ? parentHeight : collapsedOffset);
                }

                /**
                 * 水平方向的操作判断
                 * @param child
                 * @param left 将要到达的水平方向的距离
                 * @param dx 相对于当前位置的偏移量
                 * @return 所处的水平距离
                 */
                @Override
                public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
                    return child.getLeft();
                }

                /**
                 * 拖拽滚动范围
                 * @param child 回调方法返回大于0的时候child才能被捕获
                 * @return
                 */
                @Override
                public int getViewVerticalDragRange(@NonNull View child) {
                    if (hideable) {
                        return parentHeight;
                    } else {
                        return collapsedOffset;
                    }
                }
            };

    /**
     * 分发高度位置
     *
     * @param top
     */
    void dispatchOnSlide(int top) {
        View bottomSheet = viewRef.get();
        if (bottomSheet != null && !callbacks.isEmpty()) {
            float slideOffset =
                    (top > collapsedOffset || collapsedOffset == getExpandedOffset())
                            ? (float) (collapsedOffset - top) / (parentHeight - collapsedOffset)
                            : (float) (collapsedOffset - top) / (collapsedOffset - getExpandedOffset());
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).onSlide(bottomSheet, slideOffset);
            }
        }
    }

    @VisibleForTesting
    int getPeekHeightMin() {
        return peekHeightMin;
    }

    /**
     * Disables the shaped corner {@link ShapeAppearanceModel} interpolation transition animations.
     * Will have no effect unless the sheet utilizes a {@link MaterialShapeDrawable} with set shape
     * theming properties. Only For use in UI testing.
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @VisibleForTesting
    public void disableShapeAnimations() {
        // Sets the shape value animator to null, prevents animations from occuring during testing.
        interpolatorAnimator = null;
    }

    private class SettleRunnable implements Runnable {

        private final View view;

        private boolean isPosted;

        @HBottomSheetBehavior.State
        int targetState;

        SettleRunnable(View view, @HBottomSheetBehavior.State int targetState) {
            this.view = view;
            this.targetState = targetState;
        }

        @Override
        public void run() {
            if (viewDragHelper != null && viewDragHelper.continueSettling(true)) {
                ViewCompat.postOnAnimation(view, this);
            } else {
                setStateInternal(targetState);
            }
            this.isPosted = false;
        }
    }

    /**
     * State persisted across instances
     */
    protected static class SavedState extends AbsSavedState {
        @HBottomSheetBehavior.State
        final int state;
        int peekHeight;
        boolean fitToContents;
        boolean hideable;
        boolean skipCollapsed;

        public SavedState(@NonNull Parcel source) {
            this(source, null);
        }

        public SavedState(@NonNull Parcel source, ClassLoader loader) {
            super(source, loader);
            //noinspection ResourceType
            state = source.readInt();
            peekHeight = source.readInt();
            fitToContents = source.readInt() == 1;
            hideable = source.readInt() == 1;
            skipCollapsed = source.readInt() == 1;
        }

        public SavedState(Parcelable superState, @NonNull HBottomSheetBehavior<?> behavior) {
            super(superState);
            this.state = behavior.state;
            this.peekHeight = behavior.peekHeight;
            this.fitToContents = behavior.fitToContents;
            this.hideable = behavior.hideable;
            this.skipCollapsed = behavior.skipCollapsed;
        }

        /**
         * This constructor does not respect flags: {@link HBottomSheetBehavior#SAVE_PEEK_HEIGHT}, {@link
         * HBottomSheetBehavior#SAVE_FIT_TO_CONTENTS}, {@link HBottomSheetBehavior#SAVE_HIDEABLE}, {@link
         * HBottomSheetBehavior#SAVE_SKIP_COLLAPSED}. It is as if {@link HBottomSheetBehavior#SAVE_NONE}
         * were set.
         *
         * @deprecated Use {@link HBottomSheetBehavior.SavedState (Parcelable, BottomSheetBehavior)} instead.
         */
        @Deprecated
        public SavedState(Parcelable superstate, int state) {
            super(superstate);
            this.state = state;
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(state);
            out.writeInt(peekHeight);
            out.writeInt(fitToContents ? 1 : 0);
            out.writeInt(hideable ? 1 : 0);
            out.writeInt(skipCollapsed ? 1 : 0);
        }

        public static final Creator<SavedState> CREATOR =
                new ClassLoaderCreator<SavedState>() {
                    @NonNull
                    @Override
                    public HBottomSheetBehavior.SavedState createFromParcel(@NonNull Parcel in, ClassLoader loader) {
                        return new HBottomSheetBehavior.SavedState(in, loader);
                    }

                    @Nullable
                    @Override
                    public HBottomSheetBehavior.SavedState createFromParcel(@NonNull Parcel in) {
                        return new HBottomSheetBehavior.SavedState(in, null);
                    }

                    @NonNull
                    @Override
                    public HBottomSheetBehavior.SavedState[] newArray(int size) {
                        return new HBottomSheetBehavior.SavedState[size];
                    }
                };
    }

    /**
     * A utility function to get the {@link HBottomSheetBehavior} associated with the {@code view}.
     *
     * @param view The {@link View} with {@link HBottomSheetBehavior}.
     * @return The {@link HBottomSheetBehavior} associated with the {@code view}.
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public static <V extends View> HBottomSheetBehavior<V> from(@NonNull V view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof CoordinatorLayout.LayoutParams)) {
            throw new IllegalArgumentException("The view is not a child of CoordinatorLayout");
        }
        CoordinatorLayout.Behavior<?> behavior =
                ((CoordinatorLayout.LayoutParams) params).getBehavior();
        if (!(behavior instanceof HBottomSheetBehavior)) {
            throw new IllegalArgumentException("The view is not associated with BottomSheetBehavior");
        }
        return (HBottomSheetBehavior<V>) behavior;
    }

    /**
     * Sets whether the BottomSheet should update the accessibility status of its {@link *
     * CoordinatorLayout} siblings when expanded.
     *
     * <p>Set this to true if the expanded state of the sheet blocks access to siblings (e.g., when
     * the sheet expands over the full screen).
     */
    public void setUpdateImportantForAccessibilityOnSiblings(
            boolean updateImportantForAccessibilityOnSiblings) {
        this.updateImportantForAccessibilityOnSiblings = updateImportantForAccessibilityOnSiblings;
    }

    private void updateImportantForAccessibility(boolean expanded) {
        if (viewRef == null) {
            return;
        }

        ViewParent viewParent = viewRef.get().getParent();
        if (!(viewParent instanceof CoordinatorLayout)) {
            return;
        }

        CoordinatorLayout parent = (CoordinatorLayout) viewParent;
        final int childCount = parent.getChildCount();
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) && expanded) {
            if (importantForAccessibilityMap == null) {
                importantForAccessibilityMap = new HashMap<>(childCount);
            } else {
                // The important for accessibility values of the child views have been saved already.
                return;
            }
        }

        for (int i = 0; i < childCount; i++) {
            final View child = parent.getChildAt(i);
            if (child == viewRef.get()) {
                continue;
            }

            if (expanded) {
                // Saves the important for accessibility value of the child view.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    importantForAccessibilityMap.put(child, child.getImportantForAccessibility());
                }
                if (updateImportantForAccessibilityOnSiblings) {
                    ViewCompat.setImportantForAccessibility(
                            child, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                }
            } else {
                if (updateImportantForAccessibilityOnSiblings
                        && importantForAccessibilityMap != null
                        && importantForAccessibilityMap.containsKey(child)) {
                    // Restores the original important for accessibility value of the child view.
                    ViewCompat.setImportantForAccessibility(child, importantForAccessibilityMap.get(child));
                }
            }
        }

        if (!expanded) {
            importantForAccessibilityMap = null;
        }
    }

    /**
     * 更新子view当前可执行状态
     */
    private void updateAccessibilityActions() {
        if (viewRef == null) {
            return;
        }
        V child = viewRef.get();
        if (child == null) {
            return;
        }
        ViewCompat.removeAccessibilityAction(child, AccessibilityNodeInfoCompat.ACTION_COLLAPSE);//折叠
        ViewCompat.removeAccessibilityAction(child, AccessibilityNodeInfoCompat.ACTION_EXPAND);//展开
        ViewCompat.removeAccessibilityAction(child, AccessibilityNodeInfoCompat.ACTION_DISMISS);//隐藏

        if (hideable && state != STATE_HIDDEN) {
            addAccessibilityActionForState(child, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_DISMISS, STATE_HIDDEN);
        }

        switch (state) {
            case STATE_EXPANDED: {//展开状态下可执行的状态
                int nextState = fitToContents ? STATE_COLLAPSED : STATE_HALF_EXPANDED;//根据是否适配容器，折叠或半展
                addAccessibilityActionForState(
                        child, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_COLLAPSE, nextState);
                break;
            }
            case STATE_HALF_EXPANDED: {//半展开状态下可执行的状态
                addAccessibilityActionForState(
                        child, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_COLLAPSE, STATE_COLLAPSED);//折叠
                addAccessibilityActionForState(
                        child, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_EXPAND, STATE_EXPANDED);//全展开
                break;
            }
            case STATE_COLLAPSED: {//折叠状态下可执行的状态
                int nextState = fitToContents ? STATE_EXPANDED : STATE_HALF_EXPANDED;//根据是否适配容器，全展或半展
                addAccessibilityActionForState(child, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_EXPAND, nextState);
                break;
            }
            default: // fall out
        }
    }

    /**
     * 新增可设置状态
     *
     * @param child
     * @param action
     * @param state
     */
    private void addAccessibilityActionForState(
            V child, AccessibilityNodeInfoCompat.AccessibilityActionCompat action, final int state) {
        ViewCompat.replaceAccessibilityAction(
                child,
                action,
                null,
                new AccessibilityViewCommand() {
                    @Override
                    public boolean perform(@NonNull View view, @Nullable CommandArguments arguments) {
                        setState(state);
                        return true;
                    }
                });
    }


    private String getStateStr(int state) {
        switch (state) {
            case STATE_DRAGGING:
                return "STATE_DRAGGING";
            case STATE_SETTLING:
                return "STATE_SETTLING";
            case STATE_EXPANDED:
                return "STATE_EXPANDED";
            case STATE_COLLAPSED:
                return "STATE_COLLAPSED";
            case STATE_HIDDEN:
                return "STATE_HIDDEN";
            case STATE_HALF_EXPANDED:
                return "STATE_HALF_EXPANDED";
        }
        return "no state";
    }
}
