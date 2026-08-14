import { http } from '@/api/http'
import type { ApiResult } from '@/types/api'
import type { ContentDetail, ContentDraft, FeedItem, FeedQuery, FeedResult, SearchFeedResult } from '@/types/content'

export const getFeed = (query: FeedQuery) =>
  http.get<never, ApiResult<FeedResult>>('/log/list', { params: query })

export const getContentDetail = (id: number) =>
  http.get<never, ApiResult<ContentDetail>>(`/log/detail/${id}`)

export const likeContent = (id: number) =>
  http.post<never, ApiResult<null>>(`/log/like/${id}`)

export const unlikeContent = (id: number) =>
  http.delete<never, ApiResult<null>>(`/log/like/${id}`)

export const getEditableContent = (id: number) =>
  http.get<never, ApiResult<ContentDraft>>(`/log/edit/${id}`)

export const publishContent = (draft: ContentDraft) =>
  http.post<never, ApiResult<null>>('/log/release', draft)

export const updateContent = (id: number, draft: ContentDraft) =>
  http.put<never, ApiResult<null>>(`/log/update/${id}`, draft)

export const getLikedContents = (page: number, size = 10) =>
  http.get<never, ApiResult<FeedItem[]>>('/log/like/list', { params: { page, size } })

export const searchContents = (keyword: string, cursor?: string, size = 10) =>
  http.get<never, ApiResult<SearchFeedResult>>('/log/search', { params: { keyword, cursor, size } })
