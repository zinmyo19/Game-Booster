// PATH: app/src/main/java/com/gamebooster/shizuku/IShellService.kt
package com.gamebooster.shizuku

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

// Manual Binder interface — replaces the .aidl file
// No AIDL compiler needed!
interface IShellService : IInterface {
    fun exec(command: String): String?

    // Stub class — server side (runs inside Shizuku process)
    abstract class Stub : Binder(), IShellService {

        companion object {
            private const val DESCRIPTOR = "com.gamebooster.shizuku.IShellService"
            private const val TRANSACTION_exec = IBinder.FIRST_CALL_TRANSACTION

            // Client side — get interface from binder
            fun asInterface(binder: IBinder?): IShellService? {
                if (binder == null) return null
                val iin = binder.queryLocalInterface(DESCRIPTOR)
                if (iin != null && iin is IShellService) return iin
                return Proxy(binder)
            }
        }

        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(
            code: Int, data: Parcel, reply: Parcel?, flags: Int
        ): Boolean {
            return when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    true
                }
                TRANSACTION_exec -> {
                    data.enforceInterface(DESCRIPTOR)
                    val command = data.readString() ?: ""
                    val result  = exec(command)
                    reply?.writeNoException()
                    reply?.writeString(result)
                    true
                }
                else -> super.onTransact(code, data, reply, flags)
            }
        }

        // Proxy class — client side (runs in app process)
        private class Proxy(private val remote: IBinder) : IShellService {

            override fun asBinder(): IBinder = remote

            override fun exec(command: String): String? {
                val data  = Parcel.obtain()
                val reply = Parcel.obtain()
                return try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeString(command)
                    remote.transact(TRANSACTION_exec, data, reply, 0)
                    reply.readException()
                    reply.readString()
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            }
        }
    }
}
