package com.itangcent.idea.utils

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.util.io.FileUtil
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.FileUtils
import com.itangcent.intellij.util.ToolUtils
import java.awt.HeadlessException
import java.io.File
import java.nio.charset.Charset
import kotlin.text.Charsets

@Singleton
class DefaultFileSaveHelper : FileSaveHelper {

    @Inject(optional = true)
    @Named("file.save.default")
    private val defaultExportedFile: String? = null

    @Inject(optional = true)
    @Named("file.save.last.location.key")
    private val lastImportedLocation: String? = null

    @Inject
    private val actionContext: ActionContext? = null

    override fun saveOrCopy(
        content: String?,
        onCopy: () -> Unit,
        onSaveSuccess: (String) -> Unit,
        onSaveFailed: (String?) -> Unit,
    ) {
        saveOrCopy(content, Charsets.UTF_8, onCopy, onSaveSuccess, onSaveFailed)
    }

    override fun saveOrCopy(
        content: String?,
        charset: Charset,
        onCopy: () -> Unit,
        onSaveSuccess: (String) -> Unit,
        onSaveFailed: (String?) -> Unit,
    ) {
        if (content == null) return

        saveOrCopy(content, charset, {
            getDefaultExportedFile()
        }, onCopy, onSaveSuccess, onSaveFailed)
    }

    override fun saveOrCopy(
        content: String?,
        charset: Charset,
        defaultFileName: () -> String?,
        onCopy: () -> Unit,
        onSaveSuccess: (String) -> Unit,
        onSaveFailed: (String?) -> Unit,
    ) {
        if (content == null) return

        IdeaFileChooserHelper.create(actionContext!!, FileChooserDescriptorFactory
            .createSingleFileOrFolderDescriptor()
            .withTitle("Export Location")
            .withDescription("Choose directory to export api to")
            .withHideIgnored(false))
            .lastSelectedLocation(getLastImportedLocation())
            .selectFile({ file ->
                if (file.isDirectory) {
                    try {
                        val defaultFile = defaultFileName() ?: getDefaultExportedFile()
                        var filePath = "${file.path}${File.separator}$defaultFile"
                        filePath = availablePath(filePath)
                        FileUtils.forceSave(filePath, content.toByteArray(charset))
                        onSaveSuccess(filePath)
                    } catch (e: Exception) {
                        onSaveFailed(e.message)
                        actionContext.runAsync {
                            copyAndLog(content, onCopy)
                        }
                    }
                } else {
                    try {
                        FileUtils.forceSave(file, content.toByteArray(charset))
                        onSaveSuccess(file.path)
                    } catch (e: Exception) {
                        onSaveFailed(e.message)
                        actionContext.runAsync {
                            copyAndLog(content, onCopy)
                        }
                    }
                }
            }, {
                actionContext.runAsync {
                    copyAndLog(content, onCopy)
                }
            })
    }

    override fun saveBytes(
        content: (String) -> ByteArray,
        defaultFileName: () -> String?,
        onSaveSuccess: () -> Unit,
        onSaveFailed: (String?) -> Unit,
        onSaveCancel: () -> Unit,
    ) {
        IdeaFileChooserHelper.create(actionContext!!, FileChooserDescriptorFactory
            .createSingleFileOrFolderDescriptor()
            .withTitle("Select Location")
            .withDescription("Choose folder/file to save")
            .withHideIgnored(false))
            .lastSelectedLocation(getLastImportedLocation())
            .selectFile({ file ->
                if (file.isDirectory) {
                    try {
                        val defaultFile = defaultFileName() ?: "untitled"
                        var path = "${file.path}${File.separator}$defaultFile"
                        path = availablePath(path)

                        FileUtils.forceSave(path, content(path))
                        onSaveSuccess()
                    } catch (e: Exception) {
                        onSaveFailed(e.message)
                    }
                } else {
                    try {
                        FileUtils.forceSave(file, content(file.path))
                        onSaveSuccess()
                    } catch (e: Exception) {
                        onSaveFailed(e.message)
                    }
                }
            }, {
                onSaveCancel()
            })
    }

    private fun availablePath(path: String): String {
        try {
            if (FileUtil.exists(path)) {
                var index = 1
                var pathWithIndex: String?
                while (true) {
                    pathWithIndex = "$path-$index"
                    if (!FileUtil.exists(pathWithIndex)) {
                        break
                    }
                    ++index
                }
                pathWithIndex?.let { return it }
            }
        } catch (e: Exception) {
        }
        return path
    }

    private fun copyAndLog(info: String, onCopy: () -> Unit) {
        val logger: Logger = ActionContext.getContext()!!.instance(Logger::class)
        try {
            ToolUtils.copy2Clipboard(info)
        } catch (e: HeadlessException) {
        }
        onCopy()
        if (info.length > 10000) {
            logger.info("Api data is too lager to show in console!")
        } else {
            logger.log(info)
        }
    }

    private fun getDefaultExportedFile(): String {
        return defaultExportedFile ?: "untitled"
    }

    private fun getLastImportedLocation(): String {
        return lastImportedLocation ?: "com.itangcent.api.export.path"
    }
}