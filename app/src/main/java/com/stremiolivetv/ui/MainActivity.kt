package com.stremiolivetv.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stremiolivetv.data.*
import com.stremiolivetv.model.*
import com.stremiolivetv.player.PlayerActivity
import com.stremiolivetv.update.UpdateChecker
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.URL

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs by lazy { getSharedPreferences("stream_fusion", MODE_PRIVATE) }
    private lateinit var body: FrameLayout
    private lateinit var status: TextView
    private lateinit var nav: LinearLayout
    private var activeNav: Button? = null
    private var channels = emptyList<Channel>()
    private var programmes = emptyList<Programme>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(shell())
        home()
        restoreSources()
        if (prefs.getString("xtream_server", "").isNullOrBlank()) xtreamDialog()
        UpdateChecker(this).checkOnLaunch()
    }

    private fun shell(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(BG) }
        nav = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL; setPadding(dp(8),dp(18),dp(8),dp(12)); setBackgroundColor(SURFACE) }
        nav.addView(TextView(this).apply { text="SF"; textSize=18f; gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD); setTextColor(ACCENT); background=rounded(Color.rgb(34,27,20),10); setPadding(0,0,0,0) },LinearLayout.LayoutParams(dp(48),dp(48)).apply{bottomMargin=dp(18)})
        listOf(
            Triple("⌂","Home",::home), Triple("▣","Movies",{ catalog("movie","Movies") }),
            Triple("▤","Series",{ catalog("series","Series") }), Triple("▦","Guide",::guide),
            Triple("▶","Channels",::liveTv), Triple("⌕","Search",::search),
            Triple("★","Library",::library), Triple("⚙","Settings",::settings)
        ).forEach { (icon,name,action) ->
            nav.addView(Button(this).apply {
                text=icon; contentDescription=name; textSize=22f; isAllCaps=false; gravity=Gravity.CENTER
                setTextColor(MUTED); background=rounded(Color.TRANSPARENT,8); isFocusable=true; setPadding(0,0,0,0)
                setOnFocusChangeListener{v,f-> styleNav(v as Button,f || v===activeNav) }
                setOnClickListener { activeNav?.let{styleNav(it,false)};activeNav=this;styleNav(this,true);status.text=name;action() }
            },LinearLayout.LayoutParams(dp(52),dp(52)).apply{bottomMargin=dp(8)})
        }
        status = TextView(this).apply { text="STREAM FUSION  •  READY"; textSize=12f; letterSpacing=.08f; setTextColor(MUTED); setPadding(dp(28),dp(9),0,dp(7)); setBackgroundColor(BG) }
        body = FrameLayout(this)
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; addView(status,LinearLayout.LayoutParams(-1,dp(34))); addView(body,LinearLayout.LayoutParams(-1,0,1f)) }
        root.addView(nav, LinearLayout.LayoutParams(dp(76),-1))
        root.addView(content, LinearLayout.LayoutParams(0,-1,1f))
        return root
    }

    private fun home() {
        val shelves=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(28),dp(18),dp(18),dp(36));background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(32,20,16),BG,BG))}
        val scroll=ScrollView(this).apply{addView(shelves)};swap(scroll,"Home")
        val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.BOTTOM;setPadding(dp(30),dp(24),dp(30),dp(24));background=rounded(Color.rgb(27,22,25),14)}
        hero.addView(TextView(this).apply{text="STREAM FUSION";textSize=12f;letterSpacing=.16f;setTextColor(ACCENT);setTypeface(typeface,Typeface.BOLD)})
        hero.addView(title("Everything you watch. One screen.",30f).apply{setPadding(0,dp(8),0,dp(4))})
        hero.addView(copy("Stremio movies and series • Xtream Codes live television"))
        shelves.addView(hero,LinearLayout.LayoutParams(-1,dp(170)).apply{bottomMargin=dp(22)})
        fun addShelf(name:String,items:List<Media>){if(items.isEmpty())return;shelves.addView(title(name,19f).apply{setPadding(dp(4),dp(10),0,dp(10))});shelves.addView(RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@MainActivity,LinearLayoutManager.HORIZONTAL,false);adapter=MediaAdapter(scope,items,::openMedia,true)},LinearLayout.LayoutParams(-1,dp(286)))}
        scope.launch{
            val result=runCatching{coroutineScope{val movies=async(Dispatchers.IO){StremioClient.catalog("movie")};val series=async(Dispatchers.IO){StremioClient.catalog("series")};movies.await() to series.await()}}
            result.onSuccess{(movies,series)->addShelf("Popular — Movies",movies.take(20));addShelf("Popular — Series",series.take(20));addShelf("Featured — Movies",movies.drop(10).take(20));status.text="Stremio catalogs"}.onFailure{status.text="Catalog error: "+it.message}
        }
    }

    private fun catalog(type:String, heading:String, query:String?=null) {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(26),dp(18),dp(26),0); background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(38,24,66),Color.rgb(11,13,18),Color.rgb(9,11,16))) }
        root.addView(title(heading.uppercase(),29f))
        root.addView(copy(if(type=="movie")"Discover movies from Stremio-compatible catalogs" else "Browse Stremio-compatible series and episodes"))
        val loading=ProgressBar(this); root.addView(loading,LinearLayout.LayoutParams(-1,dp(64)))
        val grid=RecyclerView(this).apply { layoutManager=GridLayoutManager(this@MainActivity,6) }
        root.addView(grid,LinearLayout.LayoutParams(-1,0,1f)); swap(root,heading)
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { StremioClient.catalog(type,query) } }
                .onSuccess { loading.visibility=View.GONE; grid.adapter=MediaAdapter(scope,it,::openMedia); status.text=it.size.toString()+" titles" }
                .onFailure { loading.visibility=View.GONE; status.text="Catalog error: "+it.message }
        }
    }

    private fun openMedia(media:Media) {
        val details=buildString { append(media.name); media.year?.let{append("  •  "+it)}; media.rating?.let{append("  •  ★ "+it)}; media.description?.let{append("\n\n"+it)} }
        AlertDialog.Builder(this).setTitle(media.name).setMessage(details).setNegativeButton("Close",null)
            .setNeutralButton(if(isFavorite(media.id)) "Remove from Library" else "Add to Library"){_,_->toggleFavorite(media)}
            .setPositiveButton("Find Streams"){_,_->findStreams(media)}.show()
    }

    private fun findStreams(media:Media) {
        if (media.id.startsWith("xtream:")) {
            val client = xtreamClient() ?: return
            status.text="Loading Xtream stream…"
            scope.launch {
                val streams=runCatching{withContext(Dispatchers.IO){client.streams(media)}}.getOrDefault(emptyList())
                if(streams.isEmpty()){status.text="No Xtream streams returned";return@launch}
                AlertDialog.Builder(this@MainActivity).setTitle("Choose episode or stream").setItems(streams.map{it.title}.toTypedArray()){_,i->play(streams[i].url,media.name)}.show()
            }
            return
        }
        val addons=stringList("addons")
        if(addons.isEmpty()){ status.text="Add a Stremio-compatible add-on in Settings"; settings(); return }
        status.text="Finding streams…"
        scope.launch {
            val streams=withContext(Dispatchers.IO){StremioClient.streams(addons,media)}
            if(streams.isEmpty()){status.text="No playable HTTP streams returned";return@launch}
            AlertDialog.Builder(this@MainActivity).setTitle("Choose a stream").setItems(streams.map{it.title}.toTypedArray()){_,i->play(streams[i].url,media.name)}.show()
        }
    }

    private fun liveTv() {
        val root=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setBackgroundColor(Color.rgb(9,11,16)) }
        val categoryPane=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(14),dp(12),dp(12)); setBackgroundColor(Color.rgb(20,23,31)) }
        categoryPane.addView(title("LIVE TV",20f).apply{setPadding(dp(8),0,0,dp(14))})
        val categories=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        categoryPane.addView(ScrollView(this).apply{addView(categories)},LinearLayout.LayoutParams(-1,0,1f))
        categoryPane.addView(button("Xtream Login",::xtreamDialog),LinearLayout.LayoutParams(-1,dp(50)))
        val center=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),0)}
        val filter=EditText(this).apply{hint="Search channels";setHintTextColor(Color.GRAY);setTextColor(Color.WHITE);setSingleLine()}
        center.addView(filter,LinearLayout.LayoutParams(-1,dp(52)))
        val list=RecyclerView(this).apply{layoutManager=LinearLayoutManager(this@MainActivity)};center.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        val detail=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(28),dp(24),dp(20));setBackgroundColor(Color.rgb(14,17,24))}
        detail.addView(title("Now Playing",16f));val channelName=title("Choose a channel",28f).apply{setPadding(0,dp(20),0,dp(10))};detail.addView(channelName)
        val program=copy("Select a channel to see current program information.");detail.addView(program)
        var group="All Channels";var query=""
        fun render(){val visible=channels.filter{(group=="All Channels"||it.group==group)&&(query.isBlank()||it.name.contains(query,true))};list.adapter=ChannelAdapter(visible,programmes,::playChannel,::favoriteChannel){c->channelName.text=c.name;val now=System.currentTimeMillis();val show=programmes.firstOrNull{it.channelId==c.tvgId&&now in it.startMillis until it.stopMillis};program.text=show?.let{it.title+"\n\n"+(it.description?:"")}?:(c.group+"\n\nNo guide information available")};status.text=visible.size.toString()+" channels • "+group}
        (listOf("All Channels")+channels.map{it.group}.distinct().sorted()).forEach{category->categories.addView(Button(this).apply{text=category;isAllCaps=false;gravity=Gravity.START or Gravity.CENTER_VERTICAL;setTextColor(Color.WHITE);setBackgroundColor(Color.TRANSPARENT);isFocusable=true;setOnFocusChangeListener{v,f->v.setBackgroundColor(if(f)Color.rgb(66,70,83) else Color.TRANSPARENT)};setOnClickListener{group=category;render()}},LinearLayout.LayoutParams(-1,dp(48)))}
        filter.addTextChangedListener(TextChange{query=it;render()});render()
        root.addView(categoryPane,LinearLayout.LayoutParams(dp(230),-1));root.addView(center,LinearLayout.LayoutParams(dp(430),-1));root.addView(detail,LinearLayout.LayoutParams(0,-1,1f));swap(root,"Live TV")
    }

    private fun m3uDialog() {
        val input=EditText(this).apply { hint="M3U playlist URL"; setSingleLine() }
        AlertDialog.Builder(this).setTitle("Add M3U playlist").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Load"){_,_->loadM3u(input.text.toString())}.show()
    }

    private fun xtreamDialog() {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),0,dp(20),0) }
        val server=EditText(this).apply{hint="Server URL"}; val user=EditText(this).apply{hint="Username"}; val pass=EditText(this).apply{hint="Password"}
        box.addView(server);box.addView(user);box.addView(pass)
        server.setText(prefs.getString("xtream_server",""));user.setText(prefs.getString("xtream_user",""));pass.setText(prefs.getString("xtream_pass",""))
        AlertDialog.Builder(this).setTitle("Xtream Codes Login").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Connect"){_,_->
            connectXtream(server.text.toString(),user.text.toString(),pass.text.toString())
        }.show()
    }

    private fun connectXtream(server:String,user:String,pass:String){
        if(server.isBlank()||user.isBlank()||pass.isBlank())return
        status.text="Signing into Xtream Codes…"
        scope.launch{
            val client=XtreamClient(server,user,pass)
            runCatching{withContext(Dispatchers.IO){client.authenticate() to client.liveChannels()}}
                .onSuccess{(expiry,loaded)->prefs.edit().putString("xtream_server",server).putString("xtream_user",user).putString("xtream_pass",pass).putString("epg_url",client.epgUrl()).apply();channels=loaded;status.text="Xtream connected • "+loaded.size+" channels • expires "+expiry;loadEpg(client.epgUrl());liveTv()}
                .onFailure{status.text="Xtream login failed: "+it.message}
        }
    }

    private fun loadM3u(url:String,epg:String?=null) {
        if(url.isBlank())return; status.text="Loading playlist…"
        scope.launch { runCatching{withContext(Dispatchers.IO){M3uParser.parse(URL(url).readText())}}
            .onSuccess{channels=it;prefs.edit().putString("playlist_url",url).apply();if(!epg.isNullOrBlank())prefs.edit().putString("epg_url",epg).apply();status.text="Loaded "+it.size+" channels";if(!epg.isNullOrBlank())loadEpg(epg);liveTv()}
            .onFailure{status.text="Playlist error: "+it.message} }
    }

    private fun loadEpg(url:String) {
        if(url.isBlank())return; status.text="Loading guide…"
        scope.launch { runCatching{withContext(Dispatchers.IO){URL(url).openStream().use(XmlTvParser::parse)}}
            .onSuccess{programmes=it;status.text="Guide loaded • "+it.size+" programs"}.onFailure{status.text="Guide error: "+it.message} }
    }

    private fun guide() {
        swap(TiviGuideLayout(this,scope,channels,programmes,::playChannel),"Live TV Guide")
        status.text=if(programmes.isEmpty())"Add an XMLTV/Xtream guide source in Settings" else programmes.size.toString()+" programs"
    }

    private fun search() {
        val root=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(24),dp(20),dp(24),0)}
        val input=EditText(this).apply{hint="Search movies and series";setHintTextColor(Color.GRAY);setTextColor(Color.WHITE);setSingleLine()}
        root.addView(input,LinearLayout.LayoutParams(0,dp(58),1f));root.addView(button("Movies"){catalog("movie","Movie results",input.text.toString())});root.addView(button("Series"){catalog("series","Series results",input.text.toString())});swap(root,"Search")
    }

    private fun library() {
        val items=mediaList(); val grid=RecyclerView(this).apply{layoutManager=GridLayoutManager(this@MainActivity,6);adapter=MediaAdapter(scope,items,::openMedia)}
        swap(grid,"Library");status.text=items.size.toString()+" saved titles"
    }

    private fun settings() {
        val root=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setBackgroundColor(BG)}
        val categories=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(24),dp(12),dp(18));setBackgroundColor(Color.rgb(19,17,20))}
        categories.addView(title("SETTINGS",18f).apply{setPadding(dp(10),0,0,dp(18))})
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(32),dp(24),dp(34),dp(24))}
        val panelScroll=ScrollView(this).apply{addView(panel)}
        fun render(section:String){panel.removeAllViews();panel.addView(title(section,28f).apply{setPadding(0,0,0,dp(18))});when(section){
            "Live TV & Guide"->settingsLive(panel)
            "Movies & Series"->settingsAddons(panel)
            "Playback"->settingsPlayback(panel)
            "Appearance"->settingsAppearance(panel)
            "Updates & About"->settingsAbout(panel)
        }}
        listOf("Live TV & Guide","Movies & Series","Playback","Appearance","Updates & About").forEachIndexed{i,name->categories.addView(settingsCategory(name){render(name);status.text="Settings  •  $name"},LinearLayout.LayoutParams(-1,dp(54)).apply{bottomMargin=dp(6)});if(i==0)render(name)}
        root.addView(categories,LinearLayout.LayoutParams(dp(260),-1));root.addView(panelScroll,LinearLayout.LayoutParams(0,-1,1f));swap(root,"Settings")
    }

    private fun settingsLive(panel:LinearLayout){
        panel.addView(settingCard("Xtream Codes account", if (xtreamClient()!=null) "Connected • ${channels.size} channels" else "Not connected", "Manage login", ::xtreamDialog))
        val epg=EditText(this).apply{hint="XMLTV guide URL";setHintTextColor(MUTED);setTextColor(Color.WHITE);setText(prefs.getString("epg_url",""));setSingleLine();background=rounded(RAISED,8);setPadding(dp(16),0,dp(16),0)}
        panel.addView(settingHeader("TV guide","Override the Xtream guide with an XMLTV URL."));panel.addView(epg,LinearLayout.LayoutParams(-1,dp(54)).apply{bottomMargin=dp(10)});panel.addView(button("Save and refresh guide"){prefs.edit().putString("epg_url",epg.text.toString()).apply();loadEpg(epg.text.toString())})
    }
    private fun settingsAddons(panel:LinearLayout){
        panel.addView(settingHeader("Stremio-compatible add-ons","These power Movies and Series only. Live TV always stays on Xtream Codes."))
        val addon=EditText(this).apply{hint="https://…/manifest.json";setHintTextColor(MUTED);setTextColor(Color.WHITE);setSingleLine();background=rounded(RAISED,8);setPadding(dp(16),0,dp(16),0)}
        panel.addView(addon,LinearLayout.LayoutParams(-1,dp(54)).apply{bottomMargin=dp(10)});panel.addView(button("Add add-on"){val v=addon.text.toString().trim();if(v.isNotEmpty()){saveList("addons",(stringList("addons")+v).distinct());settings()}})
        stringList("addons").forEach{v->panel.addView(settingCard("Installed add-on",v,"Remove"){saveList("addons",stringList("addons")-v);settings()})}
    }
    private fun settingsPlayback(panel:LinearLayout){
        panel.addView(settingHeader("Player","Fast channel changes with hardware decoding and automatic decoder fallback."))
        panel.addView(toggleCard("Hardware decoder","Use device acceleration when available","hardware_decoder",true))
        panel.addView(toggleCard("Autoplay live channels","Begin playback immediately after selection","autoplay_live",true))
        panel.addView(toggleCard("Prefer HLS streams","Use adaptive .m3u8 streams when the provider offers them","prefer_hls",false))
    }
    private fun settingsAppearance(panel:LinearLayout){
        panel.addView(settingHeader("TV interface","Dionysus-style fixed rail, high-contrast focus states, compact guide rows."))
        panel.addView(toggleCard("Show status bar","Show source and loading messages at the top","show_status",true){status.visibility=if(it)View.VISIBLE else View.GONE})
        panel.addView(toggleCard("Large text","Increase labels for viewing across the room","large_text",false))
    }
    private fun settingsAbout(panel:LinearLayout){
        panel.addView(settingCard("Stream Fusion TV","Version ${com.stremiolivetv.BuildConfig.VERSION_NAME}\nUpdates install from your private app release channel.","Check for updates"){UpdateChecker(this).checkOnLaunch()})
        panel.addView(settingHeader("Sources","Movies & Series: Stremio-compatible catalogs\nLive TV & Guide: Xtream Codes / XMLTV"))
    }

    private fun restoreSources(){
        val client=xtreamClient()
        if(client!=null){scope.launch{runCatching{withContext(Dispatchers.IO){client.liveChannels()}}.onSuccess{channels=it;status.text="Xtream restored • "+it.size+" channels"}}}
        else prefs.getString("playlist_url",null)?.let(::loadM3u)
        prefs.getString("epg_url",null)?.takeIf(String::isNotBlank)?.let(::loadEpg)
    }
    private fun xtreamClient():XtreamClient?{val server=prefs.getString("xtream_server","").orEmpty();val user=prefs.getString("xtream_user","").orEmpty();val pass=prefs.getString("xtream_pass","").orEmpty();return if(server.isBlank()||user.isBlank()||pass.isBlank())null else XtreamClient(server,user,pass)}
    private fun playChannel(c:Channel)=play(c.url,c.name)
    private fun play(url:String,name:String)=startActivity(Intent(this,PlayerActivity::class.java).putExtra("url",url).putExtra("title",name))
    private fun favoriteChannel(c:Channel){val values=stringList("favorite_channels").toMutableList();if(!values.remove(c.url))values+=c.url;saveList("favorite_channels",values)}
    private fun isFavorite(id:String)=mediaList().any{it.id==id}
    private fun toggleFavorite(m:Media){val items=mediaList().toMutableList();val i=items.indexOfFirst{it.id==m.id};if(i>=0)items.removeAt(i)else items+=m;prefs.edit().putString("library",JSONArray(items.map(::mediaJson)).toString()).apply()}
    private fun mediaList():List<Media> = runCatching{val a=JSONArray(prefs.getString("library","[]"));(0 until a.length()).map{val v=a.getJSONObject(it);Media(v.getString("id"),v.getString("type"),v.getString("name"),v.optString("poster").ifBlank{null},v.optString("description").ifBlank{null},v.optString("year").ifBlank{null},v.optString("rating").ifBlank{null})}}.getOrDefault(emptyList())
    private fun mediaJson(m:Media)=org.json.JSONObject().apply{put("id",m.id);put("type",m.type);put("name",m.name);put("poster",m.poster);put("description",m.description);put("year",m.year);put("rating",m.rating)}
    private fun stringList(k:String):List<String> = runCatching{val a=JSONArray(prefs.getString(k,"[]"));(0 until a.length()).map{a.getString(it)}}.getOrDefault(emptyList())
    private fun saveList(k:String,v:List<String>)=prefs.edit().putString(k,JSONArray(v).toString()).apply()
    private fun swap(v:View,name:String){body.removeAllViews();body.addView(v,FrameLayout.LayoutParams(-1,-1));status.text=name}
    private fun title(v:String,s:Float)=TextView(this).apply{text=v;textSize=s;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD)}
    private fun copy(v:String)=TextView(this).apply{text=v;textSize=16f;setTextColor(MUTED);setPadding(0,dp(8),0,dp(10))}
    private fun button(v:String,click:()->Unit)=Button(this).apply{text=v;isAllCaps=false;setTextColor(Color.rgb(28,19,10));background=rounded(ACCENT,8);isFocusable=true;setTypeface(typeface,Typeface.BOLD);setOnFocusChangeListener{view,f->view.alpha=if(f)1f else .86f;view.scaleX=if(f)1.02f else 1f;view.scaleY=if(f)1.02f else 1f};setOnClickListener{click()}}
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun styleNav(v:Button,active:Boolean){v.background=rounded(if(active)ACCENT else Color.TRANSPARENT,8);v.setTextColor(if(active)Color.rgb(31,21,12) else MUTED)}
    private fun settingsCategory(label:String,click:()->Unit)=Button(this).apply{text=label;isAllCaps=false;gravity=Gravity.START or Gravity.CENTER_VERTICAL;textSize=15f;setTextColor(Color.WHITE);background=rounded(Color.TRANSPARENT,8);setPadding(dp(14),0,dp(10),0);isFocusable=true;setOnFocusChangeListener{v,f->v.background=rounded(if(f)RAISED else Color.TRANSPARENT,8)};setOnClickListener{click()}}
    private fun settingHeader(heading:String,description:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(6),0,dp(18));addView(title(heading,19f));addView(copy(description))}
    private fun settingCard(heading:String,description:String,action:String,click:()->Unit)=LinearLayout(this).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=rounded(RAISED,10);setPadding(dp(20),dp(14),dp(14),dp(14))
        val words=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;addView(title(heading,18f));addView(copy(description).apply{maxLines=3})}
        addView(words,LinearLayout.LayoutParams(0,-2,1f));addView(button(action,click),LinearLayout.LayoutParams(dp(190),dp(50)))
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)}
    }
    private fun toggleCard(heading:String,description:String,key:String,default:Boolean,changed:(Boolean)->Unit={}):View{
        val enabled=prefs.getBoolean(key,default)
        return settingCard(heading, description, if (enabled) "On" else "Off") {
            val value=!prefs.getBoolean(key,default);prefs.edit().putBoolean(key,value).apply();changed(value);settings()
        }
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    override fun onDestroy(){scope.cancel();super.onDestroy()}

    companion object{
        private val BG=Color.rgb(11,10,13)
        private val SURFACE=Color.rgb(26,17,22)
        private val RAISED=Color.rgb(42,28,36)
        private val ACCENT=Color.rgb(201,162,74)
        private val MUTED=Color.rgb(184,172,155)
    }
}

private class TextChange(val changed:(String)->Unit):android.text.TextWatcher{
    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=changed(s?.toString().orEmpty())
    override fun afterTextChanged(s:android.text.Editable?)=Unit
}
