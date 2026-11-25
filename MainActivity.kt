package com.example.archiveplayer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.PresetReverb
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class ArchiveHit(val identifier: String, val title: String, val creator: String, val year: String)
data class SavedMark(val title: String, val position: Long)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var reverb: PresetReverb? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            ArchivePlayerUi(
                player = player,
                onLocal = { uri, name -> playUri(uri, name) },
                onArchive = { hit -> fetchAndPlay(hit) },
                onSpeed = { speed -> player.setPlaybackParameters(PlaybackParameters(speed, 1f)) },
                onPitch = { pitch -> player.setPlaybackParameters(PlaybackParameters(player.playbackParameters.speed, pitch)) },
                onAudioFx = { eq, bassAmount, reverbAmount -> applyAudioFx(eq, bassAmount, reverbAmount) },
                onPip = { enterPip() },
                onDownload = { hit -> lifecycleScope.launch { val fileUrl = withContext(Dispatchers.IO) { resolveArchiveFile(hit.identifier) }; if (fileUrl != null) download(fileUrl, hit.title) } }
            )
        }
    }

    private fun playUri(uri: Uri, name: String) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    private fun fetchAndPlay(hit: ArchiveHit) {
        lifecycleScope.launch {
            val mediaUrl = withContext(Dispatchers.IO) { resolveArchiveFile(hit.identifier) }
            if (mediaUrl != null) {
                player.setMediaItem(MediaItem.fromUri(mediaUrl))
                player.prepare()
                player.play()
            }
        }
    }

    private fun resolveArchiveFile(identifier: String): String? {
        val api = "https://archive.org/metadata/${URLEncoder.encode(identifier, "UTF-8")}"
        val conn = URL(api).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        return try {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val files = json.optJSONArray("files") ?: return null
            val candidates = mutableListOf<Pair<Int, String>>()
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                val name = f.optString("name")
                val format = f.optString("format").lowercase(Locale.ROOT)
                if (f.optString("source") == "original" && f.optString("private") != "true" &&
                    (name.endsWith(".mp4", true) || name.endsWith(".webm", true) ||
                     name.endsWith(".mp3", true) || name.endsWith(".ogg", true) ||
                     name.endsWith(".m4v", true) || name.endsWith(".mp3"))) {
                    val score = when {
                        name.endsWith(".mp4", true) -> 0
                        name.endsWith(".mp3", true) -> 1
                        name.endsWith(".webm", true) -> 2
                        else -> 3
                    }
                    candidates.add(score to name)
                }
            }
            val file = candidates.sortedBy { it.first }.firstOrNull()?.second ?: return null
            "https://archive.org/download/${Uri.encode(identifier)}/${Uri.encode(file)}"
        } finally { conn.disconnect() }
    }

    private fun applyAudioFx(eqValues: List<Int>, bassAmount: Int, reverbAmount: Int) {
        val session = player.audioSessionId
        if (session == 0) return
        try {
            equalizer?.release()
            bass?.release()
            reverb?.release()
            equalizer = Equalizer(0, session).apply {
                enabled = true
                val range = bandLevelRange
                for (i in 0 until numberOfBands) {
                    val value = eqValues.getOrElse(i) { 0 }.coerceIn(-12, 12)
                    val millibels = (value * 100).coerceIn(range[0].toInt(), range[1].toInt())
                    setBandLevel(i.toShort(), millibels.toShort())
                }
            }
            bass = BassBoost(0, session).apply { setStrength((bassAmount * 1000 / 12).toShort()); enabled = bassAmount > 0 }
            reverb = PresetReverb(0, session).apply {
                preset = PresetReverb.PRESET_LARGEHALL
                enabled = reverbAmount > 0
            }
        } catch (_: Exception) { }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    private fun download(url: String, filename: String) {
        val request = android.app.DownloadManager.Request(Uri.parse(url))
            .setTitle(filename)
            .setDescription("Archive Player download")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        dm.enqueue(request)
    }

    override fun onDestroy() {
        equalizer?.release(); bass?.release(); reverb?.release()
        player.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivePlayerUi(
    player: ExoPlayer,
    onLocal: (Uri, String) -> Unit,
    onArchive: (ArchiveHit) -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onAudioFx: (List<Int>, Int, Int) -> Unit,
    onPip: () -> Unit,
    onDownload: (ArchiveHit) -> Unit
) {
    val purple = Color(0xFF9C85FF)
    val bg = Color(0xFF10131B)
    val card = Color(0xFF191E29)
    var tab by remember { mutableStateOf("Player") }
    var search by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ArchiveHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Choose a local file or search Archive.org.") }
    var currentTitle by remember { mutableStateOf("Nothing playing") }
    var speed by remember { mutableFloatStateOf(1f) }
    var pitch by remember { mutableFloatStateOf(1f) }
    var crt by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var eq by remember { mutableStateOf(List(5) { 0 }) }
    var bassAmount by remember { mutableIntStateOf(0) }
    var reverbAmount by remember { mutableIntStateOf(0) }
    var bookmarks by remember { mutableStateOf<List<SavedMark>>(emptyList()) }
    var queue by remember { mutableStateOf<List<String>>(emptyList()) }
    var isPlaying by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            currentTitle = uri.lastPathSegment ?: "Local media"
            onLocal(uri, currentTitle)
            message = "Local media loaded"
        }
    }
    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0
            kotlinx.coroutines.delay(400)
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = purple, background = bg, surface = card)) {
        Scaffold(containerColor = bg, topBar = {
            TopAppBar(title = { Column { Text("ARCHIVE PLAYER", fontSize = 18.sp); Text("Media • Archive • Offline", fontSize = 11.sp, color = Color.Gray) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg))
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                NavigationBar(containerColor = card) {
                    listOf("Player","Archive","Library","Effects").forEach { item ->
                        NavigationBarItem(selected = tab == item, onClick = { tab = item }, label = { Text(item) },
                            icon = { Icon(when(item){"Archive"->Icons.Default.Search;"Library"->Icons.Default.QueueMusic;"Effects"->Icons.Default.Tune;else->Icons.Default.PlayArrow}, item) })
                    }
                }
                when(tab) {
                    "Player" -> PlayerScreen(player, currentTitle, message, isPlaying, position, duration, speed, crt, loop, shuffle, bookmarks,
                        { filePicker.launch(arrayOf("video/*","audio/*")) }, { player.playWhenReady = !player.isPlaying },
                        { player.seekTo((position + 15000).coerceAtMost(duration)) }, { player.seekTo((position - 15000).coerceAtLeast(0)) },
                        { speed = if (speed >= 8f) .25f else speed * 2f; onSpeed(speed) },
                        { crt = !crt }, { loop = !loop; player.repeatMode = if(loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF },
                        { shuffle = !shuffle; player.shuffleModeEnabled = shuffle },
                        { bookmarks = bookmarks + SavedMark(currentTitle, position) },
                        onPip, { player.seekTo(it) })
                    "Archive" -> ArchiveScreen(search, { search = it }, loading, hits, message, {
                        loading = true
                        kotlinx.coroutines.MainScope().launch {
                            val result = searchArchive(search)
                            hits = result
                            loading = false
                            message = if(result.isEmpty()) "No results found. Try another search." else "${result.size} results"
                        }
                    }, { hit -> currentTitle = hit.title; onArchive(hit); tab = "Player"; message = "Opening Archive.org item: ${hit.identifier}" },
                        { hit -> onDownload(hit) })
                    "Library" -> LibraryScreen(queue, bookmarks, { queue = queue + it }, { queue = emptyList() }, { mark ->
                        player.seekTo(mark.position); tab = "Player"
                    })
                    else -> EffectsScreen(speed, { speed = it; onSpeed(it) }, pitch, { pitch = it; onPitch(it) }, eq, { eq=it; onAudioFx(eq,bassAmount,reverbAmount) }, bassAmount, { bassAmount=it; onAudioFx(eq,bassAmount,reverbAmount) }, reverbAmount, { reverbAmount=it; onAudioFx(eq,bassAmount,reverbAmount) })
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(
    player: ExoPlayer, title: String, message: String, playing: Boolean, position: Long, duration: Long,
    speed: Float, crt: Boolean, loop: Boolean, shuffle: Boolean, marks: List<SavedMark>,
    open: () -> Unit, toggle: () -> Unit, next: () -> Unit, prev: () -> Unit, changeSpeed: () -> Unit,
    toggleCrt: () -> Unit, toggleLoop: () -> Unit, toggleShuffle: () -> Unit, bookmark: () -> Unit,
    pip: () -> Unit, seek: (Long) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A202B))) {
            Box(Modifier.fillMaxWidth().aspectRatio(16/9f).background(Color.Black), contentAlignment = Alignment.Center) {
                AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = player; useController = false } }, modifier = Modifier.fillMaxSize())
                if (crt) Canvas(Modifier.fillMaxSize()) { for (y in 0..size.height.toInt() step 4) drawLine(Color.Black.copy(alpha=.18f), androidx.compose.ui.geometry.Offset(0f,y.toFloat()), androidx.compose.ui.geometry.Offset(size.width,y.toFloat()), strokeWidth=1f) }
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, color = Color.Gray, fontSize = 12.sp)
        Slider(value = if(duration>0) position.toFloat()/duration else 0f, onValueChange = { if(duration>0) seek((duration*it).toLong()) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(time(position)); Text(time(duration)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick=prev){Icon(Icons.Default.SkipPrevious,"Previous")}
            FilledIconButton(onClick=toggle, modifier=Modifier.size(56.dp)){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,"Play")}
            IconButton(onClick=next){Icon(Icons.Default.SkipNext,"Next")}
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly) {
            OutlinedButton(onClick=changeSpeed){Text("$speed×")}
            OutlinedButton(onClick=toggleLoop){Text(if(loop)"Repeat: On" else "Repeat")}
            OutlinedButton(onClick=toggleShuffle){Text(if(shuffle)"Shuffle: On" else "Shuffle")}
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly) {
            OutlinedButton(onClick=open){Text("Open file")}
            OutlinedButton(onClick=toggleCrt){Text(if(crt)"CRT: On" else "CRT filter")}
            OutlinedButton(onClick=pip){Text("PiP")}
            OutlinedButton(onClick=bookmark){Text("Bookmark")}
        }
        Text("Playback speed quick control (tap to cycle)", color=Color.Gray, fontSize=12.sp)
    }
}

