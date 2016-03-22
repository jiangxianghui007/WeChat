package com.example.lightman.redenvelope;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import java.util.List;
import android.media.AudioManager;
import android.media.SoundPool;

public class EnvelopeService extends AccessibilityService {
    static final String TAG = "zhangwang";
    static final String WECHAT_PACKAGENAME = "com.tencent.mm";
    static final String ENVELOPE_TEXT_KEY = "[微信红包]";

    private int WECHAT_PROCESS = 1;
    private int HOME_CLICK_ACTION = 2;
    private int OPEN_NOTIFICATION = 3;
    private int TURN_SCREEN_OFF = 4;
    private int REAL_CLICK_LUCKY_MONEY = 5;
    private int CHECK_WINDOW_CHANGED_SELF = 6;

    private KeyguardManager km;
    private KeyguardManager.KeyguardLock kl;
    private PowerManager pm;
    private PowerManager.WakeLock wl;
    private ActivityManager am;
    List<ActivityManager.RunningAppProcessInfo> mProcesses;
    private SoundPool mSp;
    private AudioManager mAM;
    private int mSoundId;

    static boolean checkWindowState = false;
    boolean mNotificationComing = false;
    boolean mGotPowerLock = false;
    Notification globalNotification;
    AccessibilityNodeInfo globalNode;
    private int clickLuckyMoneyTimes = 0;
    private boolean mRefuseWindowCheckedAfterNotification = false;
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String mAction = intent.getAction();
            if (mAction.equals(Intent.ACTION_SCREEN_OFF)) {
                turnOffScreen();
            }
        }
    };

    public Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == WECHAT_PROCESS) {
                // makeSureWeChatProcessAlive();
                // sendEmptyMessageDelayed(WECHAT_PROCESS, 500000);
            } else if (msg.what == HOME_CLICK_ACTION) {
                performGlobalAction(GLOBAL_ACTION_HOME);
            } else if (msg.what == OPEN_NOTIFICATION) {
                realOpenNotification(globalNotification);
            } else if (msg.what == TURN_SCREEN_OFF) {
                turnOffScreen();
            } else if (msg.what == REAL_CLICK_LUCKY_MONEY) {
                // realClickLuckyMoney();
                clickLuckyMoneyDialog();
            } else if (msg.what == CHECK_WINDOW_CHANGED_SELF) {
                if (!mRefuseWindowCheckedAfterNotification) {
                    Log.e(TAG, TAG + "Window Changed Event doesn't come , so check now");
                    clickLuckyMoneyDialog();
                }
            }
        }
    };

    public EnvelopeService() {
    }

    public void makeSureWeChatProcessAlive() {
        boolean findProcess = false;
        mProcesses = am.getRunningAppProcesses();
        if (mProcesses.size() > 0) {
            for (int i = 0; i < mProcesses.size(); i++) {
                if (mProcesses.get(i).processName.equals("com.tencent.mm")) {
                    findProcess = true;
                    break;
                }
            }
            if (findProcess) {
                return;
            } else {
                makeSureScreenIsOn();
                Intent mIntent = new Intent();
                mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mIntent.setComponent(new ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI"));
                startActivity(mIntent);
                handler.sendEmptyMessageDelayed(HOME_CLICK_ACTION, 15000);
            }

        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        final int eventType = event.getEventType();
        // Log.e(TAG, "zhangwang 事件---->" + event);
        // 通知栏事件
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            List<CharSequence> texts = event.getText();
            if (!texts.isEmpty()) {
                for (CharSequence t : texts) {
                    String text = String.valueOf(t);
                    if (text.contains(ENVELOPE_TEXT_KEY)) {
                        mNotificationComing = true;
                        openNotification(event);
                        break;
                    }
                }
            }
        } else if (checkWindowState && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.e(TAG, TAG + "WINDOW STATE CHANGED");
            openEnvelope(event);
        }
    }

    @Override
    public void onInterrupt() {
        Toast.makeText(this, "中断抢红包服务", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        mSp = new SoundPool(6, AudioManager.STREAM_NOTIFICATION, 0);
        mAM = (AudioManager) getSystemService(this.AUDIO_SERVICE);
        Toast.makeText(this, "连接抢红包服务", Toast.LENGTH_SHORT).show();
        handler.sendEmptyMessageDelayed(WECHAT_PROCESS, 20000);
        IntentFilter mFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mBroadcastReceiver, mFilter);
        mSoundId = mSp.load(this, R.raw.tada, 1);
    }

    public void playNotificationSound() {
        float mMax = mAM.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
        float mCurrent = mAM.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        final float ratio = mMax / mMax;
        mSp.play(mSoundId, ratio, ratio, 1, 0, 1);
    }

    private void sendNotificationEvent() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (!manager.isEnabled()) {
            return;
        }
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        event.setPackageName(WECHAT_PACKAGENAME);
        event.setClassName(Notification.class.getName());
        CharSequence tickerText = ENVELOPE_TEXT_KEY;
        event.getText().add(tickerText);
        manager.sendAccessibilityEvent(event);
    }

    private boolean makeSureScreenIsOn() {
        if (pm.isScreenOn()) {
            if (mGotPowerLock && handler.hasMessages(TURN_SCREEN_OFF)) {
                handler.removeMessages(TURN_SCREEN_OFF);
                handler.sendEmptyMessageDelayed(TURN_SCREEN_OFF, 18000);
            }
            return true;
        } else {
            if (kl == null)
                kl = km.newKeyguardLock("unLock");
            if (wl == null)
                wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");
            if (!mGotPowerLock) {
                mGotPowerLock = true;
                wl.acquire();
                kl.disableKeyguard();
                handler.sendEmptyMessageDelayed(TURN_SCREEN_OFF, 18000);
            }
            return false;
        }
    }

    private void turnOffScreen() {
        if (mGotPowerLock && !mNotificationComing) {
            mGotPowerLock = false;
            if (kl != null)
                kl.reenableKeyguard();
            if (wl != null)
                wl.release();
        }
    }

    private void openNotification(AccessibilityEvent event) {
        if (event.getParcelableData() == null || !(event.getParcelableData() instanceof Notification)) {
            return;
        }
        Notification notification = (Notification) event.getParcelableData();
        globalNotification = notification.clone();

        if (makeSureScreenIsOn()) {
            realOpenNotification(globalNotification);
        } else {
            handler.sendEmptyMessageDelayed(OPEN_NOTIFICATION, 1000);
        }
    }

    private void realOpenNotification(Notification notification) {
        PendingIntent pendingIntent = notification.contentIntent;
        try {
            Log.e(TAG, "zhangwang click notification");
            checkWindowState = true;
            pendingIntent.send();
            handler.sendEmptyMessageDelayed(CHECK_WINDOW_CHANGED_SELF, 2000);
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }

    private void openEnvelope(AccessibilityEvent event) {
        if ("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI".equals(event.getClassName())) {
            // 点中了红包，下一步就是去拆红包
            gotLuckyMoney();
            checkWindowState = false;
            mNotificationComing = false;
            mRefuseWindowCheckedAfterNotification = false;
        } else if ("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI".equals(event.getClassName())) {
            // 拆完红包后看详细的纪录界面
            checkWindowState = false;
            mNotificationComing = false;
            mRefuseWindowCheckedAfterNotification = false;
        } else if ("com.tencent.mm.ui.LauncherUI".equals(event.getClassName())) {
            // 在聊天界面,去点中红包
            if (!mRefuseWindowCheckedAfterNotification)
                clickLuckyMoneyDialog();
        }
    }

    private void gotLuckyMoney() {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo == null) {
            Log.w(TAG, "rootWindow为空");
            return;
        }
        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText("拆红包");
        for (AccessibilityNodeInfo n : list) {
            boolean result = n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.e(TAG, TAG + "----> tear lucky money :" + result);
        }
    }

    private void clickLuckyMoneyDialog() {
        boolean result = false;
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        // Log.e(TAG, TAG + "node info :" + nodeInfo);
        if (nodeInfo == null) {
            Log.w(TAG, "rootWindow为空");
            return;
        }

        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText("领取红包");

        if (!list.isEmpty() && "com.tencent.mm".equals(nodeInfo.getPackageName()))
            mRefuseWindowCheckedAfterNotification = true;

        // 最新的红包领起
        // Log.e(TAG, TAG + "list size :" + list.size());
        for (int i = list.size() - 1; i >= 0; i--) {
            AccessibilityNodeInfo parent = list.get(i).getParent();
            if (parent != null) {
                globalNode = parent;
                realClickLuckyMoney();
                break;
            }
        }
    }

    void realClickLuckyMoney() {
        boolean result = false;
        result = globalNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        Log.e(TAG, TAG + "-->got lucky money:" + result + ", clickLuckyMoneyTimes :" + clickLuckyMoneyTimes);
        if (!result) {
            clickLuckyMoneyTimes++;
            if (clickLuckyMoneyTimes < 5) {
                handler.sendEmptyMessageDelayed(REAL_CLICK_LUCKY_MONEY, 1000);
            } else {
                clickLuckyMoneyTimes = 0;
                playNotificationSound();
            }
        } else
            clickLuckyMoneyTimes = 0;
    }
}
