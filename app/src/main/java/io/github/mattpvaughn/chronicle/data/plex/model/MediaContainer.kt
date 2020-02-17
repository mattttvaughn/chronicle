/*
 * Copyright (C) 2016 Simon Norberg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.mattpvaughn.chronicle.data.plex.model

import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(strict = false)
data class MediaContainer @JvmOverloads constructor(
    @field:Attribute(required = false)
    var playQueueSelectedItemID: Long? = null,
    @field:ElementList(inline = true, required = false, entry = "Directory")
    var directories: List<Directory> = ArrayList(),
    @field:ElementList(inline = true, required = false, entry = "Track")
    var tracks: List<TrackPlexModel> = ArrayList(),
    @field:ElementList(inline = true, required = false, entry = "Device")
    var devices: List<Device>? = null,
    @field:Attribute(required = false)
    var size: Long? = 0
)

@Root(strict = false)
data class Genre @JvmOverloads constructor(
    @field:Attribute(required = false)
    var tag: String = ""
) {
    override fun toString(): String {
        return tag
    }
}

fun MediaContainer.asAudiobooks(): List<Audiobook> {
    return directories.map { Audiobook.from(it) }
}

fun MediaContainer.asTrackList(): List<MediaItemTrack> {
    return tracks.asMediaItemTracks()
}