@Composable
fun ArchiveScreen(search: String, setSearch:(String)->Unit, loading:Boolean, hits:List<ArchiveHit>, message:String, searchNow:()->Unit, play:(ArchiveHit)->Unit, download:(ArchiveHit)->Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        OutlinedTextField(search, setSearch, modifier=Modifier.fillMaxWidth(), label={Text("Search Archive.org")}, singleLine=true)
        Button(onClick=searchNow, modifier=Modifier.fillMaxWidth(), enabled=!loading){Text(if(loading)"Searching…" else "Search")}
        Text(message, color=Color.Gray, fontSize=12.sp, modifier=Modifier.padding(8.dp))
        LazyColumn { items(hits) { hit ->
            Card(Modifier.fillMaxWidth().padding(vertical=5.dp), colors=CardDefaults.cardColors(containerColor=Color(0xFF191E29))) {
                Column(Modifier.padding(12.dp)) {
                    Text(hit.title, style=MaterialTheme.typography.titleSmall)
                    Text("${hit.creator} • ${hit.year} • ${hit.identifier}", color=Color.Gray, fontSize=11.sp)
                    Row { Button(onClick={play(hit)}){Text("Play")}; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick={download(hit)}){Text("Download")}}
                }
            }
        } }
    }
}

@Composable
fun LibraryScreen(queue:List<String>, marks:List<SavedMark>, add:(String)->Unit, clear:()->Unit, jump:(SavedMark)->Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("Queue", style=MaterialTheme.typography.titleLarge)
        Row { Button(onClick={add("Demo entry")}){Text("Add placeholder")}; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick=clear){Text("Clear")} }
        queue.forEach { Text("♫  $it", Modifier.padding(8.dp)) }
        Divider(Modifier.padding(vertical=10.dp))
        Text("Bookmarks", style=MaterialTheme.typography.titleLarge)
        marks.forEach { mark -> Row(Modifier.fillMaxWidth().clickable{jump(mark)}.padding(8.dp), horizontalArrangement=Arrangement.SpaceBetween){Text(mark.title);Text(time(mark.position))} }
        if(marks.isEmpty()) Text("Bookmarks appear here.", color=Color.Gray)
    }
}

