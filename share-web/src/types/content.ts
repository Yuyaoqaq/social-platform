export interface FeedItem {
  id: number
  picurls: string[]
  title: string
  authorAvatar: string
  love: number
  author: string
  tags?: string[]
  isLiked?: boolean
  createTime?: string
}

export interface FeedResult {
  list: FeedItem[]
  nextCursor: number | null
}

export interface SearchFeedResult {
  list: FeedItem[]
  nextCursor: string | null
}

export interface FeedQuery {
  cursor?: number
  size?: number
  author?: string
  tags?: string[]
}

export interface ContentDetail {
  picurls: string[]
  title: string
  avatarurl: string
  love: number
  author: string
  info: string
  createTime: string
  updateTime?: string
  isLiked?: boolean
}

export interface ContentDraft {
  picurls: string[]
  title: string
  info: string
  author: string
  love: number
}
