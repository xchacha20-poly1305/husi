package fr.husi.aidl

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import kotlinx.parcelize.parcelableCreator

actual abstract class IServiceControlStub : Binder(), IServiceControl {

    init {
        attachInterface(this, DESCRIPTOR)
    }

    override fun asBinder(): IBinder = this

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        when (code) {
            INTERFACE_TRANSACTION -> {
                reply?.writeString(DESCRIPTOR)
                return true
            }

            TRANSACTION_getStatus -> {
                data.enforceInterface(DESCRIPTOR)
                val result = getStatus()
                reply!!.writeNoException()
                reply.writeTypedObject(result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
                return true
            }

            TRANSACTION_registerObserver -> {
                data.enforceInterface(DESCRIPTOR)
                val observer = IServiceObserverStub.asInterface(data.readStrongBinder())
                registerObserver(observer)
                reply!!.writeNoException()
                return true
            }

            TRANSACTION_unregisterObserver -> {
                data.enforceInterface(DESCRIPTOR)
                val observer = IServiceObserverStub.asInterface(data.readStrongBinder())
                unregisterObserver(observer)
                reply!!.writeNoException()
                return true
            }

            else -> return super.onTransact(code, data, reply, flags)
        }
    }

    private class Proxy(private val remote: IBinder) : IServiceControl {
        override fun asBinder(): IBinder = remote

        override fun getStatus(): ServiceStatus {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TRANSACTION_getStatus, data, reply, 0)
                reply.readException()
                reply.readTypedObject(parcelableCreator<ServiceStatus>())!!
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        override fun registerObserver(observer: IServiceObserver?) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeStrongBinder(observer?.asBinder())
                remote.transact(TRANSACTION_registerObserver, data, reply, 0)
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        override fun unregisterObserver(observer: IServiceObserver?) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeStrongBinder(observer?.asBinder())
                remote.transact(TRANSACTION_unregisterObserver, data, reply, 0)
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }
    }

    companion object {
        private const val DESCRIPTOR = "fr.husi.aidl.IServiceControl"
        private const val TRANSACTION_getStatus = FIRST_CALL_TRANSACTION + 0
        private const val TRANSACTION_registerObserver = FIRST_CALL_TRANSACTION + 1
        private const val TRANSACTION_unregisterObserver = FIRST_CALL_TRANSACTION + 2

        fun asInterface(obj: IBinder?): IServiceControl? {
            if (obj == null) return null
            return obj.queryLocalInterface(DESCRIPTOR) as? IServiceControl ?: Proxy(obj)
        }
    }
}
