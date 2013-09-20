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
import java.io.FilenameFilter;
import java.util.ArrayList;

import android.os.AsyncTask;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

/**
 * 职责：对文件的复制，删除，移动（剪切）操作提供支持
 * */
public class FileOperationHelper {
	private static final String LOG_TAG = "FileOperation";

	/**
	 * 可能被许多线程访问，需要同步
	 * */
	private ArrayList<FileInfo> mCurFileNameList = new ArrayList<FileInfo>();

	private boolean mMoving;

	private IOperationProgressListener mOperationListener;

	private FilenameFilter mFilter;

	public interface IOperationProgressListener {
		void onFinish();

		void onFileChanged(String path);
	}

	public FileOperationHelper(IOperationProgressListener l) {
		mOperationListener = l;
	}

	public void setFilenameFilter(FilenameFilter f) {
		mFilter = f;
	}

	public boolean CreateFolder(String path, String name) {
		Log.v(LOG_TAG, "CreateFolder >>> " + path + "," + name);

		File f = new File(Util.makePath(path, name));
		if (f.exists())
			return false;

		return f.mkdir();
	}

	/**
	 * 实际效果是把files添加到mCurFileNameList
	 * */
	public void Copy(ArrayList<FileInfo> files) {
		copyFileList(files);
	}

	/**
	 * 把mCurFileNameList下的文件paste到指定path
	 * */
	public boolean Paste(String path) {
		if (mCurFileNameList.size() == 0)
			return false;

		final String _path = path;
		// 后台线程执行copy
		asnycExecute(new Runnable() {
			@Override
			public void run() {
				for (FileInfo f : mCurFileNameList) {
					CopyFile(f, _path);
				}
				// 调用回调方法，传入sd根目录
				mOperationListener.onFileChanged(Environment
						.getExternalStorageDirectory().getAbsolutePath());

				clear();
			}
		});

		return true;
	}

	public boolean canPaste() {
		return mCurFileNameList.size() != 0;
	}

	public void StartMove(ArrayList<FileInfo> files) {
		if (mMoving)
			return;

		mMoving = true;
		copyFileList(files);
	}

	public boolean isMoveState() {
		return mMoving;
	}

	public boolean canMove(String path) {
		for (FileInfo f : mCurFileNameList) {
			if (!f.IsDir)
				continue;

			if (Util.containsPath(f.filePath, path))
				return false;
		}

		return true;
	}

	public void clear() {
		synchronized (mCurFileNameList) {
			mCurFileNameList.clear();
		}
	}

	/**
	 * 作用：置mMoving为false，依然将剩余的文件移动到path
	 * */
	public boolean EndMove(String path) {
		if (!mMoving)
			return false;
		mMoving = false;

		if (TextUtils.isEmpty(path))
			return false;

		final String _path = path;
		asnycExecute(new Runnable() {
			@Override
			public void run() {
				// 这里没有获取对象锁！！
				for (FileInfo f : mCurFileNameList) {
					MoveFile(f, _path);
				}

				mOperationListener.onFileChanged(Environment
						.getExternalStorageDirectory().getAbsolutePath());

				clear();
			}
		});

		return true;
	}

	public ArrayList<FileInfo> getFileList() {
		return mCurFileNameList;
	}

	private void asnycExecute(Runnable r) {
		final Runnable _r = r;
		new AsyncTask() {
			@Override
			protected Object doInBackground(Object... params) {
				synchronized (mCurFileNameList) {
					_r.run();
				}
				if (mOperationListener != null) {
					mOperationListener.onFinish();
				}

				return null;
			}
		}.execute();
	}

	public boolean isFileSelected(String path) {
		synchronized (mCurFileNameList) {
			for (FileInfo f : mCurFileNameList) {
				if (f.filePath.equalsIgnoreCase(path))
					return true;
			}
		}
		return false;
	}

