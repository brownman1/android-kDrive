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
package com.infomaniak.drive.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.utils.getName
import kotlinx.android.synthetic.main.view_categories_container.view.*

class CategoriesContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        inflate(context, R.layout.view_categories_container, this)
    }

    fun setup(categories: List<Category>, canPutCategoryOnFile: Boolean, onClicked: () -> Unit) {
        setCategoryTitle(canPutCategoryOnFile, categories)
        setStates(canPutCategoryOnFile, categories)
        setListeners(canPutCategoryOnFile, onClicked)
        setCategories(categories, onClicked)
    }

    private fun setCategoryTitle(canPutCategoryOnFile: Boolean, categories: List<Category>) {
        categoryTitle.setText(
            if (canPutCategoryOnFile) {
                if (categories.isEmpty()) R.string.addCategoriesTitle else R.string.manageCategoriesTitle
            } else R.string.categoriesFilterTitle
        )
    }

    private fun setStates(canPutCategoryOnFile: Boolean, categories: List<Category>) {
        categorySwitch.isVisible = canPutCategoryOnFile
        categoriesGroup.isVisible = categories.isNotEmpty()
    }

    private fun setListeners(canPutCategoryOnFile: Boolean, onClicked: () -> Unit) {
        if (canPutCategoryOnFile) {
            categoriesContainerView.setOnClickListener { onClicked() }
        }
    }

    private fun setCategories(categories: List<Category>, onClicked: () -> Unit) {
        if (categories.isNotEmpty()) {
            with(categoriesGroup) {
                removeAllViews()
                categories.forEach { category ->
                    addView(
                        Chip(context).apply {
                            text = category.getName(context)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f)
                            setTextColor(ContextCompat.getColor(context, R.color.white))
                            typeface = ResourcesCompat.getFont(context, R.font.suisseintl_medium)
                            chipBackgroundColor = ColorStateList.valueOf(category.color.toColorInt())
                            setOnClickListener { onClicked() }
                        }
                    )
                }
            }
        }
    }
}