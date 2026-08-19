package net.catmine.studio.catDonate.message

import net.catmine.engine.message.MessageKey
import net.catmine.engine.message.MessageService
import net.catmine.engine.text.ComponentParser
import org.bukkit.command.CommandSender

enum class DonateMessage(
    override val path: String,
    override val defaultText: String,
) : MessageKey {
    PREFIX("prefix", "<dark_gray>[<gold>CatDonate</gold>]</dark_gray> "),
    ONLY_PLAYER("only-player", "<red>Chỉ người chơi mới có thể dùng lệnh này."),
    USAGE_SUBMIT("usage-submit", "<yellow>Dùng: /napthe [nhà-mạng] [mệnh-giá] [serial] [mã-thẻ]"),
    USAGE_ADMIN("usage-admin", "<yellow>Dùng: /catdonate [reload|xem|xuly] ..."),
    INVALID_TELCO("invalid-telco", "<red>Nhà mạng không hợp lệ."),
    INVALID_AMOUNT("invalid-amount", "<red>Mệnh giá {amount} không được bật cho {telco}."),
    INVALID_CARD_DATA("invalid-card-data", "<red>Serial và mã thẻ không hợp lệ."),
    NOT_CONFIGURED("not-configured", "<red>Card2K chưa được cấu hình."),
    COOLDOWN("cooldown", "<yellow>Vui lòng chờ {seconds} giây."),
    TOO_MANY_PENDING("too-many-pending", "<red>Bạn đang có tối đa {limit} giao dịch chờ."),
    DUPLICATE_CARD("duplicate-card", "<red>Thẻ này đã được gửi trước đó."),
    SUBMITTED("submitted", "<green>Đã nhận thẻ. Mã giao dịch: <white>{request_id}</white>.</green> <gray>Dùng /napthe trangthai {request_id} để kiểm tra."),
    SUBMITTED_WAIT("submitted-wait", "<green>Đã nhận thẻ. Mã giao dịch: <white>{request_id}</white>.</green> <yellow>Vui lòng chờ khoảng {seconds} giây để hệ thống kiểm tra kết quả.</yellow>"),
    PENDING("pending", "<yellow>Giao dịch {request_id} đang xử lý; hệ thống sẽ tự kiểm tra lại."),
    SUCCESS("success", "<green>Giao dịch {request_id} thành công, mệnh giá {amount}."),
    SUCCESS_NO_REWARD("success-no-reward", "<yellow>Giao dịch thành công nhưng chưa có cấu hình thưởng."),
    WRONG_VALUE("wrong-value", "<green>Giao dịch {request_id} thành công.</green> <yellow>Thẻ sai mệnh giá; dùng giá trị thực {amount}."),
    FAILED("failed", "<red>Giao dịch {request_id} thất bại: {reason}."),
    MAINTENANCE("maintenance", "<yellow>Card2K đang bảo trì; giao dịch sẽ tự kiểm tra lại."),
    TIMEOUT("timeout", "<red>Card2K phản hồi quá thời gian; giao dịch sẽ được kiểm tra lại."),
    POLL_EXHAUSTED("poll-exhausted", "<red>Giao dịch {request_id} đã hết lượt kiểm tra."),
    NEEDS_REVIEW("needs-review", "<red>Giao dịch {request_id} cần kiểm tra thủ công."),
    STATUS("status", "<gold>{request_id}</gold> — {status} — {telco} {amount} — {created_at}"),
    STATUS_NOT_FOUND("status-not-found", "<red>Không tìm thấy giao dịch."),
    RELOAD_SUCCESS("reload-success", "<green>Đã tải lại cấu hình."),
    RELOAD_REJECTED("reload-rejected", "<red>Không thể đổi partner/domain khi còn giao dịch chưa kết thúc."),
    RELOAD_FAILED("reload-failed", "<red>Không thể tải cấu hình: {reason}"),
    ADMIN_VIEW("admin-view", "<gold>{request_id}</gold> player={player}, status={status}, reward={reward_state}, polls={polls}, error={error}"),
    ADMIN_ACTION_DONE("admin-action-done", "<green>Đã thực hiện {action} cho {request_id}."),
    ADMIN_ACTION_WARNING("admin-action-warning", "<yellow>Cảnh báo: thao tác này có thể thưởng trùng."),
    ADMIN_ACTION_INVALID("admin-action-invalid", "<red>Không thể thực hiện thao tác này."),
    INTERNAL_ERROR("internal-error", "<red>Có lỗi nội bộ. Mã giao dịch: {request_id}."),
}

class DonateMessenger(private val service: MessageService<DonateMessage>) {
    fun reload() = service.reload()

    fun send(sender: CommandSender, key: DonateMessage, placeholders: Map<String, String> = emptyMap()) {
        val safe = placeholders.mapValues { ComponentParser.escapeTags(it.value) }
        val rendered = replaceLegacyPlaceholders(service.raw(key, safe), safe)
        sender.sendMessage(service.component(DonateMessage.PREFIX).append(ComponentParser.parse(rendered)))
    }
}

internal fun replaceLegacyPlaceholders(template: String, placeholders: Map<String, String>): String =
    placeholders.entries.fold(template) { text, (key, value) -> text.replace("<$key>", value) }
