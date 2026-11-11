package io.github.lucf15.localai.reader

import io.github.lucf15.localai.sdk.Capability

const val SAMPLE_TEXT = "La biblioteca Aurora resterà chiusa il 14 novembre per manutenzione. Riaprirà il 15 novembre alle 09:00. Il prestito digitale resterà disponibile durante la chiusura."

internal data class Example(val id: String, val title: String, val description: String, val capability: Capability, val source: String, val icon: Int)
internal val examples = listOf(
    Example("article", "Catch up on an article", "Turn a short news item into a summary.", Capability.SUMMARIZE,
        SAMPLE_TEXT, io.github.lucf15.localai.design.R.drawable.ic_article),
    Example("email", "Polish an email", "Make a rough draft clearer and more professional.", Capability.REWRITE,
        "Ciao Marta, non riesco a finire la proposta per oggi. Mi mancano i dati sulle vendite. Me li puoi mandare entro domani mattina? Così ti mando tutto per venerdì. Grazie!",
        io.github.lucf15.localai.design.R.drawable.ic_mail),
    Example("note", "Check a note", "Correct spelling and grammar without changing the meaning.", Capability.PROOFREAD,
        "Dommani abbiamo una riunione con il cliente. Ricordati di portare i documento aggiornati e di controllare che tutte le slide sia pronte.",
        io.github.lucf15.localai.design.R.drawable.ic_edit),
)
