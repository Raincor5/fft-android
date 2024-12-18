package com.example.labels.utils

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.labels.LabelResponse
import com.example.labels.R
import java.text.SimpleDateFormat
import java.util.Locale

class LabelAdapter(private val labels: LinkedHashMap<String, LabelResponse>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_NORMAL = 1
    private val VIEW_TYPE_DEFROSTED = 2
    override fun getItemCount(): Int = labels.size

    // ViewHolder for Normal Labels
    inner class NormalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.cardView)
        val labelName: TextView = itemView.findViewById(R.id.labelName)
        val labelBatch: TextView = itemView.findViewById(R.id.labelBatch)
        val labelPrepped: TextView = itemView.findViewById(R.id.labelPrepped)
        val labelUseBy: TextView = itemView.findViewById(R.id.labelUseBy)
    }

    // ViewHolder for Defrosted Labels
    inner class DefrostedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.cardView)
        val labelName: TextView = itemView.findViewById(R.id.labelName)
        val labelBatch: TextView = itemView.findViewById(R.id.labelBatch)
        val labelDefrost: TextView = itemView.findViewById(R.id.labelDefrost)
        val labelReadyToPrep: TextView = itemView.findViewById(R.id.labelReadyToPrep)
        val labelUseBy: TextView = itemView.findViewById(R.id.labelUseBy)
    }

    override fun getItemViewType(position: Int): Int {
        val label = labels.values.toList()[position].parsed_data
        return if (label.label_type == "Defrosted") VIEW_TYPE_DEFROSTED else VIEW_TYPE_NORMAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_DEFROSTED) {
            DefrostedViewHolder(inflater.inflate(R.layout.item_label_defrost, parent, false))
        } else {
            NormalViewHolder(inflater.inflate(R.layout.item_label, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val label = labels.values.toList()[position].parsed_data


        val backgroundColor = when {
            label.dates.size == 3 && isBeingDefrosted(label.dates) -> {
                Log.d("LabelAdapter", "Label '${label.product_name}' is being defrosted")
                Color.WHITE
            }
            isExpired(label.dates) -> {
                Log.d("LabelAdapter", "Label '${label.product_name}' is expired")
                Color.RED
            }
            else -> {
                Log.d("LabelAdapter", "Label '${label.product_name}' is valid")
                Color.BLUE
            }
        }
        // Set the background color and store it as a tag
        holder.itemView.setBackgroundColor(backgroundColor)
        holder.itemView.setTag(R.id.original_background_color, backgroundColor)

        if (holder is NormalViewHolder) {
            holder.labelName.text = label.product_name
            holder.labelBatch.text = "Batch No: ${label.batch_no}"
            holder.labelPrepped.text = label.dates.getOrNull(0) ?: "N/A"
            holder.labelUseBy.text = label.dates.getOrNull(1) ?: "N/A"
            holder.itemView.findViewById<TextView>(R.id.empName).text = label.employee_name
        } else if (holder is DefrostedViewHolder) {
            holder.labelName.text = label.product_name
            holder.labelBatch.text = "Batch No: ${label.batch_no}"
            holder.labelDefrost.text = label.dates.getOrNull(0) ?: "N/A"
            holder.labelReadyToPrep.text = label.dates.getOrNull(1) ?: "N/A"
            holder.labelUseBy.text = label.dates.getOrNull(2) ?: "N/A"
            holder.itemView.findViewById<TextView>(R.id.empName).text = label.employee_name
        }

    }


    private fun isExpired(dates: List<String>): Boolean {
        val useByDate = dates.lastOrNull() ?: return false
        val formatter = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())

        // Clean date string by removing 'E.O.D' or any other non-date suffix
        val cleanedDate = Regex("""\b\d{2}/\d{2}/\d{2}\b""")
            .find(useByDate)?.value
            ?.let { "$it 23:59" } ?: "Invalid Date"


        return try {
            val useByTime = formatter.parse(cleanedDate)?.time ?: 0
            val currentTime = System.currentTimeMillis()

            Log.d("LabelAdapter", "Cleaned Use By Date: $cleanedDate | Parsed: $useByTime | Current: $currentTime")

            currentTime > useByTime
        } catch (e: Exception) {
            Log.e("LabelAdapter", "Error parsing Use By Date: $useByDate", e)
            false
        }
    }



    private fun isBeingDefrosted(dates: List<String>): Boolean {
        if (dates.size < 3) return false
        val formatter = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
        return try {
            val defrostTime = formatter.parse(dates[0])?.time ?: 0
            val readyToPrepTime = formatter.parse(dates[1])?.time ?: 0
            val currentTime = System.currentTimeMillis()
            currentTime in defrostTime..readyToPrepTime
        } catch (e: Exception) {
            false
        }
    }

}


