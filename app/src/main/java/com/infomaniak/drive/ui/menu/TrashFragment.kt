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
package com.infomaniak.drive.ui.menu

import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.navigation.navGraphViewModels
import com.google.android.material.button.MaterialButton
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.File.SortTypeUsage
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.multiSelect.TrashMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MatomoUtils.trackTrashEvent
import com.infomaniak.drive.utils.Utils.ROOT_ID
import kotlinx.android.synthetic.main.fragment_file_list.*

class TrashFragment : FileSubTypeListFragment() {

    val trashViewModel: TrashViewModel by navGraphViewModels(R.id.trashFragment)

    override var enabledMultiSelectMode: Boolean = true
    override var sortTypeUsage = SortTypeUsage.TRASH

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fileListViewModel.sortType = SortType.RECENT_TRASHED
        sortFiles = SortFiles()
        downloadFiles =
            DownloadFiles(
                if (folderId != ROOT_ID) File(id = folderId, name = folderName, driveId = AccountUtils.currentDriveId) else null
            )
        setNoFilesLayout = SetNoFilesLayout()

        super.onViewCreated(view, savedInstanceState)

        toolbar.setContentInsetsRelative(0, 0)

        emptyTrash.setupEmptyTrashButton()

        if (folderId == ROOT_ID) collapsingToolbarLayout.title = getString(R.string.trashTitle)

        fileAdapter.apply {
            showShareFileButton = false
            onFileClicked = { file ->
                trashViewModel.cancelTrashFileJob()
                if (file.isFolder()) {
                    safeNavigate(TrashFragmentDirections.actionTrashFragmentSelf(file.id, file.name))
                } else {
                    showTrashedFileActions(file)
                }
            }
            onMenuClicked = { file -> showTrashedFileActions(file) }
        }

        trashViewModel.removeFileId.observe(viewLifecycleOwner) { fileToRemove ->
            removeFileFromAdapter(fileToRemove)
        }

        multiSelectLayout?.apply {
            emptyTrashButton.isVisible = true
            selectAllButton.isInvisible = true
            moveButtonMultiSelect.isInvisible = true
            deleteButtonMultiSelect.isInvisible = true
        }
    }

    private fun MaterialButton.setupEmptyTrashButton() {
        isVisible = true
        setOnClickListener {
            Utils.createConfirmation(
                context = requireContext(),
                title = getString(R.string.buttonEmptyTrash),
                message = getString(R.string.modalEmptyTrashDescription),
                isDeletion = true,
                autoDismiss = false,
            ) { dialog ->
                trackTrashEvent("emptyTrash")
                trashViewModel.emptyTrash(AccountUtils.currentDriveId).observe(viewLifecycleOwner) { apiResponse ->
                    dialog.dismiss()
                    if (apiResponse.data == true) {
                        Utils.showSnackbar(requireView(), R.string.snackbarEmptyTrashConfirmation)
                        onRefresh()
                    } else {
                        requireActivity().showSnackbar(apiResponse.translateError())
                    }
                }
            }
        }
    }

    override fun onMenuButtonClicked() {
        val (fileIds, onlyFolders, onlyFavorite, onlyOffline, isAllSelected) = multiSelectManager.getMenuNavArgs()
        TrashMultiSelectActionsBottomSheetDialog().apply {
            arguments = MultiSelectActionsBottomSheetDialogArgs(
                fileIds = fileIds,
                onlyFolders = onlyFolders,
                onlyFavorite = onlyFavorite,
                onlyOffline = onlyOffline,
                isAllSelected = isAllSelected
            ).toBundle()
        }.show(childFragmentManager, "ActionTrashMultiSelectBottomSheetDialog")
    }

    private fun showTrashedFileActions(file: File) {
        trashViewModel.selectedFile.value = file
        safeNavigate(R.id.trashedFileActionsBottomSheetDialog)
    }

    private fun removeFileFromAdapter(fileId: Int) {
        fileAdapter.deleteByFileId(fileId)
        noFilesLayout.toggleVisibility(fileAdapter.getFiles().isEmpty())
    }

    companion object {
        const val MATOMO_CATEGORY = "trashFileAction"
    }

    private inner class SortFiles : () -> Unit {
        override fun invoke() {
            getBackNavigationResult<SortType>(SORT_TYPE_OPTION_KEY) { newSortType ->
                fileListViewModel.sortType = when (newSortType) {
                    SortType.OLDER -> SortType.OLDER_TRASHED
                    SortType.RECENT -> SortType.RECENT_TRASHED
                    else -> newSortType
                }
                sortButton.setText(fileListViewModel.sortType.translation)
                downloadFiles(true, true)
            }
        }
    }

    private inner class SetNoFilesLayout : () -> Unit {
        override fun invoke() {
            noFilesLayout.setup(
                icon = R.drawable.ic_delete,
                title = R.string.trashNoFile,
                initialListView = fileRecyclerView,
            )
        }
    }

    private inner class DownloadFiles() : (Boolean, Boolean) -> Unit {

        private var folder: File? = null

        constructor(folder: File?) : this() {
            this.folder = folder
        }

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            if (ignoreCache) fileAdapter.setFiles(arrayListOf())
            showLoadingTimer.start()
            fileAdapter.isComplete = false

            folder?.let { folder ->
                trashViewModel.getTrashFile(folder, fileListViewModel.sortType).observe(viewLifecycleOwner) { result ->
                    result?.apply {
                        populateFileList(
                            files = result.files,
                            isComplete = isComplete,
                            forceClean = result.page == 1,
                            isNewSort = isNewSort,
                        )
                    }
                }
            } ?: run {
                trashViewModel.getDriveTrash(AccountUtils.currentDriveId, fileListViewModel.sortType)
                    .observe(viewLifecycleOwner) { result ->
                        populateFileList(
                            files = result?.files ?: ArrayList(),
                            isComplete = result?.isComplete ?: true,
                            forceClean = result?.page == 1,
                            isNewSort = isNewSort,
                        )
                    }
            }
        }
    }
}