	/**
	 * 作用：文件f重命名为newName
	 * */
	public boolean Rename(FileInfo f, String newName) {
		if (f == null || newName == null) {
			Log.e(LOG_TAG, "Rename: null parameter");
			return false;
		}

		File file = new File(f.filePath);
		String newPath = Util.makePath(Util.getPathFromFilepath(f.filePath),
				newName);
		// file需要scan
		final boolean needScan = file.isFile();
		try {
			// 使用了剪切
			boolean ret = file.renameTo(new File(newPath));
			if (ret) {
				if (needScan) {
					mOperationListener.onFileChanged(f.filePath);
				}
				mOperationListener.onFileChanged(newPath);
			}
			return ret;
		} catch (SecurityException e) {
			Log.e(LOG_TAG, "Fail to rename file," + e.toString());
		}
		return false;
	}

	/**
	 * 删除
	 * */
	public boolean Delete(ArrayList<FileInfo> files) {
		copyFileList(files);
		asnycExecute(new Runnable() {
			@Override
			public void run() {
				for (FileInfo f : mCurFileNameList) {
					DeleteFile(f);
				}

				mOperationListener.onFileChanged(Environment
						.getExternalStorageDirectory().getAbsolutePath());

				clear();
			}
		});
		return true;
	}

	/**
	 * 具体的删除操作
	 * */
	protected void DeleteFile(FileInfo f) {
		if (f == null) {
			Log.e(LOG_TAG, "DeleteFile: null parameter");
			return;
		}

		File file = new File(f.filePath);
		boolean directory = file.isDirectory();
		if (directory) {
			// 如果是目录，递归删除目录中的文件
			for (File child : file.listFiles(mFilter)) {
				if (Util.isNormalFile(child.getAbsolutePath())) {
					DeleteFile(Util.GetFileInfo(child, mFilter, true));
				}
			}
		}
		// 最后删除该文件
		file.delete();

		Log.v(LOG_TAG, "DeleteFile >>> " + f.filePath);
	}

	/**
	 * 作用：这才是货真价实的copy，不过具体的copy还是调用的Util.copy
	 * 注意：递归调用，空目录不复制
	 * */
	private void CopyFile(FileInfo f, String dest) {
		if (f == null || dest == null) {
			Log.e(LOG_TAG, "CopyFile: null parameter");
			return;
		}

		File file = new File(f.filePath);
		// 空目录并没有复制
		if (file.isDirectory()) {

			// directory exists in destination, rename it
			String destPath = Util.makePath(dest, f.fileName);
			File destFile = new File(destPath);
			int i = 1;
			// 存在，重命名
			while (destFile.exists()) {
				destPath = Util.makePath(dest, f.fileName + " " + i++);
				destFile = new File(destPath);
			}
			for (File child : file.listFiles(mFilter)) {
				if (!child.isHidden()
						&& Util.isNormalFile(child.getAbsolutePath())) {
					CopyFile(Util.GetFileInfo(child, mFilter, Settings
							.instance().getShowDotAndHiddenFiles()), destPath);
				}
			}
		} else {
			String destFile = Util.copyFile(f.filePath, dest);
		}
		Log.v(LOG_TAG, "CopyFile >>> " + f.filePath + "," + dest);
	}

	/**
	 * 作用：剪切，目标文件f移动到新的路径dest下
	 * @param f 目标文件
	 * @param dest 新的路径
	 * */
	private boolean MoveFile(FileInfo f, String dest) {
		Log.v(LOG_TAG, "MoveFile >>> " + f.filePath + "," + dest);

		if (f == null || dest == null) {
			Log.e(LOG_TAG, "CopyFile: null parameter");
			return false;
		}

		File file = new File(f.filePath);
		String newPath = Util.makePath(dest, f.fileName);
		try {
			// 剪切到新的路径下
			return file.renameTo(new File(newPath));
		} catch (SecurityException e) {
			Log.e(LOG_TAG, "Fail to move file," + e.toString());
		}
		return false;
	}

	/**
	 * files添加到mCurFileNameList
	 * */
	private void copyFileList(ArrayList<FileInfo> files) {
		// 获取对象锁，避免其他线程操作mCurFileNameList
		synchronized (mCurFileNameList) {
			mCurFileNameList.clear();
			for (FileInfo f : files) {
				mCurFileNameList.add(f);
			}
		}
	}

}
