package io.wsign.signing

import androidx.core.content.FileProvider

/**
 * Shares photos taken for an ID attachment with the camera app. A subclass, so it never clashes with a
 * `FileProvider` declared by the host app.
 */
public class WsignSigningFileProvider : FileProvider(R.xml.wsign_signing_file_paths)
