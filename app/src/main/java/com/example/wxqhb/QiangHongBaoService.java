/*
 * *********************************************************
 *   author   colin
 *   company  fosung
 *   email    wanglin2046@126.com
 *   date     16-12-9 下午2:04
 * ********************************************************
 */

package com.example.wxqhb;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class QiangHongBaoService extends AccessibilityService {

    static final String TAG                   = "QiangHongBao";
    /**
     * 微信的包名
     */
    static final String WECHAT_PACKAGENAME    = "com.tencent.mm";
    /**
     * 拆红包类
     */
    static final String WECHAT_RECEIVER_CALSS = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI";
    /**
     * 红包详情类
     */
    static final String WECHAT_DETAIL         = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI";
    /**
     * 微信主界面或者是聊天界面
     */
    static final String WECHAT_LAUNCHER       = "com.tencent.mm.ui.LauncherUI";

    /**
     * 存放上次红包布局资源的Id, 因为ListView采用ViewHolder重用，所以只能保证当前屏幕内的id是唯一的，
     * 所以不能使用集合存储已经点过的资源Id，只能当前屏幕内不重复点击
     */
    private long lastSourceId;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        final int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {//通知状态改变
            onNotifyStateChanged(event);
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {//屏幕改变,如弹窗框，转换页面
            onWindowStateChange(event);
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {//屏幕内容改变，如拖动，新消息滚动
            onWindowContentChanged(event);
        }
    }

    @Override
    public void onInterrupt() {
        Toast.makeText(this, "中断抢红包服务", Toast.LENGTH_SHORT)
             .show();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Toast.makeText(this, "连接抢红包服务", Toast.LENGTH_SHORT)
             .show();
    }

    /**
     * 监听通知信息处理
     */
    private void onNotifyStateChanged(AccessibilityEvent event) {
        List<CharSequence> texts = event.getText();
        for (CharSequence t : texts) {
            String text = String.valueOf(t);
            if (text.contains("[微信红包]")) {
                openNotify(event);
                break;
            }
        }
    }

    /**
     * 处理通知信息数据
     */
    private void openNotify(AccessibilityEvent event) {
        if (event.getParcelableData() == null || !(event.getParcelableData() instanceof Notification)) {
            return;
        }

        try {
            Notification notification = (Notification) event.getParcelableData();
            PendingIntent pendingIntent = notification.contentIntent;
            pendingIntent.send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }

    /**
     * 监听窗口改变状态信息处理
     */
    private void onWindowStateChange(AccessibilityEvent event) {
        //查看是需要拆红包、红包已经被抢完
        if (WECHAT_RECEIVER_CALSS.equals(event.getClassName())) {
            processInHongBaoDialog();
        }
        //拆完红包后看详细的纪录界面
        else if (WECHAT_DETAIL.equals(event.getClassName())) {
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        }
        //在聊天界面,去点中红包
        else if (WECHAT_LAUNCHER.equals(event.getClassName())) {
            openHongBaoInChatPage(event);
        }
    }

    /**
     * 监听窗口内容数据信息处理
     */
    private void onWindowContentChanged(AccessibilityEvent event) {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (!openHongBaoInMainPage(event)) {
            openHongBaoInChatPage(event);
        }
    }

    /**
     * 在微信主页面的信息处理
     */
    private boolean openHongBaoInMainPage(AccessibilityEvent event) {
        boolean isFlag = false;
        AccessibilityNodeInfo rootNodeInfo = getRootInActiveWindow();
        /*在聊天界面才会有"更多功能按钮,已折叠"，主页面不会有, 由此判断是不是在主页面*/
        if (rootNodeInfo != null && rootNodeInfo.findAccessibilityNodeInfosByText("更多功能按钮，已折叠")
                                                .isEmpty()
                && rootNodeInfo.findAccessibilityNodeInfosByText("更多功能按钮")
                               .size() > 0) {
            isFlag = true;
            lastSourceId = 0; //从聊天页面出来了，清除保存的sourceId；
            AccessibilityNodeInfo nodeInfo = event.getSource();
            if (nodeInfo != null) {
                /**
                 * TODO 直接使用findAccessibilityNodeInfosByText获取不到，这个Id没验证在不同的手机会不会改变
                 */
                List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/aef");
                for (AccessibilityNodeInfo accessibilityNodeInfo : list) {
                    if ("[微信红包]".equals(String.valueOf(accessibilityNodeInfo.getText()))) {
                        accessibilityNodeInfo.getParent()
                                             .performAction(AccessibilityNodeInfo.ACTION_CLICK);//进入聊天页面
                        break;
                    }
                }
            }
            rootNodeInfo.recycle();
        }
        return isFlag;
    }


    /**
     * 在微信（群）聊天页面的信息处理
     */
    private void openHongBaoInChatPage(AccessibilityEvent event) {
        AccessibilityNodeInfo nodeInfo = event.getSource();
        List<AccessibilityNodeInfo> list = new ArrayList<>();
        if (nodeInfo != null && nodeInfo.getChildCount() > 0) {
            List<AccessibilityNodeInfo> list1 = nodeInfo.findAccessibilityNodeInfosByText("查看红包");//自己发的红包
            List<AccessibilityNodeInfo> list2 = nodeInfo.findAccessibilityNodeInfosByText("领取红包");//别人发的红包
            if (list1.size() > 0) {
                list.addAll(list1);
            }
            if (list2.size() > 0) {
                list.addAll(list2);
            }

            //只抢最后一个
            for (int i = list.size() - 1; i >= 0; i--) {
                AccessibilityNodeInfo parent = list.get(i)
                                                   .getParent();
                if (parent != null) {
                    long sourceId = getSourdeId(parent);//在listview中会循环出现，所以只能保证在一屏下是唯一的，而不是全局唯一
                    if ("android.widget.LinearLayout".equals(parent.getClassName()) && lastSourceId != sourceId) {
                        lastSourceId = sourceId;
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    }
                    break;
                }
            }
            nodeInfo.recycle();
        }
    }

    /*
     * 红包Dialog界面的处理
     * 如果还没有被拆则进行拆红包，拆完之后返回
     * 如果已经被抢完，则直接返回
     */
    private void processInHongBaoDialog() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            boolean isHave = false;
            int count = nodeInfo.getChildCount();
            for (int i = 0; i < count; i++) {
                AccessibilityNodeInfo childNode = nodeInfo.getChild(i);
                if ("android.widget.Button".equals(childNode.getClassName())) {
                    isHave = true;
                    childNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }

            if (!isHave) {
                List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText("红包派完了");
                if (list != null && list.size() > 0) {
                    performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                }
            }
            nodeInfo.recycle();
        }
    }

    /**
     * 获取sourdeId，当前屏幕唯一
     */
    private long getSourdeId(AccessibilityNodeInfo info) {
        long sourceId = 0;
        try {
            Method method = AccessibilityNodeInfo.class.getMethod("getSourceNodeId");
            method.setAccessible(true);
            Object obj = method.invoke(info);
            sourceId = (Long) obj;
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return sourceId;
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
                Log.i(TAG, "top running app is : " + topActivity);
            }
        }
        return topActivity;
    }
}
