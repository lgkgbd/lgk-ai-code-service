<template>
  <div id="comments-section" class="comment-section">
    <div class="comments-header">
      <h3>评论 {{ pagination.total || '' }}</h3>
      <div class="comment-sort">
        <a-button type="text" :class="{ active: sortField === 'thumbNum' }" @click="changeSort('thumbNum')">
          最热
        </a-button>
        <a-button type="text" :class="{ active: sortField === 'createTime' }" @click="changeSort('createTime')">
          最新
        </a-button>
      </div>
    </div>

    <div class="comment-input-section">
      <a-avatar :src="currentUser?.userAvatar" :size="40">
        {{ currentUser?.userName?.charAt(0) || 'U' }}
      </a-avatar>
      <a-textarea
        v-model:value="commentContent"
        placeholder="写下你的评论..."
        :rows="1"
        auto-size
        class="comment-textarea"
      />
      <a-button type="primary" :loading="posting" @click="submitComment" :disabled="!commentContent.trim()">
        发布
      </a-button>
    </div>

    <div class="comments-list">
      <div v-if="!isLoading && comments.length === 0" class="no-comments">
        <p>暂无评论，快来抢沙发吧！</p>
      </div>

      <div v-for="comment in comments" :key="comment.id" class="comment-item">
        <CommentItem
          :comment="comment"
          :can-delete="canDelete(comment)"
          @like="likeComment(comment)"
          @reply="submitReply(comment, $event)"
          @delete="deleteComment(comment)"
        />

        <!-- 回复列表 -->
        <div v-if="displayedReplies(comment).length" class="reply-list">
          <CommentItem
            v-for="reply in displayedReplies(comment)"
            :key="reply.id"
            :comment="reply"
            :can-delete="canDelete(reply)"
            is-reply
            @like="likeComment(reply)"
            @reply="submitReply(comment, $event, reply)"
            @delete="deleteComment(reply, comment)"
          />
        </div>

        <div v-if="canExpandReplies(comment)" class="expand-replies">
          <a-button type="link" size="small" :loading="repliesState[comment.id!]?.loading" @click="expandReplies(comment)">
            {{ hasMoreReplies(comment) ? `查看全部 ${comment.replyNum} 条回复` : '加载更多回复' }}
          </a-button>
        </div>
      </div>

      <div v-if="isLoading" class="loading-indicator">
        <a-spin />
        <span style="margin-left: 8px">加载中...</span>
      </div>
      <div v-if="!isLoading && comments.length > 0" class="load-more">
        <a-button v-if="comments.length < pagination.total" type="link" @click="fetchComments(true)">
          加载更多评论
        </a-button>
        <span v-else class="no-more">--- 我是有底线的 ---</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Modal } from 'ant-design-vue'
import {
  addComment,
  deleteComment as deleteCommentApi,
  listReplyByPage,
  listTopCommentByPage,
} from '@/api/commentController'
import { doThumb } from '@/api/thumbController'
import { showError, showSuccess, showWarning } from '@/utils/message'
import { getCurrentUser, isAdmin } from '@/access/index'
import CommentItem from './CommentItem.vue'

interface Props {
  targetType: number
  targetId?: number
}

const props = defineProps<Props>()

const currentUser = computed(() => getCurrentUser())

const comments = ref<API.CommentVO[]>([])
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const isLoading = ref(false)
const sortField = ref<'thumbNum' | 'createTime'>('createTime')

const commentContent = ref('')
const posting = ref(false)

// 每条顶级评论展开后的完整回复列表（未展开时使用评论自带的 replies 预览）
const repliesState = reactive<Record<number, { list: API.CommentVO[]; loading: boolean; expanded: boolean; current: number; total: number }>>({})

