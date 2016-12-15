/*
 * *********************************************************
 *   author   colin
 *   company  fosung
 *   email    wanglin2046@126.com
 *   date     16-12-15 上午9:22
 * ********************************************************
 */

package com.example.wxqhb;

import android.app.Application;
import android.widget.Toast;

/**
 * 程序入口
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Toast.makeText(App.this, "程序遇到错误:" + ex.getMessage(), Toast.LENGTH_LONG)
                     .show();
                ex.printStackTrace();
            }
        });
    }
}
