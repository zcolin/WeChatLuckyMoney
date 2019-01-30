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
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

public class QiangHongBaoService extends AccessibilityService {
    static final int STATUS_ENTER_NONE      = 0;
    static final int STATUS_ENTER_CHATPAGE  = 1;
    static final int STATUS_RETURN_CHATPAGE = 2;


    /**
     * TODO 直接使用findAccessibilityNodeInfosByText获取不到，这个Id没验证在不同的手机和不同的微信版本会不会改变
     */
    static final String STR_ID_MAINPAGE_HONGBAO = "com.tencent.mm:id/b4q";//"[微信红包]"出现的地方
    static final int    I_HEIGHT_FLAG           = 55;                //高度标准，红包距离底部的距离超过这个标准判断为新红包(现在微信的红包抢过的状态会改变，所以不用判断高度了)

    /**
     * 微信的包名
     */
    static final String WECHAT_PACKAGENAME    = "com.tencent.mm";
    /**
     * 拆红包类
     */
    static final String WECHAT_RECEIVER_CALSS = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotHookReceiveUI";
    /**
     * 红包详情类
     */
    static final String WECHAT_DETAIL         = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI";

    /**
     * 存放上次红包布局资源的Id, 因为ListView采用ViewHolder重用，所以只能保证当前屏幕内的id是唯一的，
     * 所以不能使用集合存储已经点过的资源Id，只能当前屏幕内不重复点击
     */
    private long lastSourceId;

    /**
     * 主页进入的状态
     */
    private int mainEnterStatus;

    private int   screenHeight;
    private float screenDensity;

