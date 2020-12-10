package com.deepquotes;

import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.RemoteViews;

import com.deepquotes.services.UpdateService;

import static android.content.Context.MODE_PRIVATE;
import static android.util.TypedValue.COMPLEX_UNIT_SP;

public class QuotesWidgetProvider extends AppWidgetProvider {

    private SharedPreferences historyQuotesSP ;
    private SharedPreferences appConfigSP;




    //窗口小部件更新时时调用
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);

//        Log.d("QuotesWidgetProvider", "onUpdate: "+context);


        if (historyQuotesSP==null)
            historyQuotesSP = context.getSharedPreferences("historyQuotes",MODE_PRIVATE);
        if (appConfigSP==null)
            appConfigSP = context.getSharedPreferences("appConfig",MODE_PRIVATE);



        final int N = appWidgetIds.length;
        for (int i=0; i<N; i++){
            int appWidgetId = appWidgetIds[i];

            RemoteViews views = new RemoteViews(context.getPackageName(),R.layout.quotes_layout);
            views.setTextColor(R.id.quotes_textview,Color.WHITE);
            int current = historyQuotesSP.getInt("currentQuote",0);
            String quote = historyQuotesSP.getString(String.valueOf(current),"欲买桂花同载酒，终不似，少年游");
            views.setTextViewText(R.id.quotes_textview,quote);
            views.setTextViewTextSize(R.id.quotes_textview, COMPLEX_UNIT_SP, appConfigSP.getInt("字体大小:", 20));
            Intent openIntent = new Intent(context, ScrollingActivity.class);
//            openIntent.putExtra("quote",quote);
            PendingIntent openPendingIntent = PendingIntent.getActivity(context, 0, openIntent, 0);
            views.setOnClickPendingIntent(R.id.quotes_textview, openPendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId,views);
        }


    }

    //窗口小部件被删除时调用
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
//        Toast.makeText(context,"你删除了控件",Toast.LENGTH_SHORT).show();
    }


    //该窗口小部件第一次添加到桌面时调用该方法
    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);

        appConfigSP = context.getSharedPreferences("appConfig",MODE_PRIVATE);
        int duration = appConfigSP.getInt("当前刷新间隔(分钟):",15);

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        ComponentName componentName = new ComponentName(context, UpdateService.class);
        JobInfo jobInfo = new JobInfo.Builder(12345,componentName)
                .setPeriodic(duration*60*1000)
                .setPersisted(true)
                .build();
        jobScheduler.schedule(jobInfo);




//        Toast.makeText(context,"你添加了了控件",Toast.LENGTH_SHORT).show();
//        Log.i("TAG", "onEnabled: ");
//        Intent startTimerIntent = new Intent(context, TimerService.class);
////        startTimerIntent.putExtra("refreshTime",sharedPreferences.getInt("当前刷新间隔(分钟):",10));
//        context.startService(startTimerIntent);
    }

    //最后一个该窗口小部件删除时调用该方法
    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
//        Toast.makeText(context,"你删除了最后一个控件",Toast.LENGTH_SHORT).show();

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(12345);
//        context.stopService(new Intent(context, TimerService.class));

//        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//        manager.cancel(createPendingIntent(context));
    }


    //当小部件大小改变时调用
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
//        Toast.makeText(context,"你改变了控件大小",Toast.LENGTH_SHORT).show();
    }


    //当小部件从备份恢复时调用该方法
    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        super.onRestored(context, oldWidgetIds, newWidgetIds);
//        Toast.makeText(context,"你恢复控件",Toast.LENGTH_SHORT).show();
    }




}
