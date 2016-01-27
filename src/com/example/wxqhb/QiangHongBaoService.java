package com.example.wxqhb;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class QiangHongBaoService extends AccessibilityService
{

	static final String	TAG						= "QiangHongBao";

	/** 
	* 微信的包名 
	*/
	static final String	WECHAT_PACKAGENAME		= "com.tencent.mm";
	/** 
	 * 拆红包类 
	 */
	static final String	WECHAT_RECEIVER_CALSS	= "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI";
	/** 
	 * 红包详情类 
	 */
	static final String	WECHAT_DETAIL			= "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI";
	/** 
	 * 微信主界面或者是聊天界面 
	 */
	static final String	WECHAT_LAUNCHER			= "com.tencent.mm.ui.LauncherUI";

	/** 红包消息的关键字*/
	static final String	HONGBAO_TEXT_KEY		= "[微信红包]";

	private boolean		isSelfSend;
	private long		lastSourceId;//避免一直死循环的调用STATE_CHANGE

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event)
	{
		final int eventType = event.getEventType();

		if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
		{
			onNotifyStateChanged(event);
		}
		else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
		{
			onWindowStateChange(event);
		} else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
		{
			onWindowContentChanged(event);
		}
	}

	@Override
	public void onInterrupt()
	{
		Toast.makeText(this, "中断抢红包服务", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onServiceConnected()
	{
		super.onServiceConnected();
		Toast.makeText(this, "连接抢红包服务", Toast.LENGTH_SHORT).show();
	}

	private void onNotifyStateChanged(AccessibilityEvent event)
	{
		List<CharSequence> texts = event.getText();
		for (CharSequence t : texts)
		{
			String text = String.valueOf(t);
			if (text.contains(HONGBAO_TEXT_KEY))
			{
				openNotify(event);
				break;
			}
		}
	}

	private void openNotify(AccessibilityEvent event)
	{
		if (event.getParcelableData() == null || !(event.getParcelableData() instanceof Notification))
		{
			return;
		}

		try
		{
			Notification notification = (Notification) event.getParcelableData();
			PendingIntent pendingIntent = notification.contentIntent;
			pendingIntent.send();
		} catch (PendingIntent.CanceledException e)
		{
			e.printStackTrace();
		}
	}

	private void onWindowStateChange(AccessibilityEvent event)
	{
		//查看是需要拆红包、红包已经被抢完
		if (WECHAT_RECEIVER_CALSS.equals(event.getClassName()))
		{
			processInHongBaoDialog();
		}
		//拆完红包后看详细的纪录界面
		else if (WECHAT_DETAIL.equals(event.getClassName()))
		{
			performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
		}
		//在聊天界面,去点中红包
		else if (WECHAT_LAUNCHER.equals(event.getClassName()))
		{
			openHongBaoInChatPage();
		}
		//自己发红包，必抢
		else if ("com.tencent.mm.plugin.wallet.pay.ui.WalletPayUI".equals(event.getClassName()))
		{
			isSelfSend = true;
			lastSourceId = 0;
		}
	}

	private void onWindowContentChanged(AccessibilityEvent event)
	{
		ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		ComponentName cn1 = am.getRunningTasks(1).get(0).topActivity;
		if (cn1 != null && WECHAT_LAUNCHER.equals(cn1.getClassName()))
		{
			if ("android.widget.TextView".equals(event.getClassName()) && "搜索".equals(event.getContentDescription()))
			{
				lastSourceId = 0;
			} else
			{
				if (!openHongBaoInMainPage())
				{
					openHongBaoInChatPage();
				}
			}
		}
	}

	private boolean openHongBaoInMainPage()
	{
		boolean isFlag = false;
		AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
		if (nodeInfo != null)
		{
			List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText(HONGBAO_TEXT_KEY);
			List<AccessibilityNodeInfo> list1 = nodeInfo.findAccessibilityNodeInfosByText("领取红包");
			if (list.size() > 0 && list1.size() == 0)
			{
				isFlag = true;
				list.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
			}
			nodeInfo.recycle();
		}

		return isFlag;
	}

	private void openHongBaoInChatPage()
	{
		AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
		if (nodeInfo != null && nodeInfo.getChildCount() > 0)
		{
			List<AccessibilityNodeInfo> list = null;
			if (isSelfSend)
			{
				isSelfSend = false;
				list = nodeInfo.findAccessibilityNodeInfosByText("查看红包");
			} else
			{
				list = nodeInfo.findAccessibilityNodeInfosByText("领取红包");
				if (list == null || list.size() == 0)
				{
					lastSourceId = 0;
				}
			}

			//只抢最后一个
			for (int i = list.size() - 1; i >= 0; i--)
			{
				AccessibilityNodeInfo parent = list.get(i).getParent();
				if (parent != null)
				{
					long sourceId = getSourdeId(parent);
					if (parent != null && "android.widget.LinearLayout".equals(parent.getClassName()) && sourceId != lastSourceId)
					{
						lastSourceId = sourceId;
						parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);

					}
				}
				break;
			}
			nodeInfo.recycle();
		}
	}

	/*
	 * 如果还没有被拆则进行拆红包，拆完之后返回
	 * 如果已经被抢完，则直接返回
	 */
	private void processInHongBaoDialog()
	{
		AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
		if (nodeInfo == null)
		{
			Log.w(TAG, "rootWindow为空");
			return;
		}

		boolean isHave = false;
		int count = nodeInfo.getChildCount();
		for (int i = 0; i < count; i++)
		{
			AccessibilityNodeInfo childNode = nodeInfo.getChild(i);
			if ("android.widget.Button".equals(childNode.getClassName()))
			{
				isHave = true;
				childNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
			}
		}

		if (!isHave)
		{
			List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText("红包派完了");
			if (list != null && list.size() > 0)
			{
				performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
			}
		}
		nodeInfo.recycle();
	}

	private long getSourdeId(AccessibilityNodeInfo info)
	{
		long sourceId = 0;
		try
		{
			Method method = AccessibilityNodeInfo.class.getMethod("getSourceNodeId");
			method.setAccessible(true);
			Object obj = method.invoke(info);
			sourceId = (Long) obj;
		} catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
		{
			e.printStackTrace();
		}
		return sourceId;
	}
}
