package org.fossify.gallery.dialogs

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.gallery.databinding.DialogChangeViewTypeBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.VIEW_TYPE_MOSAIC
import org.fossify.gallery.helpers.VIEW_TYPE_WATERFALL

class ChangeViewTypeDialog(val activity: BaseSimpleActivity, val fromFoldersView: Boolean, val path: String = "", val callback: () -> Unit) {
    private val binding = DialogChangeViewTypeBinding.inflate(activity.layoutInflater)
    private var config = activity.config
    private var pathToUse = if (path.isEmpty()) SHOW_ALL else path

    init {
        binding.apply {
            // waterfall and mosaic layouts are only available for the media (files) view
            changeViewTypeDialogRadioWaterfall.beGoneIf(fromFoldersView)
            changeViewTypeDialogRadioMosaic.beGoneIf(fromFoldersView)

            val currViewType = if (fromFoldersView) {
                config.viewTypeFolders
            } else {
                config.getFolderViewType(pathToUse)
            }

            val viewToCheck = when (currViewType) {
                VIEW_TYPE_GRID -> changeViewTypeDialogRadioGrid.id
                VIEW_TYPE_WATERFALL -> changeViewTypeDialogRadioWaterfall.id
                VIEW_TYPE_MOSAIC -> changeViewTypeDialogRadioMosaic.id
                else -> changeViewTypeDialogRadioList.id
            }

            changeViewTypeDialogRadio.check(viewToCheck)
            changeViewTypeDialogGroupDirectSubfolders.apply {
                beVisibleIf(fromFoldersView)
                isChecked = config.groupDirectSubfolders
            }

            changeViewTypeDialogUseForThisFolder.apply {
                beVisibleIf(!fromFoldersView)
                isChecked = config.hasCustomViewType(pathToUse)
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }

    private fun dialogConfirmed() {
        val viewType = when (binding.changeViewTypeDialogRadio.checkedRadioButtonId) {
            binding.changeViewTypeDialogRadioGrid.id -> VIEW_TYPE_GRID
            binding.changeViewTypeDialogRadioWaterfall.id -> VIEW_TYPE_WATERFALL
            binding.changeViewTypeDialogRadioMosaic.id -> VIEW_TYPE_MOSAIC
            else -> VIEW_TYPE_LIST
        }

        if (fromFoldersView) {
            config.viewTypeFolders = viewType
            config.groupDirectSubfolders = binding.changeViewTypeDialogGroupDirectSubfolders.isChecked
        } else {
            if (binding.changeViewTypeDialogUseForThisFolder.isChecked) {
                config.saveFolderViewType(pathToUse, viewType)
            } else {
                config.removeFolderViewType(pathToUse)
                config.viewTypeFiles = viewType
            }
        }


        callback()
    }
}
