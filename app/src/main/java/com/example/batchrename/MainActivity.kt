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

    private val pathToDoc = HashMap<String, DocumentFile>()

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
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

        binding.cbIncludeSubfolders.setOnCheckedChangeListener { _, _ ->
            if (folderDoc != null) loadFiles()
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

    private fun collectFilesRecursively(
        folder: DocumentFile,
        relPath: String,
        entries: MutableList<FileEntry>
    ) {
        folder.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            val currentPath = if (relPath.isEmpty()) name else "$relPath/$name"
            if (child.isDirectory) {
                collectFilesRecursively(child, currentPath, entries)
            } else if (child.isFile) {
                entries.add(FileEntry(displayPath = currentPath, fileName = name))
                pathToDoc[currentPath] = child
            }
        }
    }

    private fun loadFiles() {
        val doc = folderDoc ?: return
        val entries = mutableListOf<FileEntry>()
        pathToDoc.clear()

        if (binding.cbIncludeSubfolders.isChecked) {
            collectFilesRecursively(doc, "", entries)
        } else {
            doc.listFiles().forEach { file ->
                val name = file.name ?: return@forEach
                if (file.isFile) {
                    entries.add(FileEntry(displayPath = name, fileName = name))
                    pathToDoc[name] = file
                }
            }
        }

        entries.sortBy { it.displayPath.lowercase() }
        adapter.items.clear()
        adapter.items.addAll(entries)
        adapter.notifyDataSetChanged()

        if (entries.isEmpty()) {
            Toast.makeText(this, "Tidak ada file ditemukan", Toast.LENGTH_SHORT).show()
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

    private fun computeNewBaseName(oldName: String, indexAmongSelected: Int): String {
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
                val newBase = computeNewBaseName(entry.fileName, idx)
                entry.newBaseName = newBase
                val parent = entry.displayPath.substringBeforeLast("/", "")
                entry.newDisplayPath = if (parent.isNotEmpty()) "$parent/$newBase" else newBase
                idx++
            } else {
                entry.newBaseName = null
                entry.newDisplayPath = null
            }
        }
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Pratinjau diperbarui", Toast.LENGTH_SHORT).show()
    }

    private fun performRename() {
        if (folderDoc == null) {
            Toast.makeText(this, "Pilih folder dulu", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = adapter.items.filter { it.selected }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Tidak ada file dipilih", Toast.LENGTH_SHORT).show()
            return
        }

        var idx = 0
        selected.forEach { entry ->
            if (entry.newBaseName.isNullOrEmpty()) {
                val newBase = computeNewBaseName(entry.fileName, idx)
                entry.newBaseName = newBase
                val parent = entry.displayPath.substringBeforeLast("/", "")
                entry.newDisplayPath = if (parent.isNotEmpty()) "$parent/$newBase" else newBase
            }
            idx++
        }

        var successCount = 0
        var failCount = 0

        selected.forEach { entry ->
            val targetBase = entry.newBaseName ?: entry.fileName
            if (targetBase != entry.fileName) {
                val file = pathToDoc[entry.displayPath]
                if (file != null) {
                    try {
                        val ok = file.renameTo(targetBase)
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

        loadFiles()
    }
}
