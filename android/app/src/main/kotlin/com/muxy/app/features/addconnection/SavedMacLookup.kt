package com.muxy.app.features.addconnection

import com.muxy.app.features.demo.DemoConnection
import com.muxy.app.models.Connection
import com.muxy.app.models.ConnectionKind

fun List<Connection>.savedMac(
    serviceName: String?,
    host: String,
    port: Int,
): Connection? {
    val macs = filter { it.kind == ConnectionKind.DEVICE && it.id != DemoConnection.id }
    val byService = serviceName?.let { name -> macs.firstOrNull { it.serviceName == name } }
    return byService ?: macs.firstOrNull { it.host.equals(host, ignoreCase = true) && it.port == port }
}