    private KeyguardManager.KeyguardLock mKeyGuardLock;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        final int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {//通知状态改变
            onNotifyStateChanged(event);
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {//屏幕改变,如弹窗框，转换页面
            onWindowStateChange(event);
            System.out.println(event.getClassName() + "*****" + event.getContentDescription());
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {//屏幕内容改变，如拖动，新消息滚动
            onWindowContentChanged(event);
            System.out.println(event.getClassName() + "===" + event.getContentDescription());
        }
    }

    @Override
    public void onInterrupt() {
        Toast.makeText(this, "中断抢红包服务", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        screenDensity = Util.getScreenDensity(getApplicationContext());
        screenHeight = Util.getScreenHeight(getApplicationContext());
        Toast.makeText(this, "连接抢红包服务", Toast.LENGTH_SHORT).show();
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
            mKeyGuardLock = Util.disableKeylock(getApplicationContext());
            Util.acquireWakeLock(getApplicationContext());

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
        //查看是需要拆红包、红包已经被抢完.红包弹出框
        if (WECHAT_RECEIVER_CALSS.equals(event.getClassName())) {
            processInHongBaoDialog();
        }
        //拆完红包后看详细的纪录界面
        else if (WECHAT_DETAIL.equals(event.getClassName())) {
            SystemClock.sleep(500);
            if (mainEnterStatus == STATUS_ENTER_CHATPAGE) {
                mainEnterStatus = STATUS_RETURN_CHATPAGE;
            }
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        }
    }

    /**
     * 监听窗口内容数据信息处理
     */
    private void onWindowContentChanged(AccessibilityEvent event) {
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
        if (rootNodeInfo != null && (rootNodeInfo.findAccessibilityNodeInfosByText("更多功能按钮，已折叠").isEmpty() && rootNodeInfo.findAccessibilityNodeInfosByText("更多功能按钮，已展开")
                                                                                                                          .isEmpty()) && rootNodeInfo.findAccessibilityNodeInfosByText("更多功能按钮")
                                                                                                                                                     .size() > 0) {
            isFlag = true;
            lastSourceId = 0; //从聊天页面出来了，清除保存的sourceId；
            AccessibilityNodeInfo nodeInfo = event.getSource();
            if (nodeInfo != null) {
                List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(STR_ID_MAINPAGE_HONGBAO);
                for (AccessibilityNodeInfo accessibilityNodeInfo : list) {
                    String str = String.valueOf(accessibilityNodeInfo.getText());
                    if (str != null && str.contains("[微信红包]")) {
                        accessibilityNodeInfo.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);//进入聊天页面
                        mainEnterStatus = STATUS_ENTER_CHATPAGE;
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
        List<AccessibilityNodeInfo> list = null;
        AccessibilityNodeInfo rootNodeInfo = null;
        if (nodeInfo != null && "android.widget.TextView".equals(nodeInfo.getClassName())) {//消息内容改变的时候
            rootNodeInfo = getRootInActiveWindow();
            if (rootNodeInfo != null) {
                list = rootNodeInfo.findAccessibilityNodeInfosByText("微信红包");
                checkList(list);
                if (list != null && list.size() > 0) {

                    /**
                     * 这个地方之后收到新消息之后才会进来，普通滑动无法进入到这里面
                     *
                     *  接收到新消息的时候，如果是红包，则红包的sourceId还是和刚才的Id一样，并且内容包括各种Id都没有任何变化 
                     *  所以通过Id无法判断是不是来新的红包了。
                     *
                     *  在此我收到新消息之后，判断最后一个红包bottom距底部的位置<规定的高度,则判断为这是一个新红包，需要抢
                     *  这样如果红包之后发一条文字信息，可能还会打开一次，但是这样是我能想到的最好的解决方案了
                     */
                    for (int i = list.size() - 1; i >= 0; i--) {
                        Rect rect = new Rect();
                        //自己发的查看红包点击之后状态不变
                        if (list.get(i).getParent() != null && list.get(i).getParent().getParent() != null) {
                            if (list.get(i).getParent() != null && list.get(i).getParent().getChild(1) != null && "微信红包".equals(list.get(i).getParent().getChild(1).getText())) {
                                list.get(i).getParent().getBoundsInScreen(rect);
                                if ((screenHeight - rect.bottom) / screenDensity < I_HEIGHT_FLAG) {//以此判断红包在最后一个,也就是刚发的
                                    lastSourceId = 0;
                                }
                            }
                        }
                    }
                }
            }
        } else if (nodeInfo != null && nodeInfo.getChildCount() > 0) {//拖动内容的时候
            list = nodeInfo.findAccessibilityNodeInfosByText("微信红包");
            checkList(list);
        }

        if (list != null && list.size() > 0) {
            //只抢最后一个
            for (int i = list.size() - 1; i >= 0; i--) {
                AccessibilityNodeInfo parent = list.get(i).getParent();
                if (parent != null) {
                    long sourceId = getSouceNodeId(parent);//在listview中会循环出现，所以只能保证在一屏下是唯一的，而不是全局唯一
                    if ("android.widget.LinearLayout".equals(parent.getClassName()) && lastSourceId != sourceId) {
                        lastSourceId = sourceId;
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    }
                    break;
                }
            }
        } else {
            //抢完所有红包之后，如果是主页面进来的，则返回主页面
            if (mainEnterStatus == STATUS_RETURN_CHATPAGE) {
                mainEnterStatus = STATUS_ENTER_NONE;
                SystemClock.sleep(500);
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            }
        }

        if (nodeInfo != null) {
            nodeInfo.recycle();
        }
        if (rootNodeInfo != null) {
            rootNodeInfo.recycle();
        }
    }

    /**
     * 通过微信红包获取的值可能不准，此时需要再通过其他字段校验
     */
    private void checkList(List<AccessibilityNodeInfo> list) {
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                AccessibilityNodeInfo parent = list.get(i).getParent();
                if (parent != null && "android.widget.LinearLayout".equals(parent.getClassName()) && parent.getParent() != null && "android.widget.RelativeLayout".equals(parent.getParent()
                                                                                                                                                                                .getClassName())) {
                    List<AccessibilityNodeInfo> tempList = parent.findAccessibilityNodeInfosByText("已领取");
                    if (tempList == null || tempList.size() == 0) {
                        tempList = parent.findAccessibilityNodeInfosByText("已被领完");
                    }
                    if (tempList == null || tempList.size() == 0) {
                        tempList = parent.findAccessibilityNodeInfosByText("已过期");
                    }
                    if (tempList != null && tempList.size() > 0) {
                        list.remove(i);
                        i--;
                    }
                }
            }
        }
    }

    /*
     * 红包Dialog界面的处理
     * 如果还没有被拆则进行拆红包，拆完之后返回
     * 如果已经被抢完，则直接返回
     */
    private void processInHongBaoDialog() {
        AccessibilityNodeInfo nodeInfo = null;
        int sleepTime = 300 + new Random().nextInt(300);//此页面不延时获取不到，随机延时是为了防止被微信检测为外挂
        while (sleepTime < 2000 && nodeInfo == null) {
            SystemClock.sleep(sleepTime);
            sleepTime += new Random().nextInt(300);
            nodeInfo = getRootInActiveWindow();
        }

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
                if (list == null || list.size() == 0) {
                    list = nodeInfo.findAccessibilityNodeInfosByText("该红包已超过24小时");
                }
                if (list != null && list.size() > 0) {
                    if (mainEnterStatus == STATUS_ENTER_CHATPAGE) {
                        mainEnterStatus = STATUS_RETURN_CHATPAGE;
                    }
                    performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                }
            }
            nodeInfo.recycle();
        }

        //如果是自动解锁的，此时需要锁上屏幕，为下一次自动解锁
        if (mKeyGuardLock != null) {
            mKeyGuardLock.reenableKeyguard();
            mKeyGuardLock = null;
        }
    }

    /**
     * 获取sourdeId，当前屏幕唯一
     */
    private long getSouceNodeId(AccessibilityNodeInfo info) {
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
}
