package com.samyak.repostore.ui.adapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.samyak.repostore.databinding.ItemPaymentAppBinding

data class PaymentAppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable
)

class PaymentAppAdapter(
    private val apps: List<PaymentAppInfo>,
    private val onPayClick: (PaymentAppInfo) -> Unit
) : RecyclerView.Adapter<PaymentAppAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemPaymentAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: PaymentAppInfo) {
            binding.ivAppIcon.setImageDrawable(app.icon)
            binding.tvAppName.text = app.appName
            binding.btnPay.setOnClickListener { onPayClick(app) }
            binding.root.setOnClickListener { onPayClick(app) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPaymentAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size
}
