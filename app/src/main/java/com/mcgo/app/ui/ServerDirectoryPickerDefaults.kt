package com.mcgo.app.ui

import android.net.Uri
import android.provider.DocumentsContract

internal const val DefaultServerDirectoryName = "MCGO"

private const val ExternalStorageProviderAuthority = "com.android.externalstorage.documents"

internal fun defaultServerDirectoryInitialUri(): Uri =
    DocumentsContract.buildTreeDocumentUri(
        ExternalStorageProviderAuthority,
        "primary:$DefaultServerDirectoryName",
    )

internal fun serverDirectoryPickerInitialUri(currentServerDirectoryUri: String?): Uri =
    currentServerDirectoryUri
        ?.takeIf(String::isNotBlank)
        ?.let(Uri::parse)
        ?: defaultServerDirectoryInitialUri()
