import net.ostis.jesc.agent.ScAgentRegistry
import net.ostis.jesc.api.ScApi
import net.ostis.jesc.client.ScClient
import net.ostis.jesc.client.model.element.ScEventType
import net.ostis.jesc.client.model.type.ScType
import net.ostis.jesc.context.ScContext
import net.ostis.jesc.context.ScContextCommon
import ostis.legislation.WitAISCQueryCreator
import ostis.legislation.agent.FirstLetterSearchAgent
import ostis.legislation.agent.TelegramSendAnswerAgent
import ostis.legislation.agent.WhatIsAgent
import ostis.legislation.thirdparty.telegram.Telegram
import ostis.legislation.thirdparty.witai.WitAI

fun prepareContext(): ScContext {
    val scClient = ScClient("localhost", 8090)
    val scApi = ScApi(scClient)
    return ScContextCommon(scApi)
}

fun prepareTelegram(context: ScContext, witAI: WitAI) = Telegram(TELEGRAM_TOKEN).apply {

    newUpdateHandler {
        if (it.hasMessage()) {
            val queryCreator = WitAISCQueryCreator(context, witAI, it.message.chatId)
            queryCreator.createQuery(it.message.text)
        }
    }

}

data class Events(
    val naturalLangNewQuestion: Long,
    val naturalLangResultReady: Long,
    val firstLetterSearchNewQuestionEvent: Long
)

fun prepareEvents(context: ScContext): Events {
    val questionNaturalLang = context.resolveBySystemIdentifier("question_natural_lang", ScType.NODE_CONST_CLASS)
    val rrelAnswerNaturalLang = context.resolveBySystemIdentifier("rrel_answer_natural_lang", ScType.NODE_CONST_ROLE)
    val questionJescFirstLetterSearch = context.resolveBySystemIdentifier(
        "question_jesc_first_letter_search",
        ScType.NODE_CLASS
    )

    return Events(
        naturalLangNewQuestion = context.createEvent(ScEventType.ADD_OUTGOING_EDGE, questionNaturalLang),
        naturalLangResultReady = context.createEvent(ScEventType.ADD_OUTGOING_EDGE, rrelAnswerNaturalLang),
        firstLetterSearchNewQuestionEvent = context.createEvent(ScEventType.ADD_OUTGOING_EDGE, questionJescFirstLetterSearch)
    )
}

fun main() {
    val witAI = WitAI(WIT_AI_TOKEN)
    val context = prepareContext()
    val agentRegistry = ScAgentRegistry(context.api.client)

    val telegram = prepareTelegram(context, witAI)
    val events = prepareEvents(context)

    agentRegistry.registerAgent(WhatIsAgent(events.naturalLangNewQuestion, context.api.client))
    agentRegistry.registerAgent(TelegramSendAnswerAgent(telegram, events.naturalLangResultReady, context.api.client))
    agentRegistry.registerAgent(FirstLetterSearchAgent(events.firstLetterSearchNewQuestionEvent, context.api.client))

    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down JESC agents.")
    })
}

const val TELEGRAM_TOKEN = "6130969742:AAFRmCjEQF37J4SIQQnPMmG_k--YdlGNR-I"
const val WIT_AI_TOKEN = "OCBKFILCPZAW4KVB4PWIMZXKVAFDRBUN"