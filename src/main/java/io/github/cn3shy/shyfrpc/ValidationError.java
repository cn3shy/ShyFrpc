package io.github.cn3shy.shyfrpc;

/**
 * 启动前置校验的失败结果。
 *
 * @param translationKey 本地化键，直接交给 {@code Component.translatable}
 * @param detail 可选参数（通常是解析后的路径），无参数时为 {@code null}
 */
public record ValidationError(String translationKey, String detail) {
}
