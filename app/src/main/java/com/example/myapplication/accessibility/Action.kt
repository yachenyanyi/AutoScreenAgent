package com.example.autoscreenagent.accessibility

/**
 * 动作执行结果
 */
sealed class ActionResult {
    object Success : ActionResult()
    data class Failure(val reason: String) : ActionResult()
    data class NotFound(val targetType: String, val targetValue: String) : ActionResult()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
}

/**
 * 动作定义
 */
data class Action(
    val actionId: String,
    val action: String,
    val params: Map<String, Any>,
    val description: String = "",
    val timeout: Long = 5000L
)

/**
 * 动作类型枚举
 */
enum class ActionType(val value: String) {
    LAUNCH("launch"),
    TAP("tap"),
    TYPE("type"),
    SWIPE("swipe"),
    BACK("back"),
    HOME("home"),
    FINISH("finish"),
    CONFIRM("confirm")
}