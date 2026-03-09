package com.samyak.repostore.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.samyak.repostore.databinding.ItemLicenseBinding

data class LibraryInfo(
    val name: String,
    val author: String,
    val license: String,
    val url: String
)

class LicenseAdapter(
    private val libraries: List<LibraryInfo>,
    private val onItemClick: (LibraryInfo) -> Unit
) : RecyclerView.Adapter<LicenseAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemLicenseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(library: LibraryInfo) {
            binding.tvLibraryName.text = library.name
            binding.tvLibraryAuthor.text = library.author
            binding.tvLicenseType.text = library.license
            binding.root.setOnClickListener { onItemClick(library) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLicenseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(libraries[position])
    }

    override fun getItemCount(): Int = libraries.size
}
