package io.github.mattpvaughn.chronicle.data.plex.model

import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Root

@Root(strict = false)
data class User @JvmOverloads constructor(@field:Attribute var authToken: String = "")
