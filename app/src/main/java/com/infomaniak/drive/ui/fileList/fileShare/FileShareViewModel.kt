/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileList.fileShare

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.utils.AccountUtils
import kotlinx.coroutines.Dispatchers

class FileShareViewModel : ViewModel() {

    val currentFile = MutableLiveData<File>()
    val availableShareableItems = MutableLiveData<List<Shareable>>()

    fun fetchCurrentFile(fileId: Int) = liveData(Dispatchers.IO) {
        emit(
            FileController.getFileById(fileId)
                ?: ApiRepository.getFileDetails(File(id = fileId, driveId = AccountUtils.currentDriveId)).data
        )
    }

    fun postFileShareCheck(file: File, body: Map<String, Any>) = liveData(Dispatchers.IO) {
        emit(ApiRepository.postFileShareCheck(file, body))
    }

    fun postFileShare(file: File, body: Map<String, Any?>) = liveData(Dispatchers.IO) {
        emit(ApiRepository.addMultiAccess(file, body))
    }

    fun editFileShareLink(file: File, shareLink: ShareLink) = liveData(Dispatchers.IO) {
        val body = ShareLink.ShareLinkSettings(
            right = shareLink.newRight,
            canDownload = shareLink.capabilities?.canDownload,
            canComment = shareLink.capabilities?.canComment,
            canSeeInfo = shareLink.capabilities?.canSeeInfo,
        )

        if (AccountUtils.getCurrentDrive()?.pack != Drive.DrivePack.FREE.value) {
            body.validUntil = shareLink.validUntil
        }

        when {
            shareLink.newPassword.isNullOrBlank() -> {
                if (shareLink.right == ShareLink.ShareLinkFilePermission.PASSWORD) body.right = null
            }
            else -> body.password = shareLink.newPassword
        }

        emit(ApiRepository.updateShareLink(file, body))
    }
}
