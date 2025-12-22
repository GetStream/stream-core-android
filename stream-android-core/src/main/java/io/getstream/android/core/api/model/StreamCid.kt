package io.getstream.android.core.api.model

import io.getstream.android.core.annotations.StreamPublishedApi

@ConsistentCopyVisibility
@StreamPublishedApi
public data class StreamCid private constructor(
    public val type: String,
    public val id: String,
) {

    public companion object {

        public fun parse(cid: String) : StreamCid {
            require(cid.isNotEmpty())
            val split = cid.split(":")
            require(split.size == 2)
            return StreamCid(split[0], split[1])
        }

        public fun fromTypeAndId(type: String, id: String) : StreamCid {
            require(type.isNotEmpty())
            require(id.isNotEmpty())
            return StreamCid(type, id)
        }
    }

    public fun formatted(): String = "$type:$id"
}