
package net.micode.fileexplorer;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import net.micode.fileexplorer.FavoriteDatabaseHelper.FavoriteDatabaseListener;
import net.micode.fileexplorer.FileCategoryHelper.CategoryInfo;
import net.micode.fileexplorer.FileCategoryHelper.FileCategory;
import net.micode.fileexplorer.FileExplorerTabActivity.IBackPressedListener;
import net.micode.fileexplorer.FileViewInteractionHub.Mode;
import net.micode.fileexplorer.Util.SDCardInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 职责：
 * */
public class FileCategoryActivity extends Fragment implements IFileInteractionListener,
        FavoriteDatabaseListener, IBackPressedListener {

    public static final String EXT_FILETER_KEY = "ext_filter";

    private static final String LOG_TAG = "FileCategoryActivity";

    private static HashMap<Integer, FileCategory> button2Category = new HashMap<Integer, FileCategory>();

    private HashMap<FileCategory, Integer> categoryIndex = new HashMap<FileCategory, Integer>();

    /**
     * 绑定到一个ListView中
     * */
    private FileListCursorAdapter mAdapter;

    private FileViewInteractionHub mFileViewInteractionHub;

    private FileCategoryHelper mFileCagetoryHelper;

    private FileIconHelper mFileIconHelper;

    /**
     * 自定义View，分类容量显示条
     * */
    private CategoryBar mCategoryBar;

    private ScannerReceiver mScannerReceiver;

    /**
     * 当前FavoriteList
     * */
    private FavoriteList mFavoriteList;

    private ViewPage curViewPage = ViewPage.Invalid;

    private ViewPage preViewPage = ViewPage.Invalid;

    private Activity mActivity;

    private View mRootView;

    private FileViewActivity mFileViewActivity;

    private boolean mConfigurationChanged = false;

    public void setConfigurationChanged(boolean changed) {
        mConfigurationChanged = changed;
    }

    static {
        button2Category.put(R.id.category_music, FileCategory.Music);
        button2Category.put(R.id.category_video, FileCategory.Video);
        button2Category.put(R.id.category_picture, FileCategory.Picture);
        button2Category.put(R.id.category_theme, FileCategory.Theme);
        button2Category.put(R.id.category_document, FileCategory.Doc);
        button2Category.put(R.id.category_zip, FileCategory.Zip);
        button2Category.put(R.id.category_apk, FileCategory.Apk);
        button2Category.put(R.id.category_favorite, FileCategory.Favorite);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mActivity = getActivity();
        mFileViewActivity = (FileViewActivity) ((FileExplorerTabActivity) mActivity)
                .getFragment(Util.SDCARD_TAB_INDEX);
        // 默认显示category，其他file_path_list是gone
        mRootView = inflater.inflate(R.layout.file_explorer_category, container, false);
        curViewPage = ViewPage.Invalid;
        mFileViewInteractionHub = new FileViewInteractionHub(this);
        mFileViewInteractionHub.setMode(Mode.View);
        mFileViewInteractionHub.setRootPath("/");
        mFileIconHelper = new FileIconHelper(mActivity);
        // 初始化默认的FavoriteList，有照片，SD卡..
        mFavoriteList = new FavoriteList(mActivity, (ListView) mRootView.findViewById(R.id.favorite_list), this, mFileIconHelper);
        mFavoriteList.initList();
        // 初始化CursorAdapter，用来绑定来自数据库查询的FileInfo
        mAdapter = new FileListCursorAdapter(mActivity, null, mFileViewInteractionHub, mFileIconHelper);
        // 正常的fileList
        ListView fileListView = (ListView) mRootView.findViewById(R.id.file_path_list);
        fileListView.setAdapter(mAdapter);
        // 为category显示页面图标设定点击事件
        setupClick();
        // 设置category bar
        setupCategoryInfo();
        // 根据SD卡刷新相关UI的CategoryInfo
        updateUI();
        // 注册reciver，进行后台CategoryInfo更新
        registerScannerReceiver();

        return mRootView;
    }

    private void registerScannerReceiver() {
        mScannerReceiver = new ScannerReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addDataScheme("file");
        mActivity.registerReceiver(mScannerReceiver, intentFilter);
    }

    private void setupCategoryInfo() {
        mFileCagetoryHelper = new FileCategoryHelper(mActivity);
        
        mCategoryBar = (CategoryBar) mRootView.findViewById(R.id.category_bar);
        
        // 建立Category对象和Index的对应关系
        int[] imgs = new int[] {
                R.drawable.category_bar_music, R.drawable.category_bar_video,
                R.drawable.category_bar_picture, R.drawable.category_bar_theme,
                R.drawable.category_bar_document, R.drawable.category_bar_zip,
                R.drawable.category_bar_apk, R.drawable.category_bar_other
        };
        for (int i = 0; i < imgs.length; i++) {
            mCategoryBar.addCategory(imgs[i]);
        }
        for (int i = 0; i < FileCategoryHelper.sCategories.length; i++) {
            categoryIndex.put(FileCategoryHelper.sCategories[i], i);
        }
    }

    /**
     * 作用：刷新CategoryInfo
     * 1.根据SD卡信息填充CategoryInfo
     * 2.启动动画
     * */
    public void refreshCategoryInfo() {
        SDCardInfo sdCardInfo = Util.getSDCardInfo();
        if (sdCardInfo != null) {
            mCategoryBar.setFullValue(sdCardInfo.total);
            setTextView(R.id.sd_card_capacity, getString(R.string.sd_card_size, Util.convertStorage(sdCardInfo.total)));
            setTextView(R.id.sd_card_available, getString(R.string.sd_card_available, Util.convertStorage(sdCardInfo.free)));
        }
        // 填充CategoryInfo
        mFileCagetoryHelper.refreshCategoryInfo();

        // the other category size should include those files didn't get scanned.
        long size = 0;
        for (FileCategory fc : FileCategoryHelper.sCategories) {
            CategoryInfo categoryInfo = mFileCagetoryHelper.getCategoryInfos().get(fc);
            setCategoryCount(fc, categoryInfo.count);

            // other category size should be set separately with calibration
            if(fc == FileCategory.Other)
                continue;
            // 设置每个Category显示的size信息，显示在textView
            setCategorySize(fc, categoryInfo.size);
            // 设置Category的size
            setCategoryBarValue(fc, categoryInfo.size);
            size += categoryInfo.size;
        }

        if (sdCardInfo != null) {
            long otherSize = sdCardInfo.total - sdCardInfo.free - size;
            setCategorySize(FileCategory.Other, otherSize);
            setCategoryBarValue(FileCategory.Other, otherSize);
        }

        setCategoryCount(FileCategory.Favorite, mFavoriteList.getCount());
        // 启动animation
        if (mCategoryBar.getVisibility() == View.VISIBLE) {
            mCategoryBar.startAnimation();
        }
    }

    public enum ViewPage {
        Home, Favorite, Category, NoSD, Invalid
    }

    /**
     * 根据ViewPage决定显示的View
     * */
    private void showPage(ViewPage p) {
        if (curViewPage == p) return;

        curViewPage = p;
        // 先不显示这些
        showView(R.id.file_path_list, false);
        showView(R.id.navigation_bar, false);
        showView(R.id.category_page, false);
        showView(R.id.operation_bar, false);
        showView(R.id.sd_not_available_page, false);
        mFavoriteList.show(false);
        showEmptyView(false);
        // 根据p的类型，选择要显示的View
        switch (p) {
            case Home:
                showView(R.id.category_page, true);
                if (mConfigurationChanged) {
                    ((FileExplorerTabActivity) mActivity).reInstantiateCategoryTab();
                    mConfigurationChanged = false;
                }
                break;
            case Favorite:
                showView(R.id.navigation_bar, true);
                mFavoriteList.show(true);
                showEmptyView(mFavoriteList.getCount() == 0);
                break;
            case Category:
                showView(R.id.navigation_bar, true);
                showView(R.id.file_path_list, true);
                showEmptyView(mAdapter.getCount() == 0);
                break;
            case NoSD:
                showView(R.id.sd_not_available_page, true);
                break;
        }
    }

    private void showEmptyView(boolean show) {
        View emptyView = mActivity.findViewById(R.id.empty_view);
        if (emptyView != null)
            emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * 作用：根据show，设定相应id的View为View.VISIBLE或View.GONE
     * */
    private void showView(int id, boolean show) {
        View view = mRootView.findViewById(id);
        if (view != null) {
            view.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            FileCategory f = button2Category.get(v.getId());
            if (f != null) {
            	// 1.设置当前FileCategory 2.设置显示的View
                onCategorySelected(f);
                if (f != FileCategory.Favorite) {
                	// 可以设置菜单
                    setHasOptionsMenu(true);
                }
            }
        }
    };

    private void setCategoryCount(FileCategory fc, long count) {
        int id = getCategoryCountId(fc);
        if (id == 0)
            return;

        setTextView(id, "(" + count + ")");
    }

    private void setTextView(int id, String t) {
        TextView text = (TextView) mRootView.findViewById(id);
        text.setText(t);
    }

    /**
     * 作用：1.设置当前FileCategory 2.设置显示的View
     * */
    private void onCategorySelected(FileCategory f) {
        // 设置当前FileCategory
    	if (mFileCagetoryHelper.getCurCategory() != f) {
            mFileCagetoryHelper.setCurCategory(f);
            // TODO 设置当前路径?
            mFileViewInteractionHub.setCurrentPath(mFileViewInteractionHub.getRootPath()
                    + getString(mFileCagetoryHelper.getCurCategoryNameResId()));
            mFileViewInteractionHub.refreshFileList();
        }
        // 设置显示的View
        if (f == FileCategory.Favorite) {
            showPage(ViewPage.Favorite);
        } else {
            showPage(ViewPage.Category);
        }
    }


    private void setupClick(int id) {
        View button = mRootView.findViewById(id);
        button.setOnClickListener(onClickListener);
    }

    /**
     * 设置在分类浏览中，每个图标（音乐、视频、图片...）的点击事件
     * */
    private void setupClick() {
        setupClick(R.id.category_music);
        setupClick(R.id.category_video);
        setupClick(R.id.category_picture);
        setupClick(R.id.category_theme);
        setupClick(R.id.category_document);
        setupClick(R.id.category_zip);
        setupClick(R.id.category_apk);
        setupClick(R.id.category_favorite);
    }

    @Override
    public boolean onBack() {
        if (isHomePage() || curViewPage == ViewPage.NoSD || mFileViewInteractionHub == null) {
            return false;
        }

        return mFileViewInteractionHub.onBackPressed();
    }

    public boolean isHomePage() {
        return curViewPage == ViewPage.Home;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (curViewPage != ViewPage.Category && curViewPage != ViewPage.Favorite) {
            return;
        }
        mFileViewInteractionHub.onCreateOptionsMenu(menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (!isHomePage() && mFileCagetoryHelper.getCurCategory() != FileCategory.Favorite) {
            mFileViewInteractionHub.onPrepareOptionsMenu(menu);
        }
    }

    public boolean onRefreshFileList(String path, FileSortHelper sort) {
        FileCategory curCategory = mFileCagetoryHelper.getCurCategory();
        if (curCategory == FileCategory.Favorite || curCategory == FileCategory.All)
            return false;

        Cursor c = mFileCagetoryHelper.query(curCategory, sort.getSortMethod());
        showEmptyView(c == null || c.getCount() == 0);
        mAdapter.changeCursor(c);

        return true;
    }

    @Override
    public View getViewById(int id) {
        return mRootView.findViewById(id);
    }

    @Override
    public Context getContext() {
        return mActivity;
    }

    @Override
    public void onDataChanged() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
                mFavoriteList.getArrayAdapter().notifyDataSetChanged();
                showEmptyView(mAdapter.getCount() == 0);
            }

        });
    }

    @Override
    public void onPick(FileInfo f) {
        // do nothing
    }

    @Override
    public boolean shouldShowOperationPane() {
        return true;
    }

    @Override
    public boolean onOperation(int id) {
        mFileViewInteractionHub.addContextMenuSelectedItem();
        switch (id) {
            case R.id.button_operation_copy:
            case GlobalConsts.MENU_COPY:
                copyFileInFileView(mFileViewInteractionHub.getSelectedFileList());
                mFileViewInteractionHub.clearSelection();
                break;
            case R.id.button_operation_move:
            case GlobalConsts.MENU_MOVE:
                startMoveToFileView(mFileViewInteractionHub.getSelectedFileList());
                mFileViewInteractionHub.clearSelection();
                break;
            case GlobalConsts.OPERATION_UP_LEVEL:
                setHasOptionsMenu(false);
                showPage(ViewPage.Home);
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public String getDisplayPath(String path) {
        return getString(R.string.tab_category) + path;
    }

    @Override
    public String getRealPath(String displayPath) {
        return "";
    }

    @Override
    public boolean onNavigation(String path) {
        showPage(ViewPage.Home);
        return true;
    }

    @Override
    public boolean shouldHideMenu(int menu) {
        return (menu == GlobalConsts.MENU_NEW_FOLDER || menu == GlobalConsts.MENU_FAVORITE
                || menu == GlobalConsts.MENU_PASTE || menu == GlobalConsts.MENU_SHOWHIDE);
    }

    @Override
    public void addSingleFile(FileInfo file) {
        refreshList();
    }

    @Override
    public Collection<FileInfo> getAllFiles() {
        return mAdapter.getAllFiles();
    }

    @Override
    public FileInfo getItem(int pos) {
        return mAdapter.getFileItemByPos(pos);
    }

    @Override
    public int getItemCount() {
        return mAdapter.getCount();
    }

    @Override
    public void sortCurrentList(FileSortHelper sort) {
        refreshList();
    }

    private void refreshList() {
        mFileViewInteractionHub.refreshFileList();
    }

    private void copyFileInFileView(ArrayList<FileInfo> files) {
        if (files.size() == 0) return;
        mFileViewActivity.copyFile(files);
        mActivity.getActionBar().setSelectedNavigationItem(Util.SDCARD_TAB_INDEX);
    }

    private void startMoveToFileView(ArrayList<FileInfo> files) {
        if (files.size() == 0) return;
        mFileViewActivity.moveToFile(files);
        mActivity.getActionBar().setSelectedNavigationItem(Util.SDCARD_TAB_INDEX);
    }

    @Override
    public FileIconHelper getFileIconHelper() {
        return mFileIconHelper;
    }

    private static int getCategoryCountId(FileCategory fc) {
        switch (fc) {
            case Music:
                return R.id.category_music_count;
            case Video:
                return R.id.category_video_count;
            case Picture:
                return R.id.category_picture_count;
            case Theme:
                return R.id.category_theme_count;
            case Doc:
                return R.id.category_document_count;
            case Zip:
                return R.id.category_zip_count;
            case Apk:
                return R.id.category_apk_count;
            case Favorite:
                return R.id.category_favorite_count;
        }

        return 0;
    }

    /**
     * 设置每个Category显示的size信息，显示在textView
     * */
    private void setCategorySize(FileCategory fc, long size) {
        int txtId = 0;
        int resId = 0;
        switch (fc) {
            case Music:
                txtId = R.id.category_legend_music;
                resId = R.string.category_music;
                break;
            case Video:
                txtId = R.id.category_legend_video;
                resId = R.string.category_video;
                break;
            case Picture:
                txtId = R.id.category_legend_picture;
                resId = R.string.category_picture;
                break;
            case Theme:
                txtId = R.id.category_legend_theme;
                resId = R.string.category_theme;
                break;
            case Doc:
                txtId = R.id.category_legend_document;
                resId = R.string.category_document;
                break;
            case Zip:
                txtId = R.id.category_legend_zip;
                resId = R.string.category_zip;
                break;
            case Apk:
                txtId = R.id.category_legend_apk;
                resId = R.string.category_apk;
                break;
            case Other:
                txtId = R.id.category_legend_other;
                resId = R.string.category_other;
                break;
        }

        if (txtId == 0 || resId == 0)
            return;
        // 设置textView
        setTextView(txtId, getString(resId) + ":" + Util.convertStorage(size));
    }

    private void setCategoryBarValue(FileCategory f, long size) {
        if (mCategoryBar == null) {
            mCategoryBar = (CategoryBar) mRootView.findViewById(R.id.category_bar);
        }
        mCategoryBar.setCategoryValue(categoryIndex.get(f), size);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mActivity != null) {
            mActivity.unregisterReceiver(mScannerReceiver);
        }
    }

    /**
     * 接受广播，刷新文件
     * */
    private class ScannerReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(LOG_TAG, "received broadcast: " + action.toString());
            // handle intents related to external storage
            if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED) || action.equals(Intent.ACTION_MEDIA_MOUNTED)
                    || action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                notifyFileChanged();
            }
        }
    }

    /**
     * 根据实际情况刷新相关UI的CategoryInfo
     * */
    private void updateUI() {
        boolean sdCardReady = Util.isSDCardReady();
        if (sdCardReady) {
            if (preViewPage != ViewPage.Invalid) {
            	// pre
                showPage(preViewPage);
                preViewPage = ViewPage.Invalid;
            } else if (curViewPage == ViewPage.Invalid || curViewPage == ViewPage.NoSD) {
                // home
            	showPage(ViewPage.Home);
            }
            // 刷新CategoryInfo相关UI
            refreshCategoryInfo();
            // refresh file list
            mFileViewInteractionHub.refreshFileList();
            // refresh file list view in another tab
            mFileViewActivity.refresh();
        } else {
            preViewPage = curViewPage;
            showPage(ViewPage.NoSD);
        }
    }

    // process file changed notification, using a timer to avoid frequent
    // refreshing due to batch changing on file system
    synchronized public void notifyFileChanged() {
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer();
        // 1s一次
        timer.schedule(new TimerTask() {

            public void run() {
                timer = null;
                Message message = new Message();
                message.what = MSG_FILE_CHANGED_TIMER;
                handler.sendMessage(message);
            }

        }, 1000);
    }

    private static final int MSG_FILE_CHANGED_TIMER = 100;

    private Timer timer;

    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_FILE_CHANGED_TIMER:
                    updateUI();
                    break;
            }
            super.handleMessage(msg);
        }

    };

    // update the count of favorite
    @Override
    public void onFavoriteDatabaseChanged() {
        setCategoryCount(FileCategory.Favorite, mFavoriteList.getCount());
    }

    @Override
    public void runOnUiThread(Runnable r) {
        mActivity.runOnUiThread(r);
    }
}
