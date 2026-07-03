package com.link.im.entity.data;

import com.link.im.entity.base.BaseData;
import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
@ToString(callSuper = true)
public class TextData extends BaseData {

    /**
     * 正文。@ 处用占位符表示，避免存昵称（昵称会改、且无法可靠反查用户，还能防昵称注入）：
     * <ul>
     *   <li>@某人 → {@code {@userId}}，userId 必须能在 {@link #mentions} 里找到；</li>
     *   <li>@全体 → {@code {@all}}，且 {@link #mentionAll} 为 true。</li>
     * </ul>
     * 例：{@code "上午的方案 {@1001} 你看下,{@all} 也确认下"}。
     * 无 @ 的老消息 content 里不含占位符，渲染按普通文本处理，完全兼容。
     */
    private String content;

    /**
     * 结构化提及列表（@某人）。为空表示没有 @ 个人，不影响老逻辑。
     * mentionAll 不在此列出——@全体由 {@link #mentionAll} 单独标记，避免展开全体成员。
     */
    private List<Mention> mentions;

    /** 是否 @ 全体成员。与 {@link #mentions} 可同时存在。仅群主/管理员可置 true，服务端会校验。 */
    private boolean mentionAll;
}
