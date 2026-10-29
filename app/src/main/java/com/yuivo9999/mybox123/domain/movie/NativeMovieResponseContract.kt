package com.yuivo9999.mybox123.domain.movie

import com.yuivo9999.mybox123.data.movie.NativeMovieCategory
import com.yuivo9999.mybox123.data.movie.NativeMovieItem

typealias NativeMovieItem = com.yuivo9999.mybox123.data.movie.NativeMovieItem
typealias NativeMovieCategory = com.yuivo9999.mybox123.data.movie.NativeMovieCategory

data class NativeMoviePage(
    val sourceId: String,
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val items: List<NativeMovieItem>,
    val httpStatus: Int = 200,
    val rawJson: String = "{}"
)