const fetchComments = async (loadMore = false) => {
  if (!props.targetId || isLoading.value) return
  if (loadMore && comments.value.length >= pagination.total && pagination.total > 0) return

  isLoading.value = true
  pagination.current = loadMore ? pagination.current + 1 : 1

  try {
    const { data: res } = await listTopCommentByPage({
      targetType: props.targetType,
      targetId: props.targetId,
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      sortField: sortField.value,
      sortOrder: 'desc',
    })

    if (res?.code === 0) {
      const records = res.data?.records || []
      comments.value = loadMore ? [...comments.value, ...records] : records
      pagination.total = Number(res.data?.totalRow) || 0
    } else {
      if (loadMore) pagination.current--
      showError(res?.message || '加载评论失败')
    }
  } catch (error) {
    if (loadMore) pagination.current--
    console.error('加载评论失败:', error)
    showError('网络错误，请稍后重试')
  } finally {
    isLoading.value = false
  }
}

const changeSort = (field: 'thumbNum' | 'createTime') => {
  if (sortField.value === field) return
  sortField.value = field
  fetchComments()
}

const submitComment = async () => {
  if (!commentContent.value.trim() || !props.targetId) return

  posting.value = true
  try {
    const { data: res } = await addComment({
      content: commentContent.value.trim(),
      targetType: props.targetType,
      targetId: props.targetId,
    })

    if (res?.code === 0) {
      comments.value.unshift({
        id: res.data,
        content: commentContent.value.trim(),
        targetType: props.targetType,
        targetId: props.targetId,
        userId: currentUser.value?.id,
        user: currentUser.value,
        parentId: 0,
        thumbNum: 0,
        replyNum: 0,
        hasThumb: false,
        createTime: new Date().toISOString(),
      })
      pagination.total++
      commentContent.value = ''
      showSuccess('评论成功')
    } else {
      showError(res?.message || '评论失败')
    }
  } catch (error) {
    console.error('评论失败:', error)
    showError('网络错误，请稍后重试')
  } finally {
    posting.value = false
  }
}

// 顶级评论：parentId 落空或为 0；回复：rootId 即所属的顶级评论 id
const rootIdOf = (comment: API.CommentVO) => (comment.parentId ? comment.rootId || comment.parentId : comment.id)!

const displayedReplies = (comment: API.CommentVO) => {
  const state = repliesState[comment.id!]
  return state?.expanded ? state.list : comment.replies || []
}

const hasMoreReplies = (comment: API.CommentVO) => {
  const state = repliesState[comment.id!]
  if (!state?.expanded) return (comment.replyNum || 0) > (comment.replies?.length || 0)
  return state.list.length < state.total
}

const canExpandReplies = (comment: API.CommentVO) => hasMoreReplies(comment)

const expandReplies = async (comment: API.CommentVO) => {
  const id = comment.id!
  const state = repliesState[id] || (repliesState[id] = { list: [], loading: false, expanded: false, current: 0, total: comment.replyNum || 0 })
  if (state.loading) return

  state.loading = true
  state.current++
  try {
    const { data: res } = await listReplyByPage({
      targetType: props.targetType,
      targetId: props.targetId,
      parentId: id,
      pageNum: state.current,
      pageSize: 10,
      sortField: 'createTime',
      sortOrder: 'asc',
    })

    if (res?.code === 0) {
      const records = res.data?.records || []
      state.list = state.expanded ? [...state.list, ...records] : records
      state.total = Number(res.data?.totalRow) || 0
      state.expanded = true
    } else {
      state.current--
      showError(res?.message || '加载回复失败')
    }
  } catch (error) {
    state.current--
    console.error('加载回复失败:', error)
    showError('网络错误，请稍后重试')
  } finally {
    state.loading = false
  }
}

