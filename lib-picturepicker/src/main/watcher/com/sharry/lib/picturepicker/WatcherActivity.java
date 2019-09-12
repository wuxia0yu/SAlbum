package com.sharry.lib.picturepicker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.sharry.lib.picturepicker.photoview.PhotoView;
import com.sharry.lib.widget.toolbar.SToolbar;
import com.sharry.lib.widget.toolbar.ViewOptions;

import java.util.ArrayList;

import static com.sharry.lib.picturepicker.ActivityStateUtil.fixRequestOrientation;

/**
 * 图片查看器的 Activity, 主题设置为背景透明效果更佳
 *
 * @author Sharry <a href="SharryChooCHN@Gmail.com">Contact me.</a>
 * @version 1.3
 * @since 2018/9/22 23:24
 */
public class WatcherActivity extends AppCompatActivity implements
        WatcherContract.IView,
        DraggableViewPager.OnPagerChangedListener,
        WatcherFragment.Interaction,
        PickedAdapter.Interaction {

    public static final int REQUEST_CODE = 508;
    public static final String RESULT_EXTRA_PICKED_PICTURES = "result_extra_picked_pictures";
    public static final String RESULT_EXTRA_IS_PICKED_ENSURE = "result_extra_is_picked_ensure";

    private static final String TAG = WatcherActivity.class.getSimpleName();
    private static final String EXTRA_CONFIG = "start_intent_extra_config";
    private static final String EXTRA_SHARED_ELEMENT = "start_intent_extra_shared_element";
    private static final int THRESHOLD_TRANSACTION_DATA_SIZE = 150 * 1024;

    /**
     * U can launch this activity from here.
     *
     * @param request       请求的 Activity
     * @param resultTo      WatcherActivity 返回值的去向
     * @param config        WatcherActivity 的配置
     * @param sharedElement 共享元素
     */
    public static void launchActivityForResult(@NonNull Activity request, @NonNull Fragment resultTo,
                                               @NonNull WatcherConfig config, @Nullable View sharedElement) {
        Intent intent = new Intent(request, WatcherActivity.class);
        intent.putExtra(WatcherActivity.EXTRA_CONFIG, config);
        if (sharedElement != null) {
            intent.putExtra(
                    WatcherActivity.EXTRA_SHARED_ELEMENT,
                    SharedElementModel.parseFrom(sharedElement, config.getPosition())
            );
        }
        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            return;
        }
        Parcel parcel = Parcel.obtain();
        bundle.writeToParcel(parcel, 0);
        Log.i(TAG, "Transaction data size is: " + parcel.dataSize() + " bytes");
        if (parcel.dataSize() < THRESHOLD_TRANSACTION_DATA_SIZE) {
            resultTo.startActivityForResult(intent, REQUEST_CODE);
            // 使用淡入淡出的效果
            if (sharedElement != null) {
                request.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        } else {
            Log.e(TAG, "Transaction is to large!!! data size is: " + parcel.dataSize() + " bytes");
            intent = new Intent(request, WatcherActivity.class);
            intent.putExtra(WatcherActivity.EXTRA_CONFIG, config);
            resultTo.startActivityForResult(intent, REQUEST_CODE);
        }
    }

    /**
     * The presenter for the view.
     */
    private WatcherContract.IPresenter mPresenter;

    /**
     * Widgets for this Activity.
     */
    private TextView mTvTitle;
    private PhotoView mIvPlaceHolder;
    private CheckedIndicatorView mCheckIndicator;
    private DraggableViewPager mWatcherPager;
    private WatcherPagerAdapter mWatcherAdapter;
    private LinearLayout mLlBottomPreviewContainer;
    private RecyclerView mBottomPreviewPictures;
    private TextView mTvEnsure;

    /**
     * The animator for bottom preview.
     */
    private ObjectAnimator mBottomPreviewShowAnimator;
    private ObjectAnimator mBottomPreviewDismissAnimator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        fixRequestOrientation(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.picture_picker_activity_picture_watcher);
        initTitle();
        initViews();
        initPresenter();
    }

    @Override
    public void onBackPressed() {
        mPresenter.handleBackPressed();
    }

    @Override
    public void finish() {
        mPresenter.handleSetResultBeforeFinish();
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WatcherFragment.ACTIVES.clear();
        WatcherFragment.IDLES.clear();
    }

    //////////////////////////////////////////////WatcherContract.IView/////////////////////////////////////////////////

    @Override
    public void setToolbarCheckedIndicatorVisibility(boolean isShowCheckedIndicator) {
        mCheckIndicator.setVisibility(isShowCheckedIndicator ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setToolbarCheckedIndicatorColors(int indicatorBorderCheckedColor, int indicatorBorderUncheckedColor,
                                                 int indicatorSolidColor, int indicatorTextColor) {
        mCheckIndicator.setBorderColor(indicatorBorderCheckedColor, indicatorBorderUncheckedColor);
        mCheckIndicator.setSolidColor(indicatorSolidColor);
        mCheckIndicator.setTextColor(indicatorTextColor);
    }

    @Override
    public void setPreviewAdapter(ArrayList<MediaMeta> pickedSet) {
        mBottomPreviewPictures.setAdapter(new PickedAdapter(pickedSet, this));
    }

    @Override
    public void setDisplayAdapter(ArrayList<MediaMeta> items) {
        mWatcherAdapter = new WatcherPagerAdapter(getSupportFragmentManager(), items);
        mWatcherPager.setAdapter(mWatcherAdapter);
    }

    @Override
    public void showSharedElementEnter(MediaMeta mediaMeta, final SharedElementModel data) {
        // 加载共享元素占位图
        mIvPlaceHolder.setVisibility(View.VISIBLE);
        Loader.loadPicture(this, mediaMeta.path, mIvPlaceHolder);
        // 执行共享元素
        mIvPlaceHolder.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mIvPlaceHolder.getViewTreeObserver().removeOnPreDrawListener(this);
                // Execute enter animator.
                Animator startAnim = SharedElementUtils.createSharedElementEnterAnimator(mIvPlaceHolder, data);
                startAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mIvPlaceHolder.setVisibility(View.GONE);
                    }
                });
                startAnim.start();
                return true;
            }
        });
    }

    @Override
    public void notifyBottomPicturesRemoved(MediaMeta removedMeta, int removedIndex) {
        RecyclerView.Adapter adapter;
        if ((adapter = mBottomPreviewPictures.getAdapter()) != null) {
            adapter.notifyItemRemoved(removedIndex);
        }
    }

    @Override
    public void notifyBottomPictureAdded(MediaMeta addedMeta, int addedIndex) {
        RecyclerView.Adapter adapter;
        if ((adapter = mBottomPreviewPictures.getAdapter()) != null) {
            adapter.notifyItemInserted(addedIndex);
        }
    }

    @Override
    public void displayAt(int position) {
        mWatcherPager.setCurrentItem(position);
    }

    @Override
    public void setToolbarIndicatorChecked(boolean isChecked) {
        mCheckIndicator.setChecked(isChecked);
    }

    @Override
    public void displayToolbarIndicatorText(CharSequence indicatorText) {
        mCheckIndicator.setText(indicatorText);
    }

    @Override
    public void displayPreviewEnsureText(CharSequence content) {
        mTvEnsure.setText(content);
    }

    @Override
    public void displayToolbarLeftText(CharSequence content) {
        mTvTitle.setText(content);
    }

    @Override
    public void showBottomPreview() {
        if (mLlBottomPreviewContainer.getVisibility() == View.VISIBLE) {
            return;
        }
        if (mBottomPreviewShowAnimator == null) {
            mBottomPreviewShowAnimator = ObjectAnimator.ofFloat(mLlBottomPreviewContainer,
                    "translationY", mLlBottomPreviewContainer.getHeight(), 0);
            mBottomPreviewShowAnimator.setDuration(200);
            mBottomPreviewShowAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mLlBottomPreviewContainer.setVisibility(View.VISIBLE);
                }
            });
        }
        mBottomPreviewShowAnimator.start();
    }

    @Override
    public void dismissBottomPreview() {
        if (mLlBottomPreviewContainer.getVisibility() == View.INVISIBLE) {
            return;
        }
        if (mBottomPreviewDismissAnimator == null) {
            mBottomPreviewDismissAnimator = ObjectAnimator.ofFloat(mLlBottomPreviewContainer,
                    "translationY", 0, mLlBottomPreviewContainer.getHeight());
            mBottomPreviewDismissAnimator.setDuration(200);
            mBottomPreviewDismissAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLlBottomPreviewContainer.setVisibility(View.INVISIBLE);
                }
            });
        }
        mBottomPreviewDismissAnimator.start();
    }

    @Override
    public void previewPicturesSmoothScrollToPosition(int position) {
        mBottomPreviewPictures.smoothScrollToPosition(position);
    }

    @Override
    public void showMsg(String msg) {
        Snackbar.make(mBottomPreviewPictures, msg, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void setResultBeforeFinish(@Nullable ArrayList<MediaMeta> pickedPaths, boolean isEnsurePressed) {
        Intent intent = new Intent();
        intent.putExtra(RESULT_EXTRA_PICKED_PICTURES, pickedPaths);
        intent.putExtra(RESULT_EXTRA_IS_PICKED_ENSURE, isEnsurePressed);
        setResult(RESULT_OK, intent);
    }


    ////////////////////////////////////////// OnPagerChangedListener /////////////////////////////////////////////

    @Override
    public void onPagerChanged(int position) {
        if (mPresenter != null) {
            mPresenter.handlePagerChanged(position);
        }
    }

    ////////////////////////////////////////// PickedAdapter.Interaction /////////////////////////////////////////////

    @Override
    public void onPreviewItemClicked(ImageView imageView, MediaMeta meta, int position) {
        mPresenter.handlePickedItemClicked(meta);
    }

    private void initTitle() {
        SToolbar toolbar = findViewById(R.id.toolbar);
        mTvTitle = toolbar.getTitleText();
        // 添加右部的索引
        mCheckIndicator = new CheckedIndicatorView(this);
        toolbar.addRightMenuView(mCheckIndicator, new ViewOptions.Builder()
                .setVisibility(View.INVISIBLE)
                .setWidthExcludePadding(DensityUtil.dp2px(this, 25))
                .setHeightExcludePadding(DensityUtil.dp2px(this, 25))
                .setPaddingRight(DensityUtil.dp2px(this, 10))
                .setListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mPresenter.handleToolbarCheckedIndicatorClick(mCheckIndicator.isChecked());
                    }
                })
                .build());
    }

    private void initViews() {
        // 占位图
        mIvPlaceHolder = findViewById(R.id.iv_place_holder);
        // 1. 初始化 ViewPager
        mWatcherPager = findViewById(R.id.view_pager);
        mWatcherPager.setOnPagerChangedListener(this);
        mWatcherPager.setBackgroundColorRes(R.color.picture_picker_watcher_bg_color);
        // 2. 初始化底部菜单
        mLlBottomPreviewContainer = findViewById(R.id.ll_bottom_container);
        mBottomPreviewPictures = findViewById(R.id.recycle_pictures);
        mBottomPreviewPictures.setLayoutManager(new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false));
        mTvEnsure = findViewById(R.id.tv_ensure);
        mTvEnsure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPresenter.handleEnsureClick();
            }
        });
    }

    private void initPresenter() {
        mPresenter = new WatcherPresenter(
                this,
                (WatcherConfig) getIntent().getParcelableExtra(EXTRA_CONFIG),
                ((SharedElementModel) getIntent().getParcelableExtra(EXTRA_SHARED_ELEMENT))
        );
    }

}