package com.deepquotes.services

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.util.TypedValue
import android.widget.RemoteViews
import com.deepquotes.Quotes
import com.deepquotes.QuotesWidgetProvider
import com.deepquotes.R
import com.deepquotes.utils.Constant
import com.deepquotes.utils.DBManager
import com.deepquotes.utils.QuotesSQLHelper
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.util.*

class TimerService : Service() {
    /**
     * activity存活时的刷新服务，
     * 通过RemoteView刷新Widgets
     * 通过Broadcast通知Activity刷新主界面
     */
    private lateinit var appConfigSP: SharedPreferences
    private lateinit var mQuotesSQLHelper: QuotesSQLHelper
    private lateinit var mDatabase: SQLiteDatabase
    override fun onBind(intent: Intent): IBinder? {
        // TODO: Return the communication channel to the service.
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()
        mQuotesSQLHelper = DBManager.getInstance(this) as QuotesSQLHelper
        mDatabase = mQuotesSQLHelper.getReadableDatabase()
        appConfigSP = getSharedPreferences("appConfig", Context.MODE_PRIVATE)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
//        Log.d("6666", String.valueOf(appConfigSP.getInt("当前刷新间隔(分钟):", 15)));
        if (appConfigSP.getBoolean("isEnableHitokoto", false)) {   //启用一言
            if (appConfigSP.getBoolean("随机", false)) //随机一言
                getDeepQuotes(Random().nextInt(4), "") else {
                val stringBuffer = StringBuffer("?")
                if (appConfigSP.getBoolean("动画、漫画", false)) stringBuffer.append("c=a&c=b")
                if (appConfigSP.getBoolean("游戏", false)) stringBuffer.append("&c=c")
                if (appConfigSP.getBoolean("文学", false)) stringBuffer.append("&c=d")
                if (appConfigSP.getBoolean("影视", false)) stringBuffer.append("&c=h")
                if (appConfigSP.getBoolean("诗词", false)) stringBuffer.append("&c=i")
                if (appConfigSP.getBoolean("网易云", false)) stringBuffer.append("&c=j")
                //                Log.d("TAG","stringBuffer "+stringBuffer);
                getDeepQuotes(Random().nextInt(4), stringBuffer.toString())
            }
        } else {     //关闭一言
            getDeepQuotes(Random().nextInt(3), "")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    var handler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                Quotes.UPDATE_TEXT -> if (msg.obj != null) {
                    val quotes = msg.obj as Quotes
                    val text = quotes.text
                    val author = quotes.author
                    //向数据库插入数据
                    insertDatabase(text, author)

                    //发送更新成功广播,通知Activity刷新界面（若Activity存活）
                    val updatetext = Intent("com.deepquotes.broadcast.updateTextView")
                    updatetext.putExtra("quote", text)
                    //                        Log.d("广播","already send broadcast");
                    sendBroadcast(updatetext)

                    //更新widget
                    val remoteViews = RemoteViews(applicationContext.packageName, R.layout.quotes_layout)
                    remoteViews.setTextViewText(R.id.quotes_textview, text)
                    remoteViews.setTextColor(R.id.quotes_textview, appConfigSP!!.getInt("fontColor", Color.WHITE))
                    remoteViews.setTextViewTextSize(R.id.quotes_textview, TypedValue.COMPLEX_UNIT_SP, appConfigSP!!.getInt("字体大小:", 20).toFloat())
                    val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                    val componentName = ComponentName(applicationContext, QuotesWidgetProvider::class.java)
                    appWidgetManager.updateAppWidget(componentName, remoteViews)
                }
                else -> {
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mDatabase.close()
    }

    private fun insertDatabase(text: String, author: String) {
        val insertSQL = "insert into " +
                Constant.TABLE_NAME + "(" + Constant._TEXT + "," + Constant._AUTHOR + ")" +
                " values('" + text + "','" + author + "')"
        mDatabase.execSQL(insertSQL)
    }

    private fun getDeepQuotes(seed: Int, postParam: String) {
        when (seed) {
            0 -> deepQuote
            1 -> getDeepQuote2(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    try {
                        var responseStr = response.body!!.string()
                        val responseJSON = JSONObject(responseStr)
                        responseStr = if (responseJSON.getInt("code") == 429) "每日调用次数已达上限" else {
                            val quoteData = responseJSON.getJSONObject("data")
                            quoteData.getString("title")
                        }

//                            Log.d("DeepQuote2",responseStr);
                        val message = handler.obtainMessage()
                        message.what = Quotes.UPDATE_TEXT
                        message.obj = Quotes(responseStr, UNKOWN_AUTHOR)
                        handler.sendMessage(message)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
            2 -> getDeepQuote3(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    try {
                        var responseStr = response.body!!.string()
                        val responseJSON = JSONObject(responseStr)
                        responseStr = responseJSON.getString("text")

//                            Log.d("DeepQuote3",responseStr);
                        val message = handler.obtainMessage()
                        message.what = Quotes.UPDATE_TEXT
                        message.obj = Quotes(responseStr, UNKOWN_AUTHOR)
                        handler.sendMessage(message)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
            else -> getHitokotoQuote(postParam, object : Callback {
                override fun onFailure(call: Call, e: IOException) {}

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    try {
                        var responseStr = response.body!!.string()
                        val responseJSON = JSONObject(responseStr)
                        responseStr = responseJSON.getString("hitokoto")
                        val author = responseJSON.getString("from")
                        //                            Log.d("hikotoko",responseStr);
                        val message = handler.obtainMessage()
                        message.what = Quotes.UPDATE_TEXT
                        message.obj = Quotes(responseStr, author)
                        handler.sendMessage(message)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            })
        }
    }

    //                    Log.d("DeepQuote1", data);
    private val deepQuote: Unit
        private get() {
            Thread(Runnable {
                var data: String? = null
                try {
                    val document = Jsoup.connect("http://www.nows.fun/").get()
                    val element = document.select("div.main-wrapper")
                    for (i in element.indices) {
                        data = element[i].select("span").text()
                    }
                    val message = handler.obtainMessage()
                    message.what = Quotes.UPDATE_TEXT
                    message.obj = Quotes(data!!, UNKOWN_AUTHOR)
                    handler.sendMessage(message)

//                    Log.d("DeepQuote1", data);
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: NullPointerException) {
                    e.printStackTrace()
                }
            }).start()
        }

    private fun getDeepQuote2(callback: Callback) {
        val client = OkHttpClient()
        val request = Request.Builder().url("https://v1.alapi.cn/api/soul").build()
        client.newCall(request).enqueue(callback)
    }

    private fun getDeepQuote3(callback: Callback) {
        val client = OkHttpClient()
        val request = Request.Builder().url("https://api.yum6.cn/djt/index.php?encode=json").build()
        client.newCall(request).enqueue(callback)
    }

    private fun getHitokotoQuote(postParam: String, callback: Callback) {
        val client = OkHttpClient()
        val request = Request.Builder().url("https://v1.hitokoto.cn/$postParam").build()
        client.newCall(request).enqueue(callback)
    }

    companion object {
        private const val UNKOWN_AUTHOR = "来源于网络"
    }
}