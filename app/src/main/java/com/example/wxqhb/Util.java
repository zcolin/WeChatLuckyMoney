/*
 * *********************************************************
 *   author   colin
 *   company  fosung
 *   email    wanglin2046@126.com
 *   date     16-12-13 下午5:27
 * ********************************************************
 */
package com.example.wxqhb;

import android.app.KeyguardManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.util.List;

/**
 * 屏幕工具
 */
public class Util {

    /**
     * 获取屏幕高度
     */
    public static int getScreenHeight(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        wm.getDefaultDisplay()
          .getMetrics(outMetrics);
        return outMetrics.heightPixels;
    }

    /**
     * 获取屏幕density
     */
    public static float getScreenDensity(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        wm.getDefaultDisplay()
          .getMetrics(outMetrics);
        return outMetrics.density;
    }


    /**
     * 解锁屏幕、强制点亮屏幕
     * <p>
     * <pre>
     * 	标记值						CPU		屏幕				键盘
     * 	PARTIAL_WAKE_LOCK			开启		关闭				关闭
     * 	SCREEN_DIM_WAKE_LOCK		开启		调暗（Dim）		关闭
     * 	SCREEN_BRIGHT_WAKE_LOCK		开启		调亮（Bright）	关闭
     * 	FULL_WAKE_LOCK				开启		调亮（Bright）	调亮（Bright）
     *  </pre>
     */
    public static void acquireWakeLock(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (!pm.isScreenOn()) {
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP |
                    PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SimpleTimer");
            if (!wl.isHeld()) {
                wl.acquire();
                wl.release();
            }
        }
    }

    /**
     * 禁用键盘锁 、解锁键盘
     */
    public static KeyguardManager.KeyguardLock disableKeylock(Context context) {
        KeyguardManager mKeyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock mKeyguardLock = null;
        if (mKeyguardManager.isKeyguardLocked()) {
            mKeyguardLock = mKeyguardManager.newKeyguardLock("unLock");
            mKeyguardLock.disableKeyguard();
        }
        return mKeyguardLock;

    }

    /**
     * 5.0之后不支持获取topActiivty的名字，只支持获取包名
     *
     * @deprecated 不再检测栈顶的app是不是微信
     */
    private String getTopApp(Context context) {
        String topActivity = null;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager m = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (m != null) {
                long now = System.currentTimeMillis();
                //获取60秒之内的应用数据  
                List<UsageStats> stats = m.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60 * 1000, now);

                //取得最近运行的一个app，即当前运行的app  
                if ((stats != null) && (!stats.isEmpty())) {
                    int j = 0;
                    for (int i = 0; i < stats.size(); i++) {
                        if (stats.get(i)
                                 .getLastTimeUsed() > stats.get(j)
                                                           .getLastTimeUsed()) {
                            j = i;
                        }
                    }
                    topActivity = stats.get(j)
                                       .getPackageName();
                }
            }
        }
        return topActivity;
    }

}
