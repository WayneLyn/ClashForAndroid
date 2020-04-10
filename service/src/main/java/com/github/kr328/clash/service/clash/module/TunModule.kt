package com.github.kr328.clash.service.clash.module

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.github.kr328.clash.common.ids.NotificationIds
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.util.parseCIDR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TunModule(private val service: VpnService) : Module() {
    interface Configure {
        val builder: VpnService.Builder
        val mtu: Int
        val gateway: String
        val mirror: String
        val route: List<String>
        val dnsAddress: String
        val dnsHijacking: Boolean
        val allowApplications: List<String>
        val disallowApplication: List<String>
    }

    private val contentIntent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_DEFAULT)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setPackage(service.packageName)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    var configure: Configure? = null

    override suspend fun onStart() {
        withContext(Dispatchers.IO) {
            val c = configure ?: throw IllegalArgumentException("Configure required")

            val builder = c.builder

            parseCIDR(c.gateway).let {
                builder.addAddress(it.ip, it.prefix)
            }
            c.route.map { parseCIDR(it) }.forEach {
                builder.addRoute(it.ip, it.prefix)
            }
            c.allowApplications.forEach {
                builder.addAllowedApplication(it)
            }
            c.disallowApplication.forEach {
                builder.addDisallowedApplication(it)
            }

            builder.setBlocking(false)
            builder.setMtu(c.mtu)
            builder.setSession("Clash")
            builder.addDnsServer(c.dnsAddress)
            builder.setConfigureIntent(
                PendingIntent.getActivity(
                    service,
                    NotificationIds.CLASH_VPN,
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            val fd = builder.establish() ?: throw NullPointerException("Unable to create vpn")

            if (c.dnsHijacking) {
                Clash.startTunDevice(
                    fd.detachFd(),
                    c.mtu,
                    c.gateway,
                    c.mirror,
                    IPV4_ANY,
                    service::protect,
                    service::stopSelf
                )
            } else {
                Clash.startTunDevice(
                    fd.detachFd(),
                    c.mtu,
                    c.gateway,
                    c.mirror,
                    c.dnsAddress,
                    service::protect,
                    service::stopSelf
                )
            }
        }
    }

    override suspend fun onStop() {
        Clash.stopTunDevice()
    }

    companion object {
        private const val IPV4_ANY = "0.0.0.0"

        fun requestStop() {
            Clash.stopTunDevice()
        }
    }
}