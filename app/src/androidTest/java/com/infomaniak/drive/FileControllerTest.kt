package com.infomaniak.drive

import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRepository.getFileListForFolder
import com.infomaniak.drive.data.api.ApiRepository.getLastModifiedFiles
import com.infomaniak.drive.data.api.ApiRepository.renameFile
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.cache.FileController.FAVORITES_FILE_ID
import com.infomaniak.drive.data.cache.FileController.getFilesFromCacheOrDownload
import com.infomaniak.drive.data.cache.FileController.getMySharedFiles
import com.infomaniak.drive.data.cache.FileController.removeFile
import com.infomaniak.drive.data.cache.FileController.saveFavoritesFiles
import com.infomaniak.drive.data.cache.FileController.searchFiles
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.ApiTestUtils.assertApiResponse
import com.infomaniak.drive.utils.ApiTestUtils.createFileForTest
import com.infomaniak.drive.utils.ApiTestUtils.deleteTestFile
import com.infomaniak.drive.utils.Env
import com.infomaniak.drive.utils.Utils
import io.realm.Realm
import kotlinx.android.parcel.RawValue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

/**
 * File Controllers testing class
 */
class FileControllerTest : KDriveTest() {

    private lateinit var realm: Realm

    @BeforeEach
    @Throws(Exception::class)
    fun setUp() {
        realm = Realm.getInstance(getConfig())
    }

    @AfterEach
    @Throws(Exception::class)
    fun tearDown() {
        if (!realm.isClosed) realm.close()
        Realm.deleteRealm(getConfig())
    }

    @Test
    fun createTestFolder() {
        val folderName = "TestFolder"
        // Create a folder under root
        with(ApiRepository.createFolder(okHttpClient, userDrive.driveId, Utils.ROOT_ID, folderName, true)) {
            assertApiResponse(this)
            assertEquals(folderName, data?.name, "The name should correspond")
            // Delete the test folder
            deleteTestFile(data!!)
        }
    }

    @Test
    fun getRootFiles_CanGetRemoteSavedFilesFromRealm() {
        val remoteResult = getAndSaveRemoteRootFiles()

        // We check that we get the data saved in realm
        val localResult = getLocalRootFiles()
        assertNotNull(localResult, "local root files cannot be null")
        assertFalse(localResult?.second.isNullOrEmpty(), "local root files cannot be empty")
        assertTrue(
            remoteResult?.second?.size == localResult?.second?.size,
            "the size of the local and remote files must be identical",
        )
    }

    @Test
    fun deleteAddedFileFromAPI() {
        // Create a file
        val remoteFile = createAndStoreOfficeFile()
        val order = File.SortType.NAME_AZ

        // Delete the file
        deleteTestFile(remoteFile)

        // Search the deleted file
        with(ApiRepository.searchFiles(userDrive.driveId, remoteFile.name, order.order, order.orderBy, 1)) {
            assertTrue(isSuccess(), "Api response must be a success")
        assertTrue(data.isNullOrEmpty(), "Founded files should be empty")
        }
    }

    @Test
    fun getFavoriteFiles_CanGetRemoteSavedFilesFromRealm() {
        // Create a test file and store it in favorite
        val remoteFile = createAndStoreOfficeFile()
        ApiRepository.postFavoriteFile(remoteFile)
        // Get remote favorite files
        val remoteResult = ApiRepository.getFavoriteFiles(Env.DRIVE_ID, File.SortType.NAME_AZ, 1)
        assertTrue(remoteResult.isSuccess(), "get favorite files request must pass successfully")
        assertFalse(remoteResult.data.isNullOrEmpty(), "remote favorite files cannot be empty ")

        // Save remote favorite files in realm db test
        val remoteFavoriteFiles = remoteResult.data!!
        saveFavoritesFiles(remoteFavoriteFiles, realm = realm)

        // Get saved favorite files and compare with remote files
        val localFavoriteFiles =
            getFilesFromCacheOrDownload(
                parentId = FAVORITES_FILE_ID,
                page = 1,
                ignoreCache = false,
                userDrive = userDrive,
                customRealm = realm,
                ignoreCloud = true
            )
        val parent = localFavoriteFiles?.first
        val files = localFavoriteFiles?.second
        assertNotNull(localFavoriteFiles, "local favorite files cannot be null")
        assertFalse(files.isNullOrEmpty(), "local favorite files cannot be empty")

        // Compare remote files and local files
        assertTrue(parent?.id == FAVORITES_FILE_ID)
        assertTrue(files?.size == remoteFavoriteFiles.size, "local files and remote files cannot be different")
        // Delete Test file
        deleteTestFile(remoteFile)
    }

