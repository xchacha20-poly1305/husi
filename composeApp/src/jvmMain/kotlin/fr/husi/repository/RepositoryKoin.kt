package fr.husi.repository

import org.koin.core.context.GlobalContext

fun resolveRepository(): LocalRepository = GlobalContext.get().get()
