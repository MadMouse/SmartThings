/**
 *  Roku Manager
 *
 *  Copyright 2016 Leslie Drewery
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Roku (Connect)",
    namespace: "madmouse",
    author: "Leslie Drewery",
    description: "Manage Roku Devices ",
    singleInstance: true,
    category: "SmartThings Labs",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

def urnPlayerLink = "urn:roku-com:device:player:1"

preferences {
    page(name: "deviceDiscovery", title: "Roku Device Setup", content: "deviceDiscovery")
}



def deviceDiscovery() {
	int refreshCount = !state.refreshCount ? 0 : state.refreshCount as int
	state.refreshCount = refreshCount + 1
	def refreshInterval = 5
        
	def options = [:]
	def devices = getVerifiedDevices()
	devices.each {
    	def values = it.value.ssdpUSN.split(':')
		def value = it.value.name ?: "${values[1]}-${values[3]}"
		def key = it.value.mac
		options["${key}"] = value
	}


	if(!state.subscribe) {
        	log.debug "subscribe :: ${refreshCount}"
			ssdpSubscribe();
			
	}
	
    //ssdp request every 25 seconds
	if((refreshCount % 5) == 0) {
		ssdpDiscover()
    }
	
    //setup.xml request every 5 seconds except on discoveries
    if(((refreshCount % 1) == 0) && ((refreshCount % 5) != 0)) {
        log.debug "verifyDevices :: ${refreshCount}"
        verifyDevices()
    }

	return dynamicPage(name: "deviceDiscovery", title: "Discovery Started!", nextPage: "", refreshInterval: 5, install: true, uninstall: true) {
		section("Please wait while we discover your Roku Devices. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
			input "selectedDevices", "enum", required: false, title: "Select Devices (${options.size() ?: 0} found)", multiple: true, options: options
		}
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	addDevices()
	unsubscribe()
	initialize()
}

def initialize() {
	unsubscribe()
	unschedule()
	
    state.subscribe = false
	
	if (selectedDevices) {
		addDevices()
	}

	runEvery5Minutes("ssdpDiscover")
}

def uninstalled() {
    removeChildDevices(getChildDevices())
}

private removeChildDevices(delete) {
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
}

void ssdpDiscover() {
	log.debug "ssdpDiscover: roku:ecp"
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery roku:ecp", physicalgraph.device.Protocol.LAN))
}

void ssdpSubscribe() {
	log.debug "ssdpSubscribe fired"
	subscribe(location, "ssdpTerm.roku:ecp", ssdpHandler)
    state.subscribe = true
}

Map verifiedDevices() {
	def devices = getVerifiedDevices()
	def map = [:]
	devices.each {
		def value = it.value.name ?: "Roku ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
		def key = it.value.mac
		map["${key}"] = value
	}
	map
}

void verifyDevices() {
	log.debug "verifyDevices"
    def devices = getDevices()
	devices.each {
		int port = convertHexToInt(it.value.deviceAddress)
		String ip = convertHexToIP(it.value.networkAddress)
		String host = "${ip}:${port}"
		sendHubCommand(new physicalgraph.device.HubAction("""GET ${it.value.ssdpPath} HTTP/1.1\r\nHOST: ${host}\r\n\r\n""", physicalgraph.device.Protocol.LAN, host, [callback: deviceDescriptionHandler]))
	}
}

def getVerifiedDevices() {
	log.debug "getVerifiedDevices"
    getDevices()
}

def getDevices() {
	if (!state.devices) {
		state.devices = [:]
	}
	state.devices
}

def addDevices() {
	deleteRemovedDevices()
	log.debug "addDevices"
	def devices = getDevices()

	selectedDevices.each { dni ->
		def selectedDevice = devices.find { it.value.mac == dni }
		def d
		if (selectedDevice) {
			d = getChildDevices()?.find {
				it.deviceNetworkId == selectedDevice.value.mac
			}
		}

		if (!d) {
			log.debug "Creating Roku Device with dni: ${selectedDevice.value} - ${selectedDevice.value.networkAddress} : ${selectedDevice.value.deviceAddress}"
			addChildDevice("madmouse", "Roku", selectedDevice.value.mac, selectedDevice?.value.hub, [
				"label": selectedDevice?.value?.name ?: "Roku - ${selectedDevice.value.mac}",
				"data": [
					"mac": selectedDevice.value.mac,
					"ip": selectedDevice.value.networkAddress,
					"port": selectedDevice.value.deviceAddress
				]
			])
		}
	}
//    deleteRemovedDevices();
}

private deleteRemovedDevices(){
	if(selectedDevices){
        log.trace "selectedDevices ${selectedDevices}"
        log.trace "getChildDevices() ${getChildDevices()}"
        def delete = getChildDevices().findAll { !selectedDevices.contains(it.deviceNetworkId) }
    //	def delete = devicesVerified.findAll { !selectedDevices.contains(it.value.mac) }
        log.trace "Remove Devices ${delete}"
        if(delete){
            delete.each {
                deleteChildDevice(it.deviceNetworkId)
            }
        }
    } else {
    	removeChildDevices(getChildDevices())
    }
}

def parse(description) {
	log.trace "parse : " + description
}


def ssdpHandler(evt) {
	def description = evt.description
	def hub = evt?.hubId

	def parsedEvent = parseLanMessage(description)
	parsedEvent << ["hub":hub]
	log.debug "parsedEvent ${parsedEvent}"
    
	def devices = getDevices()
	String ssdpUSN = parsedEvent.ssdpUSN.toString()
	if (devices."${ssdpUSN}") {
		log.debug "found device ssdpUSN ${ssdpUSN}"
        def d = devices."${ssdpUSN}"
		if (d.networkAddress != parsedEvent.networkAddress || d.deviceAddress != parsedEvent.deviceAddress) {
			d.networkAddress = parsedEvent.networkAddress
			d.deviceAddress = parsedEvent.deviceAddress
			def child = getChildDevice(parsedEvent.mac)
			if (child) {
				child.sync(parsedEvent.networkAddress, parsedEvent.deviceAddress)
			}
		}
	} else {
    	log.debug "new device ssdpUSN ${ssdpUSN}"
		devices << ["${ssdpUSN}": parsedEvent]
	}
}

void deviceDescriptionHandler(physicalgraph.device.HubResponse hubResponse) {
	def body = hubResponse.xml
	def devices = getDevices()
	def device = devices.find { it?.key?.contains(body?.device-info?.udn?.text()) }
	if (device) {
		log.debug "device verified"
    	device.value << [name: body?.device-info?.user-device-name?.text(), 
        				 model:body?.device-info?.model-number?.text(), 
                         serialNumber:body?.device-info?.serial-number?.text(), verified: true]
	}
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private Boolean canInstallLabs() {
	return hasAllHubsOver("000.011.00603")
}
private Boolean hasAllHubsOver(String desiredFirmware)
{
	return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private List getRealHubFirmwareVersions()
{
	return location.hubs*.firmwareVersionString.findAll { it }
}