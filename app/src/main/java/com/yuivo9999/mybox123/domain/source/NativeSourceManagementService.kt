package com.yuivo9999.mybox123.domain.source

import com.yuivo9999.mybox123.data.source.NativeContentSource
import com.yuivo9999.mybox123.data.source.NativeContentSourceRepository

class NativeSourceManagementService(private val repository: NativeContentSourceRepository) {
    fun getSources(): List<NativeContentSource> = repository.getAll()
    fun saveSources(sources: List<NativeContentSource>) = repository.saveAll(sources)
}