    @Test
    fun getMySharedFiles_CanGetRemoteSavedFilesFromRealm() = runBlocking {
        // Get remote files
        val remoteFiles = arrayListOf<File>()
        var isCompletedRemoteFiles = false
        getMySharedFiles(userDrive, File.SortType.NAME_AZ) { files, isComplete ->
            remoteFiles.addAll(files)
            isCompletedRemoteFiles = isComplete
        }

        assertNotNull(remoteFiles, "remote my shares data cannot be null")
        assertTrue(isCompletedRemoteFiles, "remote my shares data must be complete")

        // Get local files
        val localFiles = arrayListOf<File>()
        var isCompletedLocaleFiles = false
        getMySharedFiles(userDrive, File.SortType.NAME_AZ, 1, true) { files, isComplete ->
            localFiles.addAll(files)
            isCompletedLocaleFiles = isComplete
        }

        assertNotNull(localFiles, "local my shares data cannot be null")
        assertTrue(isCompletedLocaleFiles, "local my shares data must be complete")

        // Compare remote files and local files
        assertTrue(remoteFiles.size == localFiles.size, "local files and remote files cannot be different")
    }

    @Test
    fun getPictures_CanGetRemoteSavedFilesFromRealm() {
        // Get remote pictures
        val apiResponseData = ApiRepository.getLastPictures(Env.DRIVE_ID, 1).let {
            assertTrue(it.isSuccess(), "get pictures request must pass")
        	assertFalse(it.data.isNullOrEmpty(), "get pictures response data cannot be null or empty")
            it.data!!
        }

        // Store remote pictures
        FileController.storePicturesDrive(apiResponseData, customRealm = realm)

        // Get saved remote files from realm
        with(FileController.getPicturesDrive(realm)) {
            assertTrue(isNotEmpty(), "local pictures cannot be empty ")

            // Compare remote pictures with local pictures
            assertTrue(size == apiResponseData.size, "remote files and local files are different")        }

    }

    @Test
    fun getOfflineFiles() {
        // Create offline test file
        val file = createAndStoreOfficeFile { remoteFile ->
            remoteFile.isOffline = true
        }

        // Get offline files
        with(FileController.getOfflineFiles(null, customRealm = realm)) {
            Assert.assertTrue("local offline files cannot be null", isNotEmpty())
            Assert.assertNotNull("stored file was not found in realm db", firstOrNull { it.id == file.id })
        }

        // Delete remote offline test files
        deleteTestFile(file)

    }

    @Test
    fun searchFile_FromRealm_IsCorrect() {
        val file = createAndStoreOfficeFile()
        with(searchFiles(file.name, File.SortType.NAME_AZ, customRealm = realm)) {
            assertTrue(isNotEmpty(), "the list of search results must contain results")
            assertTrue(first().name.contains(file.name), "the search result must match")
        }
        deleteTestFile(file)
    }

    @Test
    fun removeFileCascade_IsCorrect() {
        getAndSaveRemoteRootFiles()
        with(getLocalRootFiles()) {
            // Check if remote files are stored
            assertNotNull(this, "local root files cannot be null")
        	assertFalse(this?.second.isNullOrEmpty(), "local root files cannot be empty")
        }

        // Delete root files
        removeFile(Utils.ROOT_ID, customRealm = realm)
        // Check that all root files are deleted
        with(realm.where(File::class.java).findAll()) {
            assertTrue(isNullOrEmpty(), "Realm must not contain any files")
        }
    }

    @Test
    fun getTestFileListForFolder() {
        // Get the file list of root folder
        with(getFileListForFolder(okHttpClient, userDrive.driveId, Utils.ROOT_ID, order = File.SortType.NAME_AZ)) {
            assertApiResponse(this)
            // Use non null assertion because data nullability has been checked in assertApiResponse()
            assertTrue(data!!.children.isNotEmpty(), "Root folder should contains files")
        }
    }

    @Test
    fun renameTestFile() {
        val newName = "renamed file"
        val file = createFileForTest()
        assertApiResponse(renameFile(file, newName))
        with(getLastModifiedFiles(userDrive.driveId)) {
            assertApiResponse(this)
            assertEquals(file.id, data!!.first().id, "Last modified file should have id ${file.id}")
            assertEquals(newName, data!!.first().name, "File should be named '$newName'")
        }
        deleteTestFile(file)

    }

    private fun getAndSaveRemoteRootFiles(): Pair<File, ArrayList<File>>? {
        // Get and save remote root files in realm db test
        return getFilesFromCacheOrDownload(Utils.ROOT_ID, 1, true, userDrive = userDrive, customRealm = realm).also {
            assertNotNull(it, "remote root files cannot be null")
        }
    }

    private fun getLocalRootFiles() =
        getFilesFromCacheOrDownload(Utils.ROOT_ID, 1, false, userDrive = userDrive, customRealm = realm)

    private fun createAndStoreOfficeFile(transaction: ((remoteFile: File) -> Unit)? = null): @RawValue File {
        val remoteFile = createFileForTest()
        // Save the file as offline file
        transaction?.invoke(remoteFile)
        realm.executeTransaction { realm.insert(remoteFile) }
        return remoteFile
    }
}