@Composable
fun EffectsScreen(speed:Float, setSpeed:(Float)->Unit, pitch:Float, setPitch:(Float)->Unit, eq:List<Int>, setEq:(List<Int>)->Unit, bass:Int, setBass:(Int)->Unit, reverb:Int, setReverb:(Int)->Unit) {
    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Playback speed: ${"%.2f".format(speed)}×")
        Slider(value=speed, onValueChange=setSpeed, valueRange=.25f..8f)
        Text("Pitch: ${"%.2f".format(pitch)}")
        Slider(value=pitch,onValueChange=setPitch,valueRange=.5f..2f)
        Text("Equalizer", style=MaterialTheme.typography.titleMedium)
        eq.forEachIndexed { i, value -> Row(verticalAlignment=Alignment.CenterVertically){Text(listOf("60","250","1k","4k","16k")[i], Modifier.width(45.dp));Slider(value=value.toFloat(),onValueChange={ newValue -> setEq(eq.toMutableList().also{it[i]=newValue.toInt()})},valueRange=-12f..12f)} }
        Text("Bass boost: $bass")
        Slider(value=bass.toFloat(),onValueChange={setBass(it.toInt())},valueRange=0f..12f)
        Text("Reverb: $reverb")
        Slider(value=reverb.toFloat(),onValueChange={setReverb(it.toInt())},valueRange=0f..100f)
        Text("Audio effects depend on device support and may not apply to every codec.", color=Color.Gray,fontSize=12.sp)
    }
}

suspend fun searchArchive(query:String):List<ArchiveHit> = withContext(Dispatchers.IO) {
    if(query.isBlank()) return@withContext emptyList()
    val q=URLEncoder.encode("title:$query OR description:$query", "UTF-8")
    val url="https://archive.org/advancedsearch.php?q=$q&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&rows=25&page=1&output=json"
    val c=URL(url).openConnection() as HttpURLConnection
    c.connectTimeout=15000;c.readTimeout=20000
    try {
        val root=JSONObject(c.inputStream.bufferedReader().use{it.readText()})
        val docs=root.getJSONObject("response").getJSONArray("docs")
        (0 until docs.length()).map { i ->
            val d=docs.getJSONObject(i)
            ArchiveHit(d.optString("identifier"),d.optString("title","Untitled"),d.optString("creator","Unknown"),d.optString("year",""))
        }
    } catch(_:Exception){ emptyList() } finally {c.disconnect()}
}

fun time(ms:Long):String {
    val seconds=ms/1000
    return "%02d:%02d".format(seconds/60,seconds%60)
}
