/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.drive.ui.fileList

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.infomaniak.drive.R
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.item_search_result.view.*

class RecentSearchesAdapter(
    private val onSearchClicked: (search: String) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    private var searches = arrayListOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false))

    override fun getItemCount() = searches.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with(holder.itemView.searchResultCard) {
        val search = searches[position]
        setLayouts(position)
        setData(search)
        setListeners(search)
    }

    private fun MaterialCardView.setLayouts(position: Int) {
        var topCornerRadius = 0.0f
        var bottomCornerRadius = 0.0f
        if (position == 0) topCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)
        if (position == itemCount - 1) bottomCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)

        shapeAppearanceModel = shapeAppearanceModel
            .toBuilder()
            .setTopLeftCorner(CornerFamily.ROUNDED, topCornerRadius)
            .setTopRightCorner(CornerFamily.ROUNDED, topCornerRadius)
            .setBottomLeftCorner(CornerFamily.ROUNDED, bottomCornerRadius)
            .setBottomRightCorner(CornerFamily.ROUNDED, bottomCornerRadius)
            .build()
    }

    private fun MaterialCardView.setData(search: String) {
        searchResultText.text = search
    }

    private fun MaterialCardView.setListeners(search: String) {
        setOnClickListener { onSearchClicked(search) }
    }

    fun setItems(newSearches: List<String>) {
        searches = ArrayList(newSearches)
        notifyItemRangeChanged(0, itemCount)
    }
}
