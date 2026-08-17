# MessageBubble.vue 修改补丁

## 问题分析

当前 MessageBubble.vue 的第 155-165 行仍然使用旧逻辑：

```typescript
// ❌ 旧代码：从 msg.redPack.status 读取（已不存在）
const redPackGrabbed = computed(() => isRedPackGrabbedBy(props.msg.redPack, store.userId))
const redPackStatus = computed(() =>
  redPackStatusText(props.msg.redPack?.status, redPackGrabbed.value),
)
const redPackDone = computed(
  () => redPackGrabbed.value || !isRedPackOngoing(props.msg.redPack?.status),
)
```

由于消息的 `data` 中已经没有 `status` 和 `claimantIds` 字段了，所以 `props.msg.redPack?.status` 一直是 `undefined`，导致 UI 不会更新。

---

## 解决方案

### 步骤 1：修改 MessageBubble.vue 的 script 部分

找到第 147-171 行的红包相关代码，替换为：

```vue
<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import type { Conversation, Message } from '@/entity'
import { userStore } from '@/store/user'
import { useRedPacket } from '@/composables/useRedPacket'  // ← 新增导入
import {
  isQuotableType,
  // 移除这些旧的工具函数导入
  // isRedPackGrabbedBy,
  // isRedPackOngoing,
  // redPackStatusText,
  noticeLinesOf,
  parseMentionContent,
  rtcMediaLabel,
  rtcTextOf,
  systemTextOf,
} from '@/utils/chat'
// ... 其他导入

const store = userStore()

// ... 其他代码

// ---------- 红包封面（kind === 'redpack'）----------
/** 祝福语：后端没给时按微信默认语兜底，封面不能空着 */
const redPackBlessing = computed(() => props.msg.redPack?.blessing || '恭喜发财，大吉大利')

// ✅ 新代码：使用 useRedPacket 从状态映射读取
const {
  isGrabbed: redPackGrabbed,
  statusText: redPackStatus,
  isOngoing: redPackIsOngoing,
  isExpired: redPackIsExpired,
} = useRedPacket(
  computed(() => props.msg.redPack?.packetId),  // 响应式传入 packetId
  store.userId
)

/** 我已领过、或已抢完 / 已过期：封面置灰，点开只能看详情 */
const redPackDone = computed(
  () => redPackGrabbed.value || !redPackIsOngoing.value,
)

/** 拼手气红包在封面底栏标注「共 N 个」，单聊（totalCount<=1）不标 */
const redPackCountText = computed(() => {
  const n = props.msg.redPack?.totalCount ?? 0
  return n > 1 ? `共 ${n} 个` : ''
})

/** 点击封面：多选模式下不响应，交给整行的勾选逻辑 */
function onRedPackClick() {
  if (props.selectMode) return
  emit('redpack-click', props.msg)
}
```

---

## 步骤 2：修改 template 部分（如果需要）

模板中的红包气泡部分应该已经在使用这些 computed 属性了，不需要修改。但如果你想添加过期样式，可以：

```vue
<template>
  <!-- 红包气泡 -->
  <div
    v-if="msg.kind === 'redpack'"
    class="redpack-bubble"
    :class="{ done: redPackDone, expired: redPackIsExpired }"
    @click="onRedPackClick"
  >
    <div class="redpack-content">
      <div class="redpack-blessing">{{ redPackBlessing }}</div>
      <div class="redpack-status">{{ redPackStatus }}</div>
    </div>
    <span v-if="redPackCountText" class="redpack-count">{{ redPackCountText }}</span>
  </div>
</template>

<style scoped>
.redpack-bubble.expired {
  opacity: 0.7;
  filter: grayscale(30%);
}
</style>
```

---

## 步骤 3：验证事件处理器

确保 `LinkUpdateRedPacketEventHandler` 正在工作。在浏览器控制台应该能看到：

```
[RedPacketStore] 更新红包状态: 6a807a662ef4bb0563b6d6b8, status: 2, claimantId: null
```

---

## 步骤 4：调试

如果修改后仍然没有效果，在浏览器控制台运行：

```javascript
// 1. 检查状态映射是否更新
import { useRedPacketStateMap } from '@/store/redPacketStore'
const map = useRedPacketStateMap()
console.log('红包状态映射:', Array.from(map.entries()))

// 2. 手动触发更新测试
import { updateRedPacketState } from '@/store/redPacketStore'
updateRedPacketState('6a807a662ef4bb0563b6d6b8', 2, null, 1, '0.01')
```

---

## 关键点总结

1. **不要从 `msg.redPack.status` 读取** - 这个字段已经不存在了
2. **使用 `useRedPacket` composable** - 它会从状态映射读取，并且是响应式的
3. **packetId 必须正确传递** - 用 `computed(() => props.msg.redPack?.packetId)` 包装
4. **事件处理器必须正常工作** - 检查控制台日志

修改后，当红包过期时，UI 应该会立即更新为"已过期"状态。
