package cz.vsb.bra0174.osmz.httpserver.view

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.ObservableList
import androidx.recyclerview.widget.RecyclerView
import cz.vsb.bra0174.osmz.httpserver.databinding.LogEntryBinding
import cz.vsb.bra0174.osmz.httpserver.model.LogEntry

class LogEntryAdapter(
    private val context: Activity,
    private val entries: ObservableList<LogEntry>
) : RecyclerView.Adapter<LogEntryAdapter.LogEntryViewHolder>() {

    init {
        entries.addOnListChangedCallback(
            object : ObservableList.OnListChangedCallback<ObservableList<LogEntry>>() {
                override fun onChanged(sender: ObservableList<LogEntry>?) =
                    notifyDataSetChanged()

                override fun onItemRangeRemoved(
                    sender: ObservableList<LogEntry>?, positionStart: Int, itemCount: Int
                ) = notifyItemRangeRemoved(positionStart, itemCount)

                override fun onItemRangeMoved(
                    sender: ObservableList<LogEntry>?,
                    fromPosition: Int, toPosition: Int, itemCount: Int
                ) {
                    for ((origin, destination) in
                    0.until(itemCount).map { fromPosition + it to toPosition + it }) {
                        notifyItemMoved(origin, destination)
                    }
                }

                override fun onItemRangeInserted(
                    sender: ObservableList<LogEntry>?, positionStart: Int, itemCount: Int
                ) = notifyItemRangeInserted(positionStart, itemCount)

                override fun onItemRangeChanged(
                    sender: ObservableList<LogEntry>?, positionStart: Int, itemCount: Int
                ) = notifyItemRangeChanged(positionStart, itemCount)
            }
        )
    }

    class LogEntryViewHolder(private val binding: LogEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(logEntry: LogEntry) =
            binding.apply { this.logEntry = logEntry }.run { executePendingBindings() }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogEntryViewHolder =
        LogEntryViewHolder(
            LogEntryBinding.inflate(
                LayoutInflater.from(context),
                parent,
                false
            )
        )

    override fun getItemCount() = entries.size

    override fun onBindViewHolder(holder: LogEntryViewHolder, position: Int) =
        holder.bind(entries[position])
}