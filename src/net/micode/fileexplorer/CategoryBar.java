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

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * 职责：自定义的View，用于绘制容量显示条
 * */
@SuppressLint("DrawAllocation")
public class CategoryBar extends View {
    private static final String LOG_TAG = "CategoryBar";

    private static final int MARGIN = 12;

    private static final int ANI_TOTAL_FRAMES = 10;

    private static final int ANI_PERIOD = 100;
    private Timer timer;

    /**
     * 职责：数据类，保存的是要绘制的信息
     * */
    private class Category {
        /**
         * mensize
         * */
    	public long value;

        public long tmpValue; // used for animation

        public long aniStep; // animation step

        /**
         * 图片资源id
         * */
        public int resImg;
    }

    private ArrayList<Category> categories = new ArrayList<Category>();

    private long mFullValue;

    /**
     * 应该是设置总的mensize
     * */
    public void setFullValue(long value) {
        mFullValue = value;
    }

    public CategoryBar(Context context) {
        this(context, null);
    }

    public CategoryBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CategoryBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void addCategory(int categoryImg) {
        Category ca = new Category();
        ca.resImg = categoryImg;
        categories.add(ca);
    }

    public boolean setCategoryValue(int index, long value) {
        if (index < 0 || index >= categories.size())
            return false;
        categories.get(index).value = value;
        // 重绘?
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
    	// 根据resId获取Drawable对象
        Drawable d = getDrawable(R.drawable.category_bar_empty);
        // 获取宽度和高度
        int width = getWidth() - MARGIN * 2;
        int height = getHeight() - MARGIN * 2;
        // 是否水平?
        boolean isHorizontal = (width > height);
        // 构建Rect对象
        Rect bounds = null;
        if ( isHorizontal )
            bounds = new Rect(MARGIN, 0, MARGIN + width, d.getIntrinsicHeight());
        else
            bounds = new Rect(0, MARGIN, d.getIntrinsicWidth(), MARGIN + height);

        int beginning = MARGIN;
        if ( !isHorizontal ) beginning += height;
        // 设定Drawable对象的Rect
        d.setBounds(bounds);
        // 在当前canvas上画出图形
        d.draw(canvas);
        if (mFullValue != 0) {
            for (Category c : categories) {
            	// value是当前category占的mensize
                long value = (timer == null ? c.value : c.tmpValue);
                if ( isHorizontal ) {
                	// w = 比重*宽度 = 实际所占宽度
                    int w = (int) (value * width / mFullValue);
                    if (w == 0)
                        continue;
                    // 重新设定x坐标
                    bounds.left = beginning;
                    bounds.right = beginning + w;
                    d = getDrawable(c.resImg);
                    bounds.bottom = bounds.top + d.getIntrinsicHeight();
                    d.setBounds(bounds);
                    // 在当前canvas上画出
                    d.draw(canvas);
                    // 下一次x+w
                    beginning += w;
                }
                else {
                    int h = (int) (value * height / mFullValue);
                    if (h == 0)
                        continue;
                    bounds.bottom = beginning;
                    bounds.top = beginning - h;
                    d = getDrawable(c.resImg);
                    bounds.right = bounds.left + d.getIntrinsicWidth();
                    d.setBounds(bounds);
                    d.draw(canvas);
                    beginning -= h;
                }
            }
        }
        // 最后，画出圆角效果
        if ( isHorizontal ) {
        	// 水平，设置x
            bounds.left = 0;
            bounds.right = bounds.left + getWidth();
        }
        else {
        	// 垂直，设置y
            bounds.top = 0;
            bounds.bottom = bounds.top + getHeight();
        }
        // 画圆角
        d = getDrawable(R.drawable.category_bar_mask);
        d.setBounds(bounds);
        d.draw(canvas);
    }

    private Drawable getDrawable(int id) {
        return getContext().getResources().getDrawable(id);
    }

    /**
     * 每次增加一点
     * */
    private void stepAnimation() {
        if (timer == null)
            return;

        int finished = 0;
        for (Category c : categories) {
        	// 注意，每执行完只是+aniStep
            c.tmpValue += c.aniStep;
            if (c.tmpValue >= c.value) {
                c.tmpValue = c.value;
                finished++;
                // 全部执行结束，stop animation
                if (finished >= categories.size()) {
                    // stop animation
                    timer.cancel();
                    timer = null;
                    Log.v(LOG_TAG, "Animation stopped");
                    break;
                }
            }
        }
        // TODO 在非UI线程中使用，刷新UI
        postInvalidate();
    }

    synchronized public void startAnimation() {
        if (timer != null) {
            return;
        }

        Log.v(LOG_TAG, "startAnimation");

        for (Category c : categories) {
            c.tmpValue = 0;
            c.aniStep = c.value / ANI_TOTAL_FRAMES;
        }

        timer = new Timer();
        // 100ms一次
        timer.scheduleAtFixedRate(new TimerTask() {

            public void run() {
                stepAnimation();
            }

        }, 0, ANI_PERIOD);
    }
}
