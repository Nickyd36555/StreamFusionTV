package com.stremiolivetv.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import com.stremiolivetv.data.ImageLoader
import com.stremiolivetv.model.Channel
import com.stremiolivetv.model.Programme
import kotlinx.coroutines.CoroutineScope
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class TiviGuideLayout(
    context:Context,
    private val scope:CoroutineScope,
    private val channels:List<Channel>,
    private val programmes:List<Programme>,
    private val play:(Channel)->Unit
):LinearLayout(context){
    private val preview=ImageView(context)
    private val programTitle=TextView(context)
    private val programTime=TextView(context)
    private val programDescription=TextView(context)
    private val channelGroup=TextView(context)
    private val slotWidth=210
    private val rowHeight=58
    private val channelWidth=280
    private val startTime=(System.currentTimeMillis()/1_800_000L)*1_800_000L
    private val guideEnd=startTime+9*1_800_000L
    private val programmesByChannel=programmes.asSequence()
        .filter{it.stopMillis>startTime&&it.startMillis<guideEnd}
        .groupBy{it.channelId}
        .mapValues{(_,items)->items.sortedBy{it.startMillis}}

    init{
        orientation=VERTICAL
        setBackgroundColor(Color.rgb(11,10,13))
        addView(topPanel(),LayoutParams(-1,dp(176)))
        val header=LinearLayout(context).apply{orientation=HORIZONTAL;setBackgroundColor(Color.rgb(42,28,36))}
        header.addView(TextView(context).apply{text=SimpleDateFormat("EEE, MMM d, HH:mm",Locale.getDefault()).format(Date());setTextColor(Color.rgb(145,205,245));textSize=18f;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(18),0,0,0);setTypeface(typeface,Typeface.BOLD)},LayoutParams(dp(channelWidth),dp(48)))
        val timeScroll=HorizontalScrollView(context).apply{isHorizontalScrollBarEnabled=false;addView(timeRuler())}
        header.addView(timeScroll,LayoutParams(0,dp(48),1f));addView(header)
        val lower=LinearLayout(context).apply{orientation=HORIZONTAL}
        val channelScroll=ScrollView(context).apply{isVerticalScrollBarEnabled=false;addView(channelColumn())}
        val verticalGrid=ScrollView(context).apply{isVerticalScrollBarEnabled=false}
        val gridHorizontal=HorizontalScrollView(context).apply{isHorizontalScrollBarEnabled=false;addView(verticalGrid)}
        verticalGrid.addView(programGrid())
        lower.addView(channelScroll,LayoutParams(dp(channelWidth),-1));lower.addView(gridHorizontal,LayoutParams(0,-1,1f));addView(lower,LayoutParams(-1,0,1f))
        if(android.os.Build.VERSION.SDK_INT>=23){
            timeScroll.setOnScrollChangeListener{_,x,_,_,_->if(gridHorizontal.scrollX!=x)gridHorizontal.scrollTo(x,0)}
            gridHorizontal.setOnScrollChangeListener{_,x,_,_,_->if(timeScroll.scrollX!=x)timeScroll.scrollTo(x,0)}
            channelScroll.setOnScrollChangeListener{_,_,y,_,_->if(verticalGrid.scrollY!=y)verticalGrid.scrollTo(0,y)}
            verticalGrid.setOnScrollChangeListener{_,_,y,_,_->if(channelScroll.scrollY!=y)channelScroll.scrollTo(0,y)}
        }
    }

    private fun topPanel():View{
        val root=LinearLayout(context).apply{orientation=HORIZONTAL;setPadding(dp(18),dp(12),dp(18),dp(10));setBackgroundColor(Color.rgb(11,10,13))}
        preview.scaleType=ImageView.ScaleType.FIT_CENTER;preview.setPadding(dp(12),dp(12),dp(12),dp(12));preview.setBackgroundColor(Color.rgb(20,16,20));root.addView(preview,LayoutParams(dp(280),-1))
        val details=LinearLayout(context).apply{orientation=VERTICAL;setPadding(dp(24),dp(12),dp(12),0)}
        programTitle.apply{setTextColor(Color.WHITE);textSize=27f;setTypeface(typeface,Typeface.BOLD);text="Choose a program"}
        programTime.apply{setTextColor(Color.rgb(155,162,168));textSize=17f;setPadding(0,dp(9),0,dp(8))}
        programDescription.apply{setTextColor(Color.rgb(170,176,180));textSize=17f;maxLines=3}
        channelGroup.apply{setTextColor(Color.WHITE);textSize=17f;gravity=Gravity.END;setTypeface(typeface,Typeface.BOLD)}
        details.addView(programTitle);details.addView(programTime);details.addView(programDescription,LayoutParams(-1,0,1f));details.addView(channelGroup)
        root.addView(details,LayoutParams(0,-1,1f));return root
    }

    private fun timeRuler():View{
        val row=LinearLayout(context).apply{orientation=HORIZONTAL}
        for(i in 0..8)row.addView(TextView(context).apply{text=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(startTime+i*1_800_000L));setTextColor(Color.LTGRAY);textSize=18f;gravity=Gravity.CENTER},LayoutParams(dp(slotWidth),dp(48)))
        return row
    }

    private fun channelColumn():View{
        val column=LinearLayout(context).apply{orientation=VERTICAL;setBackgroundColor(Color.rgb(26,17,22))}
        channels.forEachIndexed{index,channel->column.addView(TextView(context).apply{text=(index+1).toString()+"    "+channel.name;setTextColor(Color.WHITE);textSize=15f;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),0,dp(8),0);setTypeface(typeface,Typeface.BOLD);setBackgroundColor(Color.rgb(26,17,22))},LayoutParams(-1,dp(rowHeight)))}
        return column
    }

    private fun programGrid():View{
        val column=LinearLayout(context).apply{orientation=VERTICAL}
        channels.forEach{channel->
            val row=LinearLayout(context).apply{orientation=HORIZONTAL;setBackgroundColor(Color.rgb(11,10,13))}
            val items=programmesByChannel[channel.tvgId].orEmpty()
            var cursor=startTime
            items.forEach{item->
                if(item.startMillis>cursor)row.addView(space(item.startMillis-cursor))
                val width=max(dp(120),((item.stopMillis-max(item.startMillis,startTime)).toDouble()/1_800_000.0*dp(slotWidth)).toInt())
                row.addView(programButton(channel,item),LayoutParams(width,dp(rowHeight)));cursor=max(cursor,item.stopMillis)
            }
            if(items.isEmpty())row.addView(TextView(context).apply{text="No guide information";setTextColor(Color.GRAY);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),0,0,0)},LayoutParams(dp(slotWidth*9),dp(rowHeight)))
            column.addView(row,LayoutParams(-2,dp(rowHeight)))
        }
        return column
    }

    private fun programButton(channel:Channel,item:Programme)=Button(context).apply{
        text=item.title;isAllCaps=false;gravity=Gravity.START or Gravity.CENTER_VERTICAL;setTextColor(Color.LTGRAY);textSize=14f;setPadding(dp(12),0,dp(8),0);setBackgroundColor(Color.rgb(27,22,27));isFocusable=true
        setOnFocusChangeListener{v,focused->v.setBackgroundColor(if(focused)Color.rgb(201,162,74) else Color.rgb(27,22,27));setTextColor(if(focused)Color.rgb(30,20,10) else Color.LTGRAY);if(focused)showDetails(channel,item)}
        setOnClickListener{play(channel)}
    }

    private fun showDetails(channel:Channel,item:Programme){
        programTitle.text=item.title
        programTime.text=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(item.startMillis))+" – "+SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(item.stopMillis))
        programDescription.text=item.description?:""
        channelGroup.text=channel.group
        ImageLoader.load(scope,preview,channel.logo)
    }
    private fun space(duration:Long)=Space(context).apply{layoutParams=LayoutParams(((duration.toDouble()/1_800_000.0)*dp(slotWidth)).toInt(),dp(rowHeight))}
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
