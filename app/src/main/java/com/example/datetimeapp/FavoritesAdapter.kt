package com.example.datetimeapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class FavoritesAdapter(
    private val onItemClick: (FavoriteCity) -> Unit,
    private val onItemLongClick: (FavoriteCity) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.VH>() {

    val items: MutableList<FavoriteCity> = mutableListOf()

    fun submit(list: List<FavoriteCity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun swap(from: Int, to: Int) {
        Collections.swap(items, from, to)
        notifyItemMoved(from, to)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tv: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tv.text = "${item.cityName}（${item.zoneId}）"
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { onItemLongClick(item); true }
    }

    override fun getItemCount(): Int = items.size
}
