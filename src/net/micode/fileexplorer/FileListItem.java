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

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

import net.micode.fileexplorer.FileViewInteractionHub.Mode;

/**
 * 职责：这个类是被重构过来的，集中处理item绑定的操作
 * */
public class FileListItem {
	/**
	 * 这个方法是被重构过来的，用来设定item中的views
	 * */
    public static void setupFileListItemInfo(Context context, View item,
            FileInfo fileInfo, FileIconHelper fileIcon,
            FileViewInteractionHub fileViewInteractionHub) {

        // if in moving mode, show selected file always
        if (fileViewInteractionHub.isMoveState()) {
            fileInfo.Selected = fileViewInteractionHub.isFileSelected(fileInfo.filePath);
        }
        /**
         * item中的checkbox用的是ImageView
         * */ 
        ImageView checkbox = (ImageView) item.findViewById(R.id.file_checkbox);
        if (fileViewInteractionHub.getMode() == Mode.Pick) {
            checkbox.setVisibility(View.GONE);
        } else {
            checkbox.setVisibility(fileViewInteractionHub.canShowCheckBox() ? View.VISIBLE : View.GONE);
            // 根据fileInfo.Selected设定Img
            checkbox.setImageResource(fileInfo.Selected ? R.drawable.btn_check_on_holo_light
                    : R.drawable.btn_check_off_holo_light);
            checkbox.setTag(fileInfo);
            // 将这个item设置为选中
            item.setSelected(fileInfo.Selected);
        }

        /**
         * 设定item中的textView
         * */
        Util.setText(item, R.id.file_name, fileInfo.fileName);
        Util.setText(item, R.id.file_count, fileInfo.IsDir ? "(" + fileInfo.Count + ")" : "");
        Util.setText(item, R.id.modified_time, Util.formatDateString(context, fileInfo.ModifiedDate));
        Util.setText(item, R.id.file_size, (fileInfo.IsDir ? "" : Util.convertStorage(fileInfo.fileSize)));

        /**
         * 采用的是FrameLayout来组织lFileImage和lFileImageFrame
         * */
        ImageView lFileImage = (ImageView) item.findViewById(R.id.file_image);
        ImageView lFileImageFrame = (ImageView) item.findViewById(R.id.file_image_frame);

        if (fileInfo.IsDir) {
        	// 目录则隐藏lFileImageFrame
            lFileImageFrame.setVisibility(View.GONE);
            lFileImage.setImageResource(R.drawable.folder);
        } else {
        	// 否则根据实际情况用FileIconHelper加载icon
            fileIcon.setIcon(fileInfo, lFileImage, lFileImageFrame);
        }
    }

    /**
     * 职责：对一个File Item被选中时所有改变的集中处理
     * */
    public static class FileItemOnClickListener implements OnClickListener {
        private Context mContext;
        private FileViewInteractionHub mFileViewInteractionHub;

        public FileItemOnClickListener(Context context,
                FileViewInteractionHub fileViewInteractionHub) {
            mContext = context;
            mFileViewInteractionHub = fileViewInteractionHub;
        }

        @Override
        public void onClick(View v) {
        	
        	/**
        	 * 当点击事件发生的时候
        	 * 1.设置Checkbox的图标
        	 * 2.在FileExplorerTabActivity中启动ActionMode
        	 * */
        	
            ImageView img = (ImageView) v.findViewById(R.id.file_checkbox);
            assert (img != null && img.getTag() != null);
            // 得到FileInfo
            FileInfo tag = (FileInfo) img.getTag();
            // 取个反
            tag.Selected = !tag.Selected;
            // 获取ActionMode
            ActionMode actionMode = ((FileExplorerTabActivity) mContext).getActionMode();
            if (actionMode == null) {
            	// startActionMode开启contextual action mode
                actionMode = ((FileExplorerTabActivity) mContext)
                        .startActionMode(new ModeCallback(mContext,   
                                mFileViewInteractionHub));
                // 保存ActionMode的状态
                ((FileExplorerTabActivity) mContext).setActionMode(actionMode);
            } else {
            	// 会调用onPreparedActionMode刷新
                actionMode.invalidate();
            }
            // 设定checkBox图片
            if (mFileViewInteractionHub.onCheckItem(tag, v)) {
                img.setImageResource(tag.Selected ? R.drawable.btn_check_on_holo_light
                        : R.drawable.btn_check_off_holo_light);
            } else {
                tag.Selected = !tag.Selected;
            }
            // 更新ActionMode标题：选中了xx个items
            Util.updateActionModeTitle(actionMode, mContext,
                    mFileViewInteractionHub.getSelectedFileList().size());
        }
    }

