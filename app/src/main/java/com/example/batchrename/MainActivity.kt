package com.example.batchrename

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.example.batchrename.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FileAdapter

    private var folderUri: Uri? = null
    private var folderDoc: DocumentFile? = null

    // Keep a map from displayed name -> DocumentFile, refreshed each time we list the folder
    private val nameToDoc = HashMap<String, DocumentFile>()

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            // Persist permission so we can write later
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            folderUri = uri
            folderDoc = DocumentFile.fromTreeUri(this, uri)
            binding.tvFolderPath.text = folderDoc?.uri?.path ?: uri.toString()
            loadFiles()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = FileAdapter(this, mutableListOf())
        binding.listViewFiles.adapter = adapter

        binding.btnPickFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            pickFolderLauncher.launch(intent)
        }

        binding.btnSelectAll.setOnClickListener {
            val shouldSelect = adapter.items.any { !it.selected }
            adapter.items.forEach { it.selected = shouldSelect }
            adapter.notifyDataSetChanged()
        }

        binding.radioGroupMode.setOnCheckedChangeListener { _: RadioGroup, _ ->
            updateModeVisibility()
            adapter.clearPreviews()
        }
        updateModeVisibility()

        binding.btnPreview.setOnClickListener { generatePreview() }
        binding.btnRename.setOnClickListener { performRename() }
    }

    private fun updateModeVisibility() {
        binding.layoutFindReplace.visibility =
            if (binding.radioFindReplace.isChecked) View.VISIBLE else View.GONE
        binding.etPrefixSuffix.visibility =
            if (binding.radioPrefix.isChecked || binding.radioSuffix.isChecked) View.VISIBLE else View.GONE
        binding.layoutNumbering.visibility =
            if (binding.radioNumbering.isChecked) View.VISIBLE else View.GONE
    }

    private fun loadFiles() {
        val doc = folderDoc ?: return
        val entries = mutableListOf<FileEntry>()
        nameToDoc.clear()

        doc.listFiles().forEach { file ->
            val name = file.name ?: return@forEach
            if (file.isFile) {
                entries.add(FileEntry(name = name, selected = true))
                nameToDoc[name] = file
            }
        }
        entries.sortBy { it.name.lowercase() }
        adapter.items.clear()
        adapter.items.addAll(entries)
        adapter.notifyDataSetChanged()

        if (entries.isEmpty()) {
            Toast.makeText(this, "Tidak ada file di folder ini", Toast.LENGTH_SHORT).show()
        }
    }

    private fun splitNameExt(fileName: String): Pair<String, String> {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < fileName.length - 1) {
            Pair(fileName.substring(0, dotIndex), fileName.substring(dotIndex))
        } else {
            Pair(fileName, "")
        }
    }

    /** Computes the new name for a given entry based on the currently selected mode. */
    private fun computeNewName(oldName: String, indexAmongSelected: Int): String {
        return when {
            binding.radioFindReplace.isChecked -> {
                val find = binding.etFind.text?.toString().orEmpty()
                val replace = binding.etReplace.text?.toString().orEmpty()
                if (find.isEmpty()) oldName else oldName.replace(find, replace)
            }
            binding.radioPrefix.isChecked -> {
                val prefix = binding.etPrefixSuffix.text?.toString().orEmpty()
                "$prefix$oldName"
            }
            binding.radioSuffix.isChecked -> {
                val suffix = binding.etPrefixSuffix.text?.toString().orEmpty()
                val (base, ext) = splitNameExt(oldName)
                "$base$suffix$ext"
            }
            binding.radioNumbering.isChecked -> {
                val base = binding.etBaseName.text?.toString()?.ifBlank { "File" } ?: "File"
                val start = binding.etStartNumber.text?.toString()?.toIntOrNull() ?: 1
                val num = start + indexAmongSelected
                val (_, ext) = splitNameExt(oldName)
                val padded = num.toString().padStart(3, '0')
                "$base$padded$ext"
            }
            else -> oldName
        }
    }

    private fun generatePreview() {
        if (adapter.items.isEmpty()) {
            Toast.makeText(this, "Pilih folder dulu", Toast.LENGTH_SHORT).show()
            return
        }
        var idx = 0
        adapter.items.forEach { entry ->
            if (entry.selected) {
                entry.newName = computeNewName(entry.name, idx)
                idx++
            } else {
                entry.newName = null
            }
        }
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Pratinjau diperbarui", Toast.LENGTH_SHORT).show()
    }

    private fun performRename() {
        val doc = folderDoc
        if (doc == null) {
            Toast.makeText(this, "Pilih folder dulu", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = adapter.items.filter { it.selected }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Tidak ada file dipilih", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure previews are computed
        var idx = 0
        selected.forEach { entry ->
            if (entry.newName.isNullOrEmpty()) {
                entry.newName = computeNewName(entry.name, idx)
            }
            idx++
        }

        var successCount = 0
        var failCount = 0

        selected.forEach { entry ->
            val target = entry.newName ?: entry.name
            if (target != entry.name) {
                val file = nameToDoc[entry.name]
                if (file != null) {
                    try {
                        val ok = file.renameTo(target)
                        if (ok) successCount++ else failCount++
                    } catch (e: Exception) {
                        failCount++
                    }
                } else {
                    failCount++
                }
            }
        }

        Toast.makeText(
            this,
            "Berhasil: $successCount, Gagal: $failCount",
            Toast.LENGTH_LONG
        ).show()

        // Refresh list to reflect new names on disk
        loadFiles()
    }
}
