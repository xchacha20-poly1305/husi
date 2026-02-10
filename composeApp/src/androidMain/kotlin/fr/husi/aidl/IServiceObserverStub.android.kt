package fr.husi.aidl

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import kotlinx.parcelize.parcelableCreator

actual abstract class IServiceObserverStub : Binder(), IServiceObserver {

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

            TRANSACTION_onState -> {
                data.enforceInterface(DESCRIPTOR)
                val status = data.readTypedObject(parcelableCreator<ServiceStatus>())!!
                onState(status)
                return true
            }

            TRANSACTION_onSpeed -> {
                data.enforceInterface(DESCRIPTOR)
                val speed = data.readTypedObject(parcelableCreator<SpeedDisplayData>())!!
                onSpeed(speed)
                return true
            }

            TRANSACTION_onAlert -> {
                data.enforceInterface(DESCRIPTOR)
                val type = data.readInt()
                val message = data.readString()!!
                onAlert(type, message)
                return true
            }

            else -> return super.onTransact(code, data, reply, flags)
        }
    }

    private class Proxy(private val remote: IBinder) : IServiceObserver {
        override fun asBinder(): IBinder = remote

        override fun onState(status: ServiceStatus) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeTypedObject(status, 0)
                remote.transact(TRANSACTION_onState, data, null, IBinder.FLAG_ONEWAY)
            } finally {
                data.recycle()
            }
        }

        override fun onSpeed(speed: SpeedDisplayData) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeTypedObject(speed, 0)
                remote.transact(TRANSACTION_onSpeed, data, null, IBinder.FLAG_ONEWAY)
            } finally {
                data.recycle()
            }
        }

        override fun onAlert(type: Int, message: String) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(type)
                data.writeString(message)
                remote.transact(TRANSACTION_onAlert, data, null, IBinder.FLAG_ONEWAY)
            } finally {
                data.recycle()
            }
        }
    }

    companion object {
        private const val DESCRIPTOR = "fr.husi.aidl.IServiceObserver"
        private const val TRANSACTION_onState = FIRST_CALL_TRANSACTION + 0
        private const val TRANSACTION_onSpeed = FIRST_CALL_TRANSACTION + 1
        private const val TRANSACTION_onAlert = FIRST_CALL_TRANSACTION + 2

        fun asInterface(obj: IBinder?): IServiceObserver? {
            if (obj == null) return null
            return obj.queryLocalInterface(DESCRIPTOR) as? IServiceObserver ?: Proxy(obj)
        }
    }
}
