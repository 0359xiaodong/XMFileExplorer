/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * This file is part of FileExplorer.
 *
 * FileExplorer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FileExplorer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SwiFTP.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.micode.fileexplorer;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.micode.fileexplorer.FileExplorerTabActivity.IBackPressedListener;
import net.micode.fileexplorer.FileViewInteractionHub.Mode;

public class FileViewActivity extends Fragment implements
		IFileInteractionListener, IBackPressedListener {

	public static final String EXT_FILTER_KEY = "ext_filter";

	private static final String LOG_TAG = "FileViewActivity";

	public static final String EXT_FILE_FIRST_KEY = "ext_file_first";

	public static final String ROOT_DIRECTORY = "root_directory";

	public static final String PICK_FOLDER = "pick_folder";

	private ListView mFileListView;

	// private TextView mCurrentPathTextView;
	private ArrayAdapter<FileInfo> mAdapter;

	private FileViewInteractionHub mFileViewInteractionHub;

	/**
	 * 文件分类帮助器
	 * */
	private FileCategoryHelper mFileCagetoryHelper;

	/**
	 * 文件图标帮助器
	 * */
	private FileIconHelper mFileIconHelper;

	/**
	 * 当前文件列表的FileInfo信息
	 * */
	private ArrayList<FileInfo> mFileNameList = new ArrayList<FileInfo>();

	private Activity mActivity;

	private View mRootView;

	private static final String sdDir = Util.getSdDirectory();

	// memorize the scroll positions of previous paths
	private ArrayList<PathScrollPositionItem> mScrollPositionList = new ArrayList<PathScrollPositionItem>();
	private String mPreviousPath; // 当前路径

	/**
	 * 职责：接收SD卡插拔的系统广播，并且在主线程中更新UI
	 * */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			String action = intent.getAction();
			Log.v(LOG_TAG, "received broadcast:" + intent.toString());
			if (action.equals(Intent.ACTION_MEDIA_MOUNTED)
					|| action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
				// 在UI线程中更新UI：
				// 1.设置没有SD卡的View
				// 2.设置导航ActionBar
				// 3.判断sd卡是否准备好，刷新文件列表
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						updateUI();
					}
				});
				/**
				 * Q：让主线程去消息队列进行相应的操作，为什么不直接写？ 
				 *    难道onReceive不在主线程中执行吗？
				 * A：onReceive默认是在主线程中执行的，但是超过10s没有响应就会ANR，
				 *    所以费事操作要放在主线程的消息队列中处理
				 * */
			}
		}
	};

	private boolean mBackspaceExit;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mActivity = getActivity();
		// getWindow().setFormat(android.graphics.PixelFormat.RGBA_8888);
		mRootView = inflater.inflate(R.layout.file_explorer_list, container,
				false);
		/**
		 * 注册当前Activity，注意：当前Activity基本上都是FileExplorerTabActivity
		 * */
		ActivitiesManager.getInstance().registerActivity(
				ActivitiesManager.ACTIVITY_FILE_VIEW, mActivity);

		mFileCagetoryHelper = new FileCategoryHelper(mActivity);
		mFileViewInteractionHub = new FileViewInteractionHub(this);
		Intent intent = mActivity.getIntent();
		String action = intent.getAction();
		/**
		 * 对Action为Intent.ACTION_PICK和Intent.ACTION_GET_CONTENT进行处理
		 * */
		if (!TextUtils.isEmpty(action)
				&& (action.equals(Intent.ACTION_PICK) || action
						.equals(Intent.ACTION_GET_CONTENT))) {
			// 设置模式为Pick
			mFileViewInteractionHub.setMode(Mode.Pick);

			boolean pickFolder = intent.getBooleanExtra(PICK_FOLDER, false);
			if (!pickFolder) {
				String[] exts = intent.getStringArrayExtra(EXT_FILTER_KEY);
				if (exts != null) {
					mFileCagetoryHelper.setCustomCategory(exts);
				}
			} else {
				mFileCagetoryHelper.setCustomCategory(new String[] {} /*
																	 * folder
																	 * only
																	 */);
				mRootView.findViewById(R.id.pick_operation_bar).setVisibility(
						View.VISIBLE);

				mRootView.findViewById(R.id.button_pick_confirm)
						.setOnClickListener(new OnClickListener() {
							public void onClick(View v) {
								try {
									Intent intent = Intent.parseUri(
											mFileViewInteractionHub
													.getCurrentPath(), 0);
									mActivity.setResult(Activity.RESULT_OK,
											intent);
									mActivity.finish();
								} catch (URISyntaxException e) {
									e.printStackTrace();
								}
							}
						});

				mRootView.findViewById(R.id.button_pick_cancel)
						.setOnClickListener(new OnClickListener() {
							public void onClick(View v) {
								mActivity.finish();
							}
						});
			}
		} else {
			// 设置为View模式
			mFileViewInteractionHub.setMode(Mode.View);
		}

		// 初始化FileListView，FileIconHelper，FileListAdapter
		mFileListView = (ListView) mRootView.findViewById(R.id.file_path_list);
		mFileIconHelper = new FileIconHelper(mActivity);
		mAdapter = new FileListAdapter(mActivity, R.layout.file_browser_item,
				mFileNameList, mFileViewInteractionHub, mFileIconHelper);

		// 是否基于sd卡 TODO 目前还不清楚这里到底是怎么处理的
		boolean baseSd = intent.getBooleanExtra(GlobalConsts.KEY_BASE_SD,
				!FileExplorerPreferenceActivity.isReadRoot(mActivity));
		Log.i(LOG_TAG, "baseSd = " + baseSd);

		String rootDir = intent.getStringExtra(ROOT_DIRECTORY);
		if (!TextUtils.isEmpty(rootDir)) {
			if (baseSd && this.sdDir.startsWith(rootDir)) {
				rootDir = this.sdDir;
			}
		} else {
			rootDir = baseSd ? this.sdDir : GlobalConsts.ROOT_PATH;
		}
		mFileViewInteractionHub.setRootPath(rootDir);

		/**
		 * 获取当前路径
		 * */
		String currentDir = FileExplorerPreferenceActivity
				.getPrimaryFolder(mActivity);
		Uri uri = intent.getData();
		if (uri != null) {
			if (baseSd && this.sdDir.startsWith(uri.getPath())) {
				// 当前路径是sdDir
				currentDir = this.sdDir;
			} else {
				// 当前路径设为Uri转码得到的路径
				currentDir = uri.getPath();
			}
		}
		mFileViewInteractionHub.setCurrentPath(currentDir);
		Log.i(LOG_TAG, "CurrentDir = " + currentDir);

		mBackspaceExit = (uri != null)
				&& (TextUtils.isEmpty(action) || (!action
						.equals(Intent.ACTION_PICK) && !action
						.equals(Intent.ACTION_GET_CONTENT)));

		mFileListView.setAdapter(mAdapter);
		mFileViewInteractionHub.refreshFileList();

		// 注册sd卡挂载广播
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
		intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
		intentFilter.addDataScheme("file");
		mActivity.registerReceiver(mReceiver, intentFilter);

		updateUI();
		setHasOptionsMenu(true);
		return mRootView;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		mActivity.unregisterReceiver(mReceiver);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		mFileViewInteractionHub.onPrepareOptionsMenu(menu);
		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		mFileViewInteractionHub.onCreateOptionsMenu(menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onBack() {
		if (mBackspaceExit || !Util.isSDCardReady()
				|| mFileViewInteractionHub == null) {
			return false;
		}
		return mFileViewInteractionHub.onBackPressed();
	}

	/**
	 * 职责：保存文件路径和对应位置
	 * */
	private class PathScrollPositionItem {
		String path;
		int pos;

		PathScrollPositionItem(String s, int p) {
			path = s;
			pos = p;
		}
	}

	// execute before change, return the memorized scroll position
	private int computeScrollPosition(String path) {
		int pos = 0;
		if (mPreviousPath != null) {
			if (path.startsWith(mPreviousPath)) {
				int firstVisiblePosition = mFileListView
						.getFirstVisiblePosition();
				if (mScrollPositionList.size() != 0
						&& mPreviousPath.equals(mScrollPositionList
								.get(mScrollPositionList.size() - 1).path)) {
					mScrollPositionList.get(mScrollPositionList.size() - 1).pos = firstVisiblePosition;
					Log.i(LOG_TAG, "computeScrollPosition: update item: "
							+ mPreviousPath + " " + firstVisiblePosition
							+ " stack count:" + mScrollPositionList.size());
					pos = firstVisiblePosition;
				} else {
					mScrollPositionList.add(new PathScrollPositionItem(
							mPreviousPath, firstVisiblePosition));
					Log.i(LOG_TAG, "computeScrollPosition: add item: "
							+ mPreviousPath + " " + firstVisiblePosition
							+ " stack count:" + mScrollPositionList.size());
				}
			} else {
				int i;
				boolean isLast = false;
				for (i = 0; i < mScrollPositionList.size(); i++) {
					if (!path.startsWith(mScrollPositionList.get(i).path)) {
						break;
					}
				}
				// navigate to a totally new branch, not in current stack
				if (i > 0) {
					pos = mScrollPositionList.get(i - 1).pos;
				}

				for (int j = mScrollPositionList.size() - 1; j >= i - 1
						&& j >= 0; j--) {
					mScrollPositionList.remove(j);
				}
			}
		}

		Log.i(LOG_TAG, "computeScrollPosition: result pos: " + path + " " + pos
				+ " stack count:" + mScrollPositionList.size());
		mPreviousPath = path;
		return pos;
	}

	/**
	 * 作用：刷新文件列表信息 
	 * 1.找出符合显示条件的FileInfo 
	 * 2.如果FileInfo为空时显示空View 
	 * 3.为ListView选中pos
	 * */
	public boolean onRefreshFileList(String path, FileSortHelper sort) {
		// file必须是目录文件
		File file = new File(path);
		if (!file.exists() || !file.isDirectory()) {
			return false;
		}
		// TODO 计算出新的pos
		final int pos = computeScrollPosition(path);
		// fileList指向mFileNameList
		ArrayList<FileInfo> fileList = mFileNameList;
		fileList.clear();
		// 得到过滤后的File数组
		File[] listFiles = file.listFiles(mFileCagetoryHelper.getFilter());
		if (listFiles == null)
			return true;

		for (File child : listFiles) {
			// do not show selected file if in move state
			if (mFileViewInteractionHub.isMoveState()
					&& mFileViewInteractionHub.isFileSelected(child.getPath()))
				continue;

			String absolutePath = child.getAbsolutePath();
			// 判断File是否Normal并且可以被显示（非隐藏）
			if (Util.isNormalFile(absolutePath)
					&& Util.shouldShowFile(absolutePath)) {
				FileInfo lFileInfo = Util.GetFileInfo(child,
						mFileCagetoryHelper.getFilter(), Settings.instance()
								.getShowDotAndHiddenFiles());
				if (lFileInfo != null) {
					fileList.add(lFileInfo);
				}
			}
		}

		sortCurrentList(sort);

		// 当没有文件信息时，显示空的View
		showEmptyView(fileList.size() == 0);
		// 在UI线程中为mFileListView选中pos
		mFileListView.post(new Runnable() {
			@Override
			public void run() {
				mFileListView.setSelection(pos);
			}
		});
		return true;
	}

	/**
	 * 更新UI，1.设置没有SD卡的View 2.设置导航ActionBar 3.判断sd卡是否准备好，刷新文件列表
	 * */
	private void updateUI() {
		// 设置noSdView，如果当前没有sd卡，就设置为GONE
		boolean sdCardReady = Util.isSDCardReady();
		View noSdView = mRootView.findViewById(R.id.sd_not_available_page);
		noSdView.setVisibility(sdCardReady ? View.GONE : View.VISIBLE);
		// 设置ActionBar
		View navigationBar = mRootView.findViewById(R.id.navigation_bar);
		navigationBar.setVisibility(sdCardReady ? View.VISIBLE : View.GONE);
		mFileListView.setVisibility(sdCardReady ? View.VISIBLE : View.GONE);

		// sd卡准备好了，刷新文件列表信息
		if (sdCardReady) {
			mFileViewInteractionHub.refreshFileList();
		}
	}

	/**
	 * 作用：显示空的View
	 * */
	private void showEmptyView(boolean show) {
		View emptyView = mRootView.findViewById(R.id.empty_view);
		if (emptyView != null)
			emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
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
			}

		});
	}

	@Override
	public void onPick(FileInfo f) {
		try {
			Intent intent = Intent.parseUri(Uri.fromFile(new File(f.filePath))
					.toString(), 0);
			mActivity.setResult(Activity.RESULT_OK, intent);
			mActivity.finish();
			return;
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean shouldShowOperationPane() {
		return true;
	}

	@Override
	public boolean onOperation(int id) {
		return false;
	}

	// 支持显示真实路径
	@Override
	public String getDisplayPath(String path) {
		if (path.startsWith(this.sdDir)
				&& !FileExplorerPreferenceActivity.showRealPath(mActivity)) {
			return getString(R.string.sd_folder)
					+ path.substring(this.sdDir.length());
		} else {
			return path;
		}
	}

	@Override
	public String getRealPath(String displayPath) {
		final String perfixName = getString(R.string.sd_folder);
		if (displayPath.startsWith(perfixName)) {
			return this.sdDir + displayPath.substring(perfixName.length());
		} else {
			return displayPath;
		}
	}

	@Override
	public boolean onNavigation(String path) {
		return false;
	}

	@Override
	public boolean shouldHideMenu(int menu) {
		return false;
	}

	public void copyFile(ArrayList<FileInfo> files) {
		mFileViewInteractionHub.onOperationCopy(files);
	}

	public void refresh() {
		if (mFileViewInteractionHub != null) {
			mFileViewInteractionHub.refreshFileList();
		}
	}

	public void moveToFile(ArrayList<FileInfo> files) {
		mFileViewInteractionHub.moveFileFrom(files);
	}

	public interface SelectFilesCallback {
		// files equals null indicates canceled
		void selected(ArrayList<FileInfo> files);
	}

	public void startSelectFiles(SelectFilesCallback callback) {
		mFileViewInteractionHub.startSelectFiles(callback);
	}

	@Override
	public FileIconHelper getFileIconHelper() {
		return mFileIconHelper;
	}

	public boolean setPath(String location) {
		if (!location.startsWith(mFileViewInteractionHub.getRootPath())) {
			return false;
		}
		mFileViewInteractionHub.setCurrentPath(location);
		mFileViewInteractionHub.refreshFileList();
		return true;
	}

	@Override
	public FileInfo getItem(int pos) {
		if (pos < 0 || pos > mFileNameList.size() - 1)
			return null;

		return mFileNameList.get(pos);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void sortCurrentList(FileSortHelper sort) {
		Collections.sort(mFileNameList, sort.getComparator());
		onDataChanged();
	}

	@Override
	public ArrayList<FileInfo> getAllFiles() {
		return mFileNameList;
	}

	@Override
	public void addSingleFile(FileInfo file) {
		mFileNameList.add(file);
		onDataChanged();
	}

	@Override
	public int getItemCount() {
		return mFileNameList.size();
	}

	@Override
	public void runOnUiThread(Runnable r) {
		mActivity.runOnUiThread(r);
	}
}