const submitReply = async (topComment: API.CommentVO, content: string, replyTo?: API.CommentVO) => {
  if (!content.trim() || !props.targetId) return
  const target = replyTo || topComment

  try {
    const { data: res } = await addComment({
      content: content.trim(),
      targetType: props.targetType,
      targetId: props.targetId,
      parentId: target.id,
      replyToUserId: target.userId,
    })

    if (res?.code === 0) {
      const newReply: API.CommentVO = {
        id: res.data,
        content: content.trim(),
        targetType: props.targetType,
        targetId: props.targetId,
        userId: currentUser.value?.id,
        user: currentUser.value,
        parentId: target.id,
        rootId: rootIdOf(topComment),
        replyToUserId: target.userId,
        replyToUserName: target.user?.userName,
        thumbNum: 0,
        replyNum: 0,
        hasThumb: false,
        createTime: new Date().toISOString(),
      }

      const id = topComment.id!
      const state = repliesState[id]
      if (state?.expanded) {
        state.list.push(newReply)
        state.total++
      } else if (topComment.replies && topComment.replies.length < 3) {
        topComment.replies.push(newReply)
      }
      topComment.replyNum = (topComment.replyNum || 0) + 1
      showSuccess('回复成功')
    } else {
      showError(res?.message || '回复失败')
    }
  } catch (error) {
    console.error('回复失败:', error)
    showError('网络错误，请稍后重试')
  }
}

const likeComment = async (comment: API.CommentVO) => {
  if (!comment.id) return
  try {
    const { data: res } = await doThumb({ targetId: comment.id, type: 'COMMENT' })
    if (res?.code === 0) {
      comment.hasThumb = !comment.hasThumb
      comment.thumbNum = (comment.thumbNum || 0) + (comment.hasThumb ? 1 : -1)
    } else {
      showError(res?.message || '操作失败')
    }
  } catch (error) {
    console.error('点赞失败:', error)
    showError('网络错误，请稍后重试')
  }
}

const canDelete = (comment: API.CommentVO) => isAdmin() || (!!currentUser.value?.id && comment.userId === currentUser.value.id)

const deleteComment = (comment: API.CommentVO, topComment?: API.CommentVO) => {
  Modal.confirm({
    title: '确认删除',
    content: '确定要删除这条评论吗？此操作不可恢复。',
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        const { data: res } = await deleteCommentApi({ id: comment.id })
        if (res?.code === 0) {
          if (topComment) {
            const id = topComment.id!
            const state = repliesState[id]
            if (state?.expanded) {
              state.list = state.list.filter((r) => r.id !== comment.id)
              state.total--
            } else if (topComment.replies) {
              topComment.replies = topComment.replies.filter((r) => r.id !== comment.id)
            }
            topComment.replyNum = Math.max(0, (topComment.replyNum || 0) - 1)
          } else {
            comments.value = comments.value.filter((c) => c.id !== comment.id)
            pagination.total = Math.max(0, pagination.total - 1)
          }
          showSuccess('删除成功')
        } else {
          showError(res?.message || '删除失败')
        }
      } catch (error) {
        console.error('删除评论失败:', error)
        showError('网络错误，请稍后重试')
      }
    },
  })
}

watch(() => props.targetId, (id) => {
  if (id) fetchComments()
})

onMounted(() => {
  if (props.targetId) fetchComments()
})
</script>

<style scoped>
.comment-section {
  background: white;
  padding: 24px;
  border-radius: 8px;
}
.comments-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.comments-header h3 {
  font-size: 18px;
  font-weight: 600;
}
.comment-sort .ant-btn {
  color: #666;
}
.comment-sort .ant-btn.active {
  color: #1890ff;
}

.comment-input-section {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 24px;
}
.comment-textarea {
  flex: 1;
}

.no-comments {
  text-align: center;
  padding: 40px 20px;
  color: #999;
}

.comment-item {
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}
.comment-item:last-child {
  border-bottom: none;
}

.reply-list {
  margin-left: 52px;
  margin-top: 8px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 8px;
}

.expand-replies {
  margin-left: 52px;
}

.loading-indicator,
.load-more {
  text-align: center;
  padding: 16px 0;
  color: #999;
}
.no-more {
  font-size: 13px;
}

@media (max-width: 768px) {
  .comment-section {
    margin-bottom: 12px;
    border-radius: 0;
  }
}
</style>
