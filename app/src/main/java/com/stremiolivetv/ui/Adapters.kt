package com.stremiolivetv.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.stremiolivetv.data.ImageLoader
import com.stremiolivetv.model.Channel
import com.stremiolivetv.model.Media
import com.stremiolivetv.model.Programme
import kotlinx.coroutines.CoroutineScope
import java.text.DateFormat
import java.util.Date

class MediaAdapter(private val scope:CoroutineScope,private val items:List<Media>,private val click:(Media)->Unit,private val shelf:Boolean=false):RecyclerView.Adapter<MediaAdapter.Holder>(){
    class Holder(val root:LinearLayout,val poster:ImageView,val name:TextView,val info:TextView):RecyclerView.ViewHolder(root)
    override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder{
        val c=parent.context;val root=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(c,7),dp(c,7),dp(c,7),dp(c,14));isFocusable=true;setBackgroundColor(Color.rgb(18,21,29));if(shelf)layoutParams=RecyclerView.LayoutParams(dp(c,160),dp(c,270)).apply{marginEnd=dp(c,8)}}
        val poster=ImageView(c).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(Color.rgb(35,38,48))}
        val name=TextView(c).apply{setTextColor(Color.WHITE);textSize=15f;maxLines=2;setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(c,7),0,0)}
        val info=TextView(c).apply{setTextColor(Color.LTGRAY);textSize=12f}
        root.addView(poster,LinearLayout.LayoutParams(-1,if(shelf)dp(c,205) else dp(c,210)));root.addView(name);root.addView(info)
        root.setOnFocusChangeListener{v,focused->v.scaleX=if(focused)1.05f else 1f;v.scaleY=if(focused)1.05f else 1f;v.setBackgroundColor(if(focused)Color.rgb(87,61,214) else Color.rgb(18,21,29))}
        return Holder(root,poster,name,info)
    }
    override fun onBindViewHolder(h:Holder,p:Int){val m=items[p];h.name.text=m.name;h.info.text=listOfNotNull(m.year,m.rating?.let{"★ "+it}).joinToString("  ");ImageLoader.load(scope,h.poster,m.poster);h.root.setOnClickListener{click(m)}}
    override fun getItemCount()=items.size
}

class ChannelAdapter(private val items:List<Channel>,private val guide:List<Programme>,private val play:(Channel)->Unit,private val favorite:(Channel)->Unit,private val selected:(Channel)->Unit={}):RecyclerView.Adapter<ChannelAdapter.Holder>(){
    class Holder(val root:LinearLayout,val name:TextView,val now:TextView):RecyclerView.ViewHolder(root)
    override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder{
        val c=parent.context;val root=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(c,18),dp(c,12),dp(c,18),dp(c,12));isFocusable=true;setBackgroundColor(Color.rgb(16,19,26))}
        val name=TextView(c).apply{setTextColor(Color.WHITE);textSize=18f;setTypeface(typeface,Typeface.BOLD)}
        val now=TextView(c).apply{setTextColor(Color.LTGRAY);textSize=14f}
        root.addView(name);root.addView(now)
        return Holder(root,name,now)
    }
    override fun onBindViewHolder(h:Holder,p:Int){val c=items[p];val time=System.currentTimeMillis();val current=guide.firstOrNull{it.channelId==c.tvgId&&time in it.startMillis until it.stopMillis};h.name.text=c.name;h.now.text=current?.title?:c.group;h.root.setOnFocusChangeListener{v,f->v.setBackgroundColor(if(f)Color.rgb(87,61,214) else Color.rgb(16,19,26));if(f)selected(c)};h.root.setOnClickListener{play(c)};h.root.setOnLongClickListener{favorite(c);true}}
    override fun getItemCount()=items.size
}

class GuideAdapter(private val rows:List<Pair<Channel,List<Programme>>>,private val play:(Channel)->Unit):RecyclerView.Adapter<GuideAdapter.Holder>(){
    class Holder(val root:LinearLayout,val channel:TextView,val shows:LinearLayout):RecyclerView.ViewHolder(root)
    override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder{
        val c=parent.context;val root=LinearLayout(c).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(c,12),dp(c,6),dp(c,12),dp(c,6));isFocusable=true}
        val channel=TextView(c).apply{setTextColor(Color.WHITE);textSize=16f;setTypeface(typeface,Typeface.BOLD)}
        val shows=LinearLayout(c).apply{orientation=LinearLayout.HORIZONTAL}
        root.addView(channel,LinearLayout.LayoutParams(dp(c,230),dp(c,72)));root.addView(shows,LinearLayout.LayoutParams(0,dp(c,72),1f))
        root.setOnFocusChangeListener{v,f->v.setBackgroundColor(if(f)Color.rgb(87,61,214) else Color.TRANSPARENT)}
        return Holder(root,channel,shows)
    }
    override fun onBindViewHolder(h:Holder,p:Int){val (channel,programs)=rows[p];h.channel.text=channel.name;h.shows.removeAllViews();if(programs.isEmpty())h.shows.addView(show(h.root,"No guide data"))else programs.forEach{h.shows.addView(show(h.root,DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.startMillis))+"  "+it.title),LinearLayout.LayoutParams(0,-1,1f).apply{marginEnd=dp(h.root.context,6)})};h.root.setOnClickListener{play(channel)}}
    private fun show(parent:View,textValue:String)=TextView(parent.context).apply{text=textValue;setTextColor(Color.WHITE);textSize=14f;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(context,12),0,dp(context,12),0);setBackgroundColor(Color.rgb(31,35,46));maxLines=2}
    override fun getItemCount()=rows.size
}

private fun dp(c:android.content.Context,v:Int)=(v*c.resources.displayMetrics.density).toInt()
