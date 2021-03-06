package com.xrosscode.plugin.wechat.redenvelop;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Parcelable;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * @author johnsonlee
 */
public class RedEnvelopTerminatorService extends AccessibilityService implements TextToSpeech.OnInitListener {

    static final String TAG = RedEnvelopTerminatorService.class.getSimpleName();

    private static final String WEI_XIN_HONG_BAO = "[微信红包]";

    public static boolean available(final Context context) {
        final AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        final List<AccessibilityServiceInfo> asis = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (null == asis || asis.isEmpty()) {
            return false;
        }

        for (final AccessibilityServiceInfo asi : asis) {
            if (RedEnvelopTerminatorService.class.getName().equals(asi.getResolveInfo().serviceInfo.name)) {
                return true;
            }
        }

        return false;
    }

    public static void openAccessibilitySettings(final Activity context) {
        context.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private final Handler mHandler = new Handler();

    private Preferences mPreferences;

    private PowerManager.WakeLock mWakeLock;

    private TextToSpeech mTts;

    private boolean mFirstOpen = false;

    @Override
    public void onInit(final int status) {
        Log.v(TAG, "Initialize TTS engine: " + status);
        Toast.makeText(getApplicationContext(), "初始化TTS引擎" + (TextToSpeech.SUCCESS == status ? "成功" : "失败"), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.mPreferences = new Preferences(this);
        Toast.makeText(this, "TTS已" + (this.mPreferences.isTtsEnabled() ? "启用" : "禁用"), Toast.LENGTH_LONG).show();
        this.mTts = new TextToSpeech(this, this);
        this.initWakeLock();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.mTts.stop();
        this.mTts.shutdown();
        this.releaseWakeLock();
    }

    @Override
    public void onAccessibilityEvent(final AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED: {
                final List<CharSequence> text = event.getText();
                if (null == text || text.isEmpty()) {
                    return;
                }

                Log.v(TAG, event.toString());

                // 凡是带有 [微信红包] 字样的消息，通通打开
                if (text.toString().contains(WEI_XIN_HONG_BAO)) {
                    notifyIfNecessary(event);
                    openRedEnvelopFromNotification(event);
                }
                break;
            }
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                findRedEnvelops(event);
                break;
            }
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        final Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(getString(R.string.label_of_main_activity_red_envelop_terminator_service_already_started))
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.toast_red_envelop_terminator_service_connected))
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0))
                .setAutoCancel(false)
                .build();
        startForeground(R.string.accessibility_service_description, notification);

        Toast.makeText(this, R.string.toast_red_envelop_terminator_service_connected, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onInterrupt() {
        final Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(getString(R.string.label_of_main_activity_red_envelop_terminator_service_already_started))
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.toast_red_envelop_terminator_service_interrupted))
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0))
                .setAutoCancel(false)
                .build();
        startForeground(R.string.accessibility_service_description, notification);

        Toast.makeText(this, R.string.toast_red_envelop_terminator_service_interrupted, Toast.LENGTH_SHORT).show();
    }

    private void initWakeLock() {
        final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        this.mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, getClass().getName());
        this.mWakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (null != this.mWakeLock) {
            if (this.mWakeLock.isHeld()) {
                this.mWakeLock.release();
            }
            this.mWakeLock = null;
        }
    }

    /**
     * 打开内容中带有 <b>[微信红包]</b> 字样的消息通知
     *
     * @param event
     */
    private void openRedEnvelopFromNotification(final AccessibilityEvent event) {
        Log.v(TAG, "Opening red envelop from notification...");

        disableKeyGuardIfNecessary();

        final Parcelable data = event.getParcelableData();
        if (!(data instanceof Notification)) {
            Log.e(TAG, "Unknown notification");
            return;
        }

        this.mFirstOpen = true;
        final Notification notification = (Notification) data;

        try {
            notification.contentIntent.send();
        } catch (final PendingIntent.CanceledException e) {
            Toast.makeText(this, R.string.toast_open_notification_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Disable keyguard before open red envelop
     */
    private void disableKeyGuardIfNecessary() {
    }

    /**
     * 从界面中找红包
     *
     * @param event
     */
    private void findRedEnvelops(final AccessibilityEvent event) {
        Log.v(TAG, "Looking for red envelops...");

        final String clazz = String.valueOf(event.getClassName());
        final AccessibilityNodeInfo root = getRootInActiveWindow();

        // 抢红包界面
        if ("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI".equals(clazz)) {
            openRedEnvelop(root);
            return;
        }

        // 对话界面
        if ("com.tencent.mm.ui.LauncherUI".equals(clazz)) {
            findRedEnvelopsFromConversation(root);
            return;
        }

        // 红包详情界面
        if ("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI".equals(clazz)) {
            this.leftIfNecessary();
            return;
        }
    }

    /**
     * 从对话中找到红包并戳开
     *
     * @param root
     */
    private void findRedEnvelopsFromConversation(final AccessibilityNodeInfo root) {
        Log.v(TAG, "Looking for red envelops");

        final List<AccessibilityNodeInfo> redEnvelops = root.findAccessibilityNodeInfosByText("领取红包");
        if (null == redEnvelops || redEnvelops.isEmpty()) {
            Log.v(TAG, "No red envelop found");

            // 假红包
            if (this.mFirstOpen) {
                this.leftIfNecessary();
                this.mFirstOpen = false;
            }
            return;
        }

        Log.v(TAG, "Found " + redEnvelops.size() + " red envelops");

        // 点开最近收到的红包
        final AccessibilityNodeInfo parent = redEnvelops.get(redEnvelops.size() - 1).getParent();
        if (this.mFirstOpen) {
            Log.v(TAG, "Opening red envelop in conversation");
            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            this.mFirstOpen = false;
        }
    }

    /**
     * 真正的抢红包
     *
     * @param root
     */
    private void openRedEnvelop(final AccessibilityNodeInfo root) {
        Log.v(TAG, "Try to open red envelop...");

        final List<AccessibilityNodeInfo> nodes = findAccessibilityNodeInfoByClassName(root, "android.widget.Button");
        if (null == nodes || nodes.isEmpty()) {
            Log.e(TAG, "The red envelop has been snapped up");
            this.leftIfNecessary();
            return;
        }

        Log.v(TAG, "Opening red envelop...");
        nodes.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        this.mFirstOpen = false;
    }

    /**
     * 根据 UI 组件的类名查找辅助节点信息
     *
     * @param root
     * @param clazzName
     * @return
     */
    private List<AccessibilityNodeInfo> findAccessibilityNodeInfoByClassName(final AccessibilityNodeInfo root, final String clazzName) {
        final List<AccessibilityNodeInfo> nodes = new ArrayList<AccessibilityNodeInfo>();
        final Stack<AccessibilityNodeInfo> stack = new Stack<AccessibilityNodeInfo>();
        stack.push(root);

        while (!stack.isEmpty()) {
            final AccessibilityNodeInfo node = stack.pop();

            if (node.getClassName().equals(clazzName)) {
                nodes.add(node);
            }

            final int n = node.getChildCount();
            for (int i = 0; i < n; i++) {
                stack.push(node.getChild(i));
            }
        }

        return nodes;
    }

    private void leftIfNecessary() {
        // TODO
    }

    private void notifyIfNecessary(final AccessibilityEvent event) {
        if (this.mPreferences.isTtsEnabled() && null != this.mTts) {
            final List<CharSequence> text = event.getText();
            if (null == text || text.isEmpty()) {
                return;
            }

            final String msg = text.get(0).toString();
            final int pos = msg.indexOf(WEI_XIN_HONG_BAO);
            if (-1 == pos) {
                return;
            }

            // xxx: [微信红包]
            final String speech;
            if (pos > 1 && ' ' == msg.charAt(pos - 1) && ':' == msg.charAt(pos - 2)) {
                final String s = msg.substring(0, pos - 2) + getString(R.string.tts_send_red_envelop);
                speech = s + s + s;
            } else {
                speech = getString(R.string.tts_found_red_envelop);
            }

            this.mTts.setSpeechRate(3f);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                this.mTts.speak(speech, TextToSpeech.QUEUE_FLUSH, null, String.valueOf(System.currentTimeMillis()));
            } else {
                this.mTts.speak(speech, TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

}
