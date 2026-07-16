package com.nova.agent.llm.local.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

/** İzin gerektirmeyen cihaz durum okumaları; araç seti ve hibrit yönlendirici paylaşır. */
object DeviceStatusReader {

    /**
     * (pil yüzdesi | bilinmiyorsa -1, şarj oluyor mu).
     * Alıcı null'dur: yalnız sticky ACTION_BATTERY_CHANGED anlık değeri okunur.
     * API 33+ için bayraklı overload kullanılır (lint: UnspecifiedRegisterReceiverFlag).
     */
    fun battery(context: Context): Pair<Int, Boolean> {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(null, filter)
        }
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return percent to charging
    }
}
