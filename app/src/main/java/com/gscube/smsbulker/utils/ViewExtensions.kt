package com.gscube.smsbulker.utils

import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

fun Fragment.showErrorSnackbar(message: String, duration: Int = Snackbar.LENGTH_LONG) {
    Snackbar.make(requireView(), message, duration)
        .setBackgroundTint(requireContext().getColor(android.R.color.holo_red_dark))
        .setTextColor(requireContext().getColor(android.R.color.white))
        .show()
}

fun Fragment.showSuccessSnackbar(message: String, duration: Int = Snackbar.LENGTH_LONG) {
    Snackbar.make(requireView(), message, duration)
        .setBackgroundTint(requireContext().getColor(android.R.color.holo_green_dark))
        .setTextColor(requireContext().getColor(android.R.color.white))
        .show()
}

fun Fragment.showInfoSnackbar(
    message: String,
    actionText: String? = null,
    action: ((View) -> Unit)? = null,
    duration: Int = Snackbar.LENGTH_LONG
) {
    Snackbar.make(requireView(), message, duration).apply {
        if (actionText != null && action != null) {
            setAction(actionText, action)
        }
    }.show()
}

fun Fragment.showLoadingSnackbar(message: String): Snackbar {
    return Snackbar.make(requireView(), message, Snackbar.LENGTH_INDEFINITE)
        .setBackgroundTint(requireContext().getColor(android.R.color.darker_gray))
        .setTextColor(requireContext().getColor(android.R.color.white))
        .apply { show() }
} 