    /**
     * 职责：根据点击重写ActionMode的回调方法
     * */
    public static class ModeCallback implements ActionMode.Callback {
        
    	/**
    	 * ActionMode.Callback接口实现了许多回调方法
    	 * */
    	
    	private Menu mMenu;
        private Context mContext;
        private FileViewInteractionHub mFileViewInteractionHub;

        /**
         * 初始化菜单“全选”和“取消”选项
         * */
        private void initMenuItemSelectAllOrCancel() {
        	// 当前File Item是否全选
            boolean isSelectedAll = mFileViewInteractionHub.isSelectedAll();
            // 根据当前是否全选对“取消”和“全选”进行设置
            mMenu.findItem(R.id.action_cancel).setVisible(isSelectedAll);
            mMenu.findItem(R.id.action_select_all).setVisible(!isSelectedAll);
        }

        /**
         * 滑动到SD卡导航页面
         * */
        private void scrollToSDcardTab() {
            ActionBar bar = ((FileExplorerTabActivity) mContext).getActionBar();
            if (bar.getSelectedNavigationIndex() != Util.SDCARD_TAB_INDEX) {
                bar.setSelectedNavigationItem(Util.SDCARD_TAB_INDEX);
            }
        }

        public ModeCallback(Context context,
                FileViewInteractionHub fileViewInteractionHub) {
            mContext = context;
            mFileViewInteractionHub = fileViewInteractionHub;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = ((Activity) mContext).getMenuInflater();
            // 先保存这个menu的引用
            mMenu = menu;
            // 注入正经的menu,有剪切图形的！
            inflater.inflate(R.menu.operation_menu, mMenu);
            initMenuItemSelectAllOrCancel();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        	// 设置Menu中部分Item的可见情况
        	mMenu.findItem(R.id.action_copy_path).setVisible(
                    mFileViewInteractionHub.getSelectedFileList().size() == 1);
            mMenu.findItem(R.id.action_cancel).setVisible(
            		mFileViewInteractionHub.isSelected());
            mMenu.findItem(R.id.action_select_all).setVisible(
            		!mFileViewInteractionHub.isSelectedAll());
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            
        	/**
        	 * 响应ActionItem的点击事件
        	 * */
        	
        	switch (item.getItemId()) {
                case R.id.action_delete:
                    mFileViewInteractionHub.onOperationDelete();
                    mode.finish();
                    break;
                case R.id.action_copy:
                	// 获取FileViewActivity，再调用copyFile方法
                    ((FileViewActivity) ((FileExplorerTabActivity) mContext)
                            .getFragment(Util.SDCARD_TAB_INDEX))
                            .copyFile(mFileViewInteractionHub.getSelectedFileList());
                    // 结束当前ActionMode
                    mode.finish();
                    // 结束后重新导向到SD卡页面
                    scrollToSDcardTab();
                    break;
                case R.id.action_move:
                    ((FileViewActivity) ((FileExplorerTabActivity) mContext)
                            .getFragment(Util.SDCARD_TAB_INDEX))
                            .moveToFile(mFileViewInteractionHub.getSelectedFileList());
                    mode.finish();
                    scrollToSDcardTab();
                    break;
                case R.id.action_send:
                    mFileViewInteractionHub.onOperationSend();
                    mode.finish();
                    break;
                case R.id.action_copy_path:
                    mFileViewInteractionHub.onOperationCopyPath();
                    mode.finish();
                    break;
                case R.id.action_cancel:
                    mFileViewInteractionHub.clearSelection();
                    initMenuItemSelectAllOrCancel();
                    mode.finish();
                    break;
                case R.id.action_select_all:
                    mFileViewInteractionHub.onOperationSelectAll();
                    initMenuItemSelectAllOrCancel();
                    break;
            }
        	// 更新标题：选中了xx个Items
            Util.updateActionModeTitle(mode, mContext, mFileViewInteractionHub
                    .getSelectedFileList().size());
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mFileViewInteractionHub.clearSelection();
            ((FileExplorerTabActivity) mContext).setActionMode(null);
        }
    }
}
