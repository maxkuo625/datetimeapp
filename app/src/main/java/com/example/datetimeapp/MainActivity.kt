package com.example.datetimeapp  // ← 改成你的 package 名稱

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    // 偏好
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    // 狀態
    private var currentZone: ZoneId = ZoneId.of("Asia/Taipei")
    private var is24Hour: Boolean = true

    // View
    private lateinit var tvDateTime: TextView
    private lateinit var tvGreeting: TextView
    private lateinit var btnShowAll: Button
    private lateinit var btnToggleBg: Button
    private lateinit var btnAddFavorite: Button
    private lateinit var switch24Hour: Switch
    private lateinit var actvCity: AutoCompleteTextView
    private lateinit var tvEmpty: TextView
    private lateinit var rvFavorites: RecyclerView

    // 每秒更新
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateNow()
            handler.postDelayed(this, 1000L)
        }
    }

    // 背景色池
    private val colorPool = listOf(
        "#FFF3E0", "#E3F2FD", "#E8F5E9", "#FCE4EC", "#EDE7F6",
        "#FFFDE7", "#E0F7FA", "#F3E5F5", "#F1F8E9", "#ECEFF1"
    )

    // 城市清單
    private val cityZones = listOf(
        "台北" to "Asia/Taipei",
        "東京" to "Asia/Tokyo",
        "首爾" to "Asia/Seoul",
        "北京" to "Asia/Shanghai",
        "新加坡" to "Asia/Singapore",
        "曼谷" to "Asia/Bangkok",
        "新德里" to "Asia/Kolkata",
        "杜拜" to "Asia/Dubai",
        "雪梨" to "Australia/Sydney",
        "墨爾本" to "Australia/Melbourne",
        "奧克蘭" to "Pacific/Auckland",
        "檀香山" to "Pacific/Honolulu",
        "洛杉磯" to "America/Los_Angeles",
        "舊金山" to "America/Los_Angeles",
        "丹佛" to "America/Denver",
        "芝加哥" to "America/Chicago",
        "紐約" to "America/New_York",
        "多倫多" to "America/Toronto",
        "墨西哥城" to "America/Mexico_City",
        "聖保羅" to "America/Sao_Paulo",
        "里斯本" to "Europe/Lisbon",
        "倫敦" to "Europe/London",
        "巴黎" to "Europe/Paris",
        "柏林" to "Europe/Berlin",
        "羅馬" to "Europe/Rome",
        "馬德里" to "Europe/Madrid",
        "莫斯科" to "Europe/Moscow"
    )

    // 格式器
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.TAIWAN)
    private val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.TAIWAN)

    // Room
    private val db by lazy { AppDb.get(this) }
    private val favoriteDao by lazy { db.favoriteDao() }

    // RecyclerView adapter
    private lateinit var favoritesAdapter: FavoritesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // find views
        tvDateTime = findViewById(R.id.tvDateTime)
        tvGreeting = findViewById(R.id.tvGreeting)
        btnShowAll = findViewById(R.id.btnShowAll)
        btnToggleBg = findViewById(R.id.btnToggleBg)
        btnAddFavorite = findViewById(R.id.btnAddFavorite)
        switch24Hour = findViewById(R.id.switch24Hour)
        actvCity = findViewById(R.id.actvCity)
        tvEmpty = findViewById(R.id.tvEmpty)
        rvFavorites = findViewById(R.id.rvFavorites)

        // 載入偏好
        val savedZoneId = prefs.getString("zoneId", "Asia/Taipei")!!
        currentZone = ZoneId.of(savedZoneId)
        is24Hour = prefs.getBoolean("is24Hour", true)
        switch24Hour.isChecked = is24Hour
        val savedBg = prefs.getInt("bgColor", 0)
        if (savedBg != 0) findViewById<View>(R.id.rootLayout).setBackgroundColor(savedBg)

        // 城市 AutoComplete
        val items = cityZones.map { (city, zone) -> "$city（$zone）" }
        actvCity.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, items))
        val idx = cityZones.indexOfFirst { it.second == savedZoneId }
        actvCity.setText(if (idx >= 0) items[idx] else items.first(), false)
        actvCity.setOnItemClickListener { _, _, position, _ ->
            currentZone = ZoneId.of(cityZones[position].second)
            prefs.edit().putString("zoneId", currentZone.id).apply()
            updateNow()
        }
        actvCity.setOnDismissListener {
            resolveZoneFromInput(actvCity.text.toString())?.let {
                currentZone = it
                prefs.edit().putString("zoneId", currentZone.id).apply()
                updateNow()
            }
        }

        // 24/12 小時制
        switch24Hour.setOnCheckedChangeListener { _, checked ->
            is24Hour = checked
            prefs.edit().putBoolean("is24Hour", is24Hour).apply()
            updateNow()
        }

        // 手動更新
        btnShowAll.setOnClickListener { updateNow() }

        // 背景色
        btnToggleBg.setOnClickListener {
            val root = findViewById<View>(R.id.rootLayout)
            val color = Color.parseColor(colorPool.random(Random(System.nanoTime())))
            root.setBackgroundColor(color)
            prefs.edit().putInt("bgColor", color).apply()
        }

        // RecyclerView + Adapter
        favoritesAdapter = FavoritesAdapter(
            onItemClick = { item ->
                currentZone = ZoneId.of(item.zoneId)
                prefs.edit().putString("zoneId", item.zoneId).apply()
                actvCity.setText("${item.cityName}（${item.zoneId}）", false)
                updateNow()
            }
        )
        rvFavorites.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = favoritesAdapter
        }

        // ItemTouchHelper：拖曳排序 + 左右滑動刪除
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                // 用 adapterPosition（相容性最好）
                favoritesAdapter.swap(vh.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 滑動刪除
                val pos = viewHolder.adapterPosition
                val item = favoritesAdapter.items.getOrNull(pos) ?: return
                lifecycleScope.launch {
                    favoriteDao.delete(item)
                }
            }

            // 放開手指：把目前順序寫回 DB（以列表位置作為 sortOrder）
            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                val reordered = favoritesAdapter.items.mapIndexed { index, item ->
                    item.copy(sortOrder = index)
                }
                lifecycleScope.launch {
                    favoriteDao.updateAll(reordered)
                }
            }

            override fun isLongPressDragEnabled(): Boolean = true // 長按即可拖
        })
        touchHelper.attachToRecyclerView(rvFavorites)

        // 加入最愛
        btnAddFavorite.setOnClickListener {
            val input = actvCity.text.toString()
            val zone = resolveZoneFromInput(input)
            val cityName = extractCityName(input) ?: "自訂城市"
            if (zone == null) {
                Toast.makeText(this, "無法辨識城市/時區", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                favoriteDao.insert(
                    FavoriteCity(cityName = cityName, zoneId = zone.id, sortOrder = Int.MAX_VALUE)
                )
            }
        }

        // 觀察最愛清單
        lifecycleScope.launch {
            favoriteDao.observeAll().collectLatest { list ->
                favoritesAdapter.submit(list)
                val empty = list.isEmpty()
                tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                rvFavorites.visibility = if (empty) View.GONE else View.VISIBLE
            }
        }

        updateNow()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    /** 依目前時區與 24/12 小時制更新畫面 */
    private fun updateNow() {
        val now = ZonedDateTime.now(currentZone)
        val timePattern = if (is24Hour) "HH:mm:ss" else "hh:mm:ss a"
        val timeFormatterLocale = if (is24Hour) Locale.getDefault() else Locale.TAIWAN
        val timeFormatter = DateTimeFormatter.ofPattern(timePattern, timeFormatterLocale)

        val full = "${now.format(dateFormatter)} ${now.format(timeFormatter)}"
        tvDateTime.text = full

        val greeting = when (now.hour) {
            in 5..11 -> "早安"
            in 12..17 -> "午安"
            else -> "晚上好"
        }
        val dow = now.format(dayOfWeekFormatter)
        tvGreeting.text = "$greeting，今天是$dow"
    }

    /** 從輸入字串試著找 ZoneId（支援城市名或完整 ZoneId） */
    private fun resolveZoneFromInput(input: String): ZoneId? {
        cityZones.firstOrNull { (city, _) -> input.startsWith(city) }?.let { return ZoneId.of(it.second) }
        val zoneInParen = Regex(".*（(.+?)）").find(input)?.groupValues?.getOrNull(1)
        return try {
            val raw = zoneInParen ?: input.trim()
            ZoneId.of(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCityName(input: String): String? =
        input.substringBefore('（', missingDelimiterValue = input).takeIf { it.isNotBlank() }

    // ===== RecyclerView Adapter（內嵌；無長按刪除，改由滑動刪除） =====
    private class FavoritesAdapter(
        private val onItemClick: (FavoriteCity) -> Unit
    ) : RecyclerView.Adapter<FavoritesAdapter.VH>() {

        val items: MutableList<FavoriteCity> = mutableListOf()

        fun submit(list: List<FavoriteCity>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        fun swap(from: Int, to: Int) {
            if (from in items.indices && to in items.indices) {
                java.util.Collections.swap(items, from, to)
                notifyItemMoved(from, to)
            }
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tv: TextView = itemView.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tv.text = "${item.cityName}（${item.zoneId}）"
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
