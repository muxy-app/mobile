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
    return byService ?: macs.firstOrNull { it.isAt(host, port) && !it.isAnotherService(serviceName) }
}

fun Connection.isAt(
    host: String,
    port: Int,
): Boolean = this.host.equals(host, ignoreCase = true) && this.port == port

private fun Connection.isAnotherService(name: String?): Boolean = name != null && serviceName != null && serviceName != name
