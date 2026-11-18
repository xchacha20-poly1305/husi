package fr.husi.remote

import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcServerStreamingCall
import com.squareup.wire.GrpcStreamingCall
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal suspend fun <R : Any> GrpcCall<Unit, R>.execute(): R = execute(Unit)

internal fun <R : Any> GrpcServerStreamingCall<Unit, R>.responses(): Flow<R> = responses(Unit)

internal fun <S : Any, R : Any> GrpcServerStreamingCall<S, R>.responses(request: S): Flow<R> = flow {
    coroutineScope {
        val call = this@responses.clone()
        val responses = call.executeIn(this, request)
        try {
            emitAll(responses.receiveAsFlow())
        } finally {
            call.cancel()
            responses.cancel()
        }
    }
}

internal fun <S : Any, R : Any> GrpcStreamingCall<S, R>.responses(requests: Flow<S>): Flow<R> = flow {
    coroutineScope {
        val call = this@responses.clone()
        val (outbound, inbound) = call.executeIn(this)
        val sender = launch {
            requests.collect { outbound.send(it) }
            outbound.close()
        }
        try {
            emitAll(inbound.receiveAsFlow())
        } finally {
            call.cancel()
            inbound.cancel()
            sender.cancel()
        }
    }
}
