package org.opencarproject.launcher.data.source

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import org.opencarproject.launcher.core.model.ClimateVendor

/**
 * Adapter over the head unit's CAN app. Nothing about the vendor is compiled in: transaction
 * codes, descriptors and the `AcState$SettingType` enum are read by reflection from the vendor's
 * own classes (loaded from its installed APK). State comes through its `ICanBusServiceFeature`
 * service (raw [IBinder.transact]), which streams it once the system's own apps have initialized
 * the vendor SDK (its rear-camera app does at boot); the launcher never initializes it. Commands go through the broadcast its own AC panel uses, so
 * the vendor maps them to the CAN box and car the system is configured for. Binder methods are
 * round trips: call them off the main thread.
 */
class QfVehicleBridge private constructor(
    private val vendor: ClimateVendor,
    private val remote: IBinder,
    /** Interface token of the vendor's `ICanBusServiceFeature` (its service doesn't report it). */
    private val descriptor: String,
    private val codes: Map<String, Int>,
    private val callbackDescriptor: String,
    private val packedDataCode: Int,
    /** The vendor's `AcState$SettingType` constants: name → value. */
    val settingTypes: Map<String, Int>,
) {
    /** A binder the service can call back with AC frames; [onFrame] runs on a binder thread. */
    fun newCallback(onFrame: (ByteArray) -> Unit): IBinder = PackedDataCallback(callbackDescriptor, packedDataCode, onFrame)

    fun addAcCallback(callback: IBinder) = call("addAcStateCallBack") { writeStrongBinder(callback) }

    fun removeAcCallback(callback: IBinder) = call("removeAcStateCallBack") { writeStrongBinder(callback) }

    /** Sends a `SettingType` (e.g. `"Power"`) the way the vendor's AC panel does; false if unknown. */
    fun send(context: Context, settingType: String): Boolean {
        val value = settingTypes[settingType] ?: return false
        context.sendBroadcast(
            Intent(vendor.controlAction)
                .setPackage(vendor.packageName)
                .putExtra(vendor.controlExtra, value.toByte()),
        )
        return true
    }

    private fun call(method: String, args: Parcel.() -> Unit): Boolean {
        val code = codes[method] ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            data.args()
            remote.transact(code, data, reply, 0)
            reply.readException()
            true
        } catch (e: Exception) {
            Log.w(TAG, "$method failed", e)
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Server side of the vendor's `ICanBusServiceCallback`, answering only `onGetPackedData`. */
    private class PackedDataCallback(
        private val descriptor: String,
        private val packedDataCode: Int,
        private val onFrame: (ByteArray) -> Unit,
    ) : Binder() {
        init {
            attachInterface(null, descriptor)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != packedDataCode) {
                // Other callbacks (raw data…) are acknowledged and ignored.
                if (code in FIRST_CALL_TRANSACTION..LAST_CALL_TRANSACTION) {
                    reply?.writeNoException()
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
            data.enforceInterface(descriptor)
            data.createByteArray()?.let(onFrame)
            reply?.writeNoException()
            return true
        }
    }

    companion object {
        private const val TAG = "ClimateSource"
        private val METHODS = listOf("addAcStateCallBack", "removeAcStateCallBack")

        /** Reads the vendor classes for [remote]; null when they're missing. */
        fun load(context: Context, vendor: ClimateVendor, remote: IBinder): QfVehicleBridge? = runCatching {
            val loader = context.createPackageContext(
                vendor.packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            ).classLoader
            val feature = Class.forName(vendor.featureStub, false, loader)
            val callback = Class.forName(vendor.callbackStub, false, loader)
            val settingTypes = Class.forName(vendor.settingTypeClass, true, loader).declaredFields
                .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == Byte::class.javaPrimitiveType }
                .associate { it.isAccessible = true; it.name to it.getByte(null).toInt() }
            QfVehicleBridge(
                vendor = vendor,
                remote = remote,
                descriptor = feature.staticField("DESCRIPTOR"),
                codes = METHODS.mapNotNull { name ->
                    runCatching { name to feature.staticField<Int>("TRANSACTION_$name") }.getOrNull()
                }.toMap(),
                callbackDescriptor = callback.staticField("DESCRIPTOR"),
                packedDataCode = callback.staticField("TRANSACTION_onGetPackedData"),
                settingTypes = settingTypes,
            )
        }.onFailure { Log.w(TAG, "can't read vendor classes", it) }.getOrNull()

        @Suppress("UNCHECKED_CAST")
        private fun <T> Class<*>.staticField(name: String): T =
            getDeclaredField(name).apply { isAccessible = true }.get(null) as T
    }
}
