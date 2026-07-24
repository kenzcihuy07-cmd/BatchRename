package com.example.batchrename

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView

/**
 * Represents one file entry: its DocumentFile name, whether it's selected,
 * and the previewed new name (if any).
 */
data class FileEntry(
    val name: String,
    var selected: Boolean = true,
    var newName: String? = null
)

class FileAdapter(
    private val context: Context,
    val items: MutableList<FileEntry>
) : BaseAdapter() {

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_file, parent, false)

        val entry = items[position]
        val checkBox = view.findViewById<CheckBox>(R.id.checkBox)
        val tvOldName = view.findViewById<TextView>(R.id.tvOldName)
        val tvNewName = view.findViewById<TextView>(R.id.tvNewName)

        // Avoid recycled-listener firing on wrong item
        checkBox.setOnCheckedChangeListener(null)
        checkBox.isChecked = entry.selected
        tvOldName.text = entry.name

        if (!entry.newName.isNullOrEmpty() && entry.newName != entry.name) {
            tvNewName.visibility = View.VISIBLE
            tvNewName.text = "\u2192 ${entry.newName}"
        } else {
            tvNewName.visibility = View.GONE
        }

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            entry.selected = isChecked
        }

        return view
    }

    fun clearPreviews() {
        items.forEach { it.newName = null }
        notifyDataSetChanged()
    }
}
