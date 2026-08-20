package net.catmine.studio.catDonate.message

import net.catmine.engine.message.MessageKey
import net.catmine.engine.message.MessageService
import net.catmine.engine.text.ComponentParser
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender

enum class DonateMessage(
    override val path: String,
    override val defaultText: String,
) : MessageKey {
    PREFIX("prefix", "<dark_gray>[<gold>CatDonate</gold>]</dark_gray> "),
    ONLY_PLAYER("only-player", "<red>Chỉ người chơi mới có thể dùng lệnh này."),
    USAGE_SUBMIT("usage-submit", "<yellow>Dùng /napthe để mở form nạp thẻ hoặc /napthe trangthai để xem trạng thái thẻ gần nhất."),
    FORM_TITLE("form.title", "Nạp thẻ"),
    FORM_TELCO("form.telco", "Nhà mạng"),
    FORM_AMOUNT("form.amount", "Mệnh giá"),
    FORM_SERIAL("form.serial", "Serial"),
    FORM_SERIAL_PLACEHOLDER("form.serial-placeholder", "Nhập serial..."),
    FORM_CODE("form.code", "Mã thẻ"),
    FORM_CODE_PLACEHOLDER("form.code-placeholder", "Nhập mã thẻ..."),
    FORM_SUBMIT("form.submit", "Nạp thẻ"),
    FORM_SUBMIT_TOOLTIP("form.submit-tooltip", "Gửi thông tin thẻ"),
    FORM_CLOSE("form.close", "Đóng"),
    FORM_CLOSE_TOOLTIP("form.close-tooltip", "Đóng form mà không gửi thẻ"),
    FORM_CONFIRM_TITLE("form.confirm-title", "Xác nhận nạp thẻ"),
    FORM_CONFIRM_DETAILS("form.confirm-details", "<gray>Nhà mạng:</gray> <white>{telco}</white><newline><gray>Mệnh giá:</gray> <white>{amount}</white><newline><gray>Serial:</gray> <white>{serial}</white><newline><gray>Mã thẻ:</gray> <white>{code}</white><newline><yellow>Hãy kiểm tra kỹ trước khi gửi.</yellow>"),
    FORM_CONFIRM("form.confirm", "Xác nhận gửi"),
    FORM_BACK("form.back", "Quay lại"),
    FORM_SUBMITTING("form-submitting", "<yellow>Đang lưu và gửi thông tin thẻ, vui lòng không gửi lại...</yellow>"),
    FORM_UNAVAILABLE("form-unavailable", "<red>Không thể mở form nạp thẻ lúc này."),
    NO_CARD_OPTIONS("no-card-options", "<red>Hiện không có nhà mạng hoặc mệnh giá nào được bật."),
    INVALID_FORM_SELECTION("invalid-form-selection", "<red>Lựa chọn nhà mạng hoặc mệnh giá không hợp lệ."),
    USAGE_ADMIN("usage-admin", "<yellow>Dùng: /catdonate [reload|xem|lichsu|xuly] ..."),
    INVALID_AMOUNT("invalid-amount", "<red>Mệnh giá {amount} không được bật cho {telco}."),
    INVALID_CARD_DATA("invalid-card-data", "<red>Serial và mã thẻ không hợp lệ."),
    NOT_CONFIGURED("not-configured", "<red>Dịch vụ nạp thẻ hiện chưa sẵn sàng."),
    COOLDOWN("cooldown", "<yellow>Vui lòng chờ {seconds} giây."),
    TOO_MANY_PENDING("too-many-pending", "<red>Bạn đang có tối đa {limit} giao dịch chờ."),
    DUPLICATE_CARD("duplicate-card", "<yellow>Thẻ này đã được gửi trước đó và đang được xử lý."),
    DUPLICATE_CARD_UNKNOWN("duplicate-card-unknown", "<yellow>Thẻ này đã được gửi trước đó và đang được xử lý."),
    SUBMITTED("submitted", "<green>Đã nhận thẻ."),
    SUBMITTED_WAIT("submitted-wait", "<green>Đã nhận thẻ.</green> <yellow>Thẻ đang được xử lý, vui lòng chờ khoảng {seconds} giây để có kết quả.</yellow>"),
    PENDING("pending", "<yellow>Thẻ đang được xử lý. Vui lòng chờ khoảng {seconds} giây để hệ thống kiểm tra lại.</yellow>"),
    SUCCESS("success", "<green>Nạp thẻ thành công; phần thưởng cho thẻ {amount} đã được trao."),
    SUCCESS_NO_REWARD("success-no-reward", "<yellow>Nạp thẻ thành công với mệnh giá {amount}, nhưng phần thưởng chưa được cấu hình. Hãy liên hệ quản trị viên."),
    WRONG_VALUE("wrong-value", "<green>Nạp thẻ thành công và phần thưởng đã được trao.</green> <yellow>Hệ thống đã tính thưởng theo mệnh giá {amount}.</yellow>"),
    REWARD_FAILED("reward-failed", "<yellow>Nạp thẻ đã được xác nhận nhưng phần thưởng chưa thể trao. Hãy liên hệ quản trị viên.</yellow>"),
    REWARD_CONFIRMED("reward-confirmed", "<green>Phần thưởng của bạn đã được quản trị viên xác nhận xử lý."),
    FAILED("failed", "<red>Nạp thẻ không thành công. Vui lòng kiểm tra lại thông tin thẻ."),
    MAINTENANCE("maintenance", "<yellow>Thẻ đang được xử lý. Vui lòng chờ khoảng {seconds} giây để hệ thống kiểm tra lại.</yellow>"),
    TIMEOUT("timeout", "<yellow>Thẻ đang được xử lý. Vui lòng chờ khoảng {seconds} giây để hệ thống kiểm tra lại.</yellow>"),
    POLL_EXHAUSTED("poll-exhausted", "<yellow>Thẻ đang cần được kiểm tra thêm. Hãy liên hệ quản trị viên nếu chưa có kết quả.</yellow>"),
    NEEDS_REVIEW("needs-review", "<yellow>Thẻ đang cần được kiểm tra thêm. Hãy liên hệ quản trị viên nếu chưa có kết quả.</yellow>"),
    STATUS("status", "<yellow>Trạng thái thẻ gần nhất: <white>{status}</white>{next_check}</yellow>"),
    STATUS_NOT_FOUND("status-not-found", "<red>Không tìm thấy giao dịch."),
    STATUS_LOAD_FAILED("status-load-failed", "<red>Không thể tải trạng thái giao dịch lúc này. Vui lòng thử lại sau."),
    STATUS_SUBMITTING("status-values.submitting", "đang được xử lý"),
    STATUS_PENDING("status-values.pending", "đang được xử lý"),
    STATUS_SUCCESS("status-values.success", "thẻ đã được xác nhận"),
    STATUS_FAILED("status-values.failed", "thất bại"),
    STATUS_NEEDS_REVIEW("status-values.needs-review", "cần được kiểm tra thêm"),
    STATUS_POLL_EXHAUSTED("status-values.poll-exhausted", "cần được kiểm tra thêm"),
    STATUS_REVIEW_EXPIRED("status-values.review-expired", "cần được kiểm tra thêm"),
    STATUS_NEXT_CHECK("status-parts.next-check", " — dự kiến kiểm tra lại sau khoảng {seconds} giây"),
    STATUS_DETAIL("status-parts.detail", " — chi tiết: {reason}"),
    REWARD_NONE("reward-values.none", "không áp dụng"),
    REWARD_PENDING("reward-values.pending", "đang chờ trao"),
    REWARD_PROCESSING("reward-values.processing", "đang trao"),
    REWARD_COMPLETED("reward-values.completed", "đã trao"),
    REWARD_NEEDS_REVIEW("reward-values.needs-review", "cần quản trị viên xử lý"),
    RELOAD_SUCCESS("reload-success", "<green>Đã tải lại cấu hình."),
    RELOAD_REJECTED("reload-rejected", "<red>Không thể đổi partner/domain khi còn giao dịch chưa kết thúc."),
    RELOAD_FAILED("reload-failed", "<red>Không thể tải cấu hình: {reason}"),
    ADMIN_VIEW("admin-view", "<gold>{request_id}</gold> player={player}, status={status}, reward={reward_state}, polls={polls}, error={error}"),
    ADMIN_HISTORY_HEADER("admin-history-header", "<gold>{count} giao dịch nạp thẻ thành công gần nhất:</gold>"),
    ADMIN_HISTORY_ENTRY("admin-history-entry", "<gray>#{number} • {completed_at}</gray> <white>{player}</white> — <aqua>{telco}</aqua> <green>{amount}</green>"),
    ADMIN_HISTORY_EMPTY("admin-history-empty", "<yellow>Chưa có giao dịch nạp thẻ thành công nào.</yellow>"),
    ADMIN_ACTION_DONE("admin-action-done", "<green>Đã thực hiện {action} cho {request_id}."),
    ADMIN_ACTION_WARNING("admin-action-warning", "<yellow>Cảnh báo: thao tác này có thể thưởng trùng."),
    ADMIN_ACTION_INVALID("admin-action-invalid", "<red>Không thể thực hiện thao tác này."),
    INTERNAL_ERROR("internal-error", "<red>Không thể nhận thẻ lúc này. Vui lòng thử lại sau."),
}

class DonateMessenger(private val service: MessageService<DonateMessage>) {
    fun reload() = service.reload()

    fun send(sender: CommandSender, key: DonateMessage, placeholders: Map<String, String> = emptyMap()) {
        sender.sendMessage(service.component(DonateMessage.PREFIX).append(component(key, placeholders)))
    }

    fun component(key: DonateMessage, placeholders: Map<String, String> = emptyMap()): Component {
        val safe = placeholders.mapValues { ComponentParser.escapeTags(it.value) }
        return ComponentParser.parse(replaceLegacyPlaceholders(service.raw(key, safe), safe))
    }

    fun plain(key: DonateMessage, placeholders: Map<String, String> = emptyMap()): String =
        PLAIN.serialize(component(key, placeholders))

    companion object {
        private val PLAIN = PlainTextComponentSerializer.plainText()
    }
}

internal fun replaceLegacyPlaceholders(template: String, placeholders: Map<String, String>): String =
    placeholders.entries.fold(template) { text, (key, value) -> text.replace("<$key>", value) }
