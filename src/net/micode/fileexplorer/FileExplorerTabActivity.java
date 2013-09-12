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
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.ActionMode;

import java.util.ArrayList;

public class FileExplorerTabActivity extends Activity {
    private static final String INSTANCESTATE_TAB = "tab";
    ViewPager mViewPager;
    // ViewPager的Adapter, extends FragmentPagerAdapter
    TabsAdapter mTabsAdapter;
    
    /**
     * 学习ActionBar与ActionMode的结合使用 TODO 
     * */
    ActionMode mActionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_pager);
        mViewPager = (ViewPager) findViewById(R.id.pager);

        /**
         * 学习ActionBar与PageView结合使用
         * */
        
        // 设置ActionBar
        final ActionBar bar = getActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS); //设置为Tabs导航模式，一系类Tabs
        bar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME);

        mTabsAdapter = new TabsAdapter(this, mViewPager);
        
        mTabsAdapter.addTab(bar.newTab().setText(R.string.tab_sd),
                FileViewActivity.class, null); // FileViewActivity继承了Fragment
        mTabsAdapter.addTab(bar.newTab().setText(R.string.tab_remote),
                ServerControlActivity.class, null);
        if (savedInstanceState != null) {
            bar.setSelectedNavigationItem(savedInstanceState.getInt(INSTANCESTATE_TAB, 0));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(INSTANCESTATE_TAB, getActionBar().getSelectedNavigationIndex()); // 保存ActionBar的索引位置
    }

    @Override
    public void onBackPressed() {
        IBackPressedListener backPressedListener = (IBackPressedListener) mTabsAdapter
                .getItem(mViewPager.getCurrentItem());
        if (!backPressedListener.onBack()) {
            super.onBackPressed();
        }
    }

    public interface IBackPressedListener {
        /**
         * 处理back事件。
         * @return True: 表示已经处理; False: 没有处理，让基类处理。
         */
        boolean onBack();
    }

    public void setActionMode(ActionMode actionMode) {
        mActionMode = actionMode;
    }

    public ActionMode getActionMode() {
        return mActionMode;
    }

    /**
     * 这个类继承了FragmentPagerAdapter，用于将Fragment适配到PageView上,结合了ActionBar和PageView
     * This is a helper class that implements the management of tabs and all
     * details of connecting a ViewPager with associated TabHost.  It relies on a
     * trick.  Normally a tab host has a simple API for supplying a View or
     * Intent that each tab will show.  This is not sufficient for switching
     * between pages.  So instead we make the content part of the tab host
     * 0dp high (it is not shown) and the TabsAdapter supplies its own dummy
     * view to show as the tab content.  It listens to changes in tabs, and takes
     * care of switch to the correct paged in the ViewPager whenever the selected
     * tab changes.
     */
    public static class TabsAdapter extends FragmentPagerAdapter
            implements ActionBar.TabListener, ViewPager.OnPageChangeListener {
        /**
         * FragmentPagerAdapter主要是实现从一个Fragment到另一个Fragment的绑定
         * 实现TabListener，OnPageChangeListener实现ActionBar与PageView的互动
         * */
    	
    	
    	private final Context mContext;
        private final ActionBar mActionBar;
        private final ViewPager mViewPager;
        private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

        static final class TabInfo {
            private final Class<?> clss;
            private final Bundle args;
            private Fragment fragment;

            TabInfo(Class<?> _class, Bundle _args) {
                clss = _class;
                args = _args;
            }
        }

        public TabsAdapter(Activity activity, ViewPager pager) {
            super(activity.getFragmentManager());
            mContext = activity;
            mActionBar = activity.getActionBar();
            mViewPager = pager;
            // ViewPager在此设置Adapter
            mViewPager.setAdapter(this);
            mViewPager.setOnPageChangeListener(this);
        }

        /**
         * 添加一个Tab，一个Tab关联上一个Fragment
         * 保存Class,Bundle,Fragment对象
         * */
        public void addTab(ActionBar.Tab tab, Class<?> clss, Bundle args) {
            TabInfo info = new TabInfo(clss, args);
            // 将TabInfo设置到tab
            tab.setTag(info);
            tab.setTabListener(this);
            mTabs.add(info);
            mActionBar.addTab(tab);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() { // PageAdapter
            return mTabs.size();
        }

        @Override
        public Fragment getItem(int position) { // FragmentPageAdapter
            TabInfo info = mTabs.get(position);
            if (info.fragment == null) {
            	// 通过context, Fragment类名，Bundles来实例化Fragment
                info.fragment = Fragment.instantiate(mContext, info.clss.getName(), info.args);
            }
            return info.fragment;
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { // OnPageChangeListener
        }

        @Override
        public void onPageSelected(int position) { // OnPageChangeListener
            mActionBar.setSelectedNavigationItem(position); // 实现滑动page与ActionBar互动，估计触发onTabSelected
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) { // TabListener
            Object tag = tab.getTag();
            for (int i=0; i<mTabs.size(); i++) {
                if (mTabs.get(i) == tag) {
                    mViewPager.setCurrentItem(i);
                }
            }
            if(!tab.getText().equals(mContext.getString(R.string.tab_sd))) {
                ActionMode actionMode = ((FileExplorerTabActivity) mContext).getActionMode();
                if (actionMode != null) {
                    actionMode.finish();
                }
            }
        }

        @Override
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        }

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    }
}
