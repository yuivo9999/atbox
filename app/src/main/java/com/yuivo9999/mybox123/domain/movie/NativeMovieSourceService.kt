package com.yuivo9999.mybox123.domain.movie

import com.yuivo9999.mybox123.data.movie.NativeMovieCategory
import com.yuivo9999.mybox123.data.movie.NativeMovieItem
import com.yuivo9999.mybox123.data.movie.StaticCatalogData

class NativeMovieSourceService {
    fun getCategories(sourceId: String): List<NativeMovieCategory> {
        return StaticCatalogData.categories
    }

    fun getList(
        sourceId: String,
        page: Int = 1,
        pageSize: Int = 30,
        categoryId: String? = null
    ): NativeMoviePage {
        val all = StaticCatalogData.movies
        val filtered = all.filter { movie ->
            categoryId == null || categoryId == "all" || when (categoryId) {
                "movie" -> movie.category == "电影"
                "series" -> movie.category == "电视剧"
                "anime" -> movie.category == "动漫"
                "variety" -> movie.category == "综艺"
                "short_drama" -> movie.category == "短剧"
                else -> movie.category.contains(categoryId)
            }
        }
        return NativeMoviePage(
            sourceId = sourceId,
            page = page,
            pageSize = pageSize,
            total = filtered.size.toLong(),
            items = filtered,
            httpStatus = 200
        )
    }

    fun search(
        sourceId: String,
        keyword: String,
        page: Int = 1,
        pageSize: Int = 30,
        categoryId: String? = null
    ): NativeMoviePage {
        val all = StaticCatalogData.movies
        val filtered = all.filter { movie ->
            movie.title.contains(keyword, ignoreCase = true) ||
            movie.category.contains(keyword, ignoreCase = true) ||
            movie.description.contains(keyword, ignoreCase = true)
        }
        return NativeMoviePage(
            sourceId = sourceId,
            page = page,
            pageSize = pageSize,
            total = filtered.size.toLong(),
            items = filtered,
            httpStatus = 200
        )
    }

    fun getDetail(sourceId: String, sourceItemId: String): NativeMovieItem? {
        return StaticCatalogData.movies.firstOrNull {
            it.contentId == sourceItemId || it.sourceItemId == sourceItemId || it.title.equals(sourceItemId, ignoreCase = true)
        }
    }
}
