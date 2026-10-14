package com.semmyx.ledger.bankfixture;
import android.app.*;
import android.content.*;

/** CI-only second app. Never bundled into TG Ledger or included in its release APK. */
public class FixtureReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (intent.getBooleanExtra("cancel", false)) { nm.cancelAll(); return; }
        nm.createNotificationChannel(new NotificationChannel("qa_bank", "QA Bank payments", NotificationManager.IMPORTANCE_DEFAULT));
        int id = intent.getIntExtra("id", 1);
        int cents = intent.getIntExtra("cents", 12550);
        long at = intent.getLongExtra("at", System.currentTimeMillis());
        String text = "Ви витратили " + (cents / 100) + "," + String.format(java.util.Locale.ROOT, "%02d", cents % 100) + " грн у Сільпо; Залишок: 10000 грн";
        Notification.Builder b = new Notification.Builder(context, "qa_bank")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Сільпо")
            .setContentText(text).setWhen(at).setShowWhen(true);
        String style = intent.getStringExtra("style");
        if ("big".equals(style)) b.setStyle(new Notification.BigTextStyle().bigText(text));
        if ("inbox".equals(style)) b.setStyle(new Notification.InboxStyle().addLine(text)
            .addLine("Оплата 11,22 грн у АТБ; Баланс: 9000 грн"));
        if ("messaging".equals(style)) b.setStyle(new Notification.MessagingStyle("Bank")
            .addMessage(text, at, "Сільпо"));
        nm.notify("qa_" + id, id, b.build());
    }
}
