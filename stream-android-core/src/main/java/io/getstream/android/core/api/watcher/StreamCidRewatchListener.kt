package io.getstream.android.core.api.watcher

import io.getstream.android.core.annotations.StreamInternalApi
import io.getstream.android.core.api.model.StreamCid

@StreamInternalApi
public fun interface StreamCidRewatchListener {

    public fun onRewatch(list: List<StreamCid>)
}
