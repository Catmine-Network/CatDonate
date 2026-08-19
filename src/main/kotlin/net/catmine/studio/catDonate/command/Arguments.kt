package net.catmine.studio.catDonate.command

import dev.rollczi.litecommands.argument.Argument
import dev.rollczi.litecommands.argument.parser.ParseResult
import dev.rollczi.litecommands.argument.resolver.ArgumentResolver
import dev.rollczi.litecommands.invocation.Invocation
import dev.rollczi.litecommands.suggestion.SuggestionContext
import dev.rollczi.litecommands.suggestion.SuggestionResult
import net.catmine.studio.catDonate.model.AdminAction
import net.catmine.studio.catDonate.model.Telco
import org.bukkit.command.CommandSender

class TelcoArgument : ArgumentResolver<CommandSender, Telco>() {
    override fun parse(invocation: Invocation<CommandSender>, context: Argument<Telco>, argument: String): ParseResult<Telco> =
        Telco.parse(argument)?.let(ParseResult<Telco>::success) ?: ParseResult.failure("Nhà mạng không hợp lệ")

    override fun suggest(invocation: Invocation<CommandSender>, argument: Argument<Telco>, context: SuggestionContext): SuggestionResult =
        SuggestionResult.of(Telco.suggestions)
}

class AdminActionArgument : ArgumentResolver<CommandSender, AdminAction>() {
    override fun parse(invocation: Invocation<CommandSender>, context: Argument<AdminAction>, argument: String): ParseResult<AdminAction> =
        AdminAction.parse(argument)?.let(ParseResult<AdminAction>::success) ?: ParseResult.failure("Thao tác không hợp lệ")

    override fun suggest(invocation: Invocation<CommandSender>, argument: Argument<AdminAction>, context: SuggestionContext): SuggestionResult =
        SuggestionResult.of(AdminAction.suggestions)
}
