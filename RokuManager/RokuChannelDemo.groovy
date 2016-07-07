/**
 *  Roku Channel Change Monitor
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
    name: "Roku Channel Change Monitor",
    namespace: "madmouse",
    author: "Leslie Drewery",
    description: "Monitor the Roku Device for a channel Change",
    category: "",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {

	page(name : "selectMain")
    page(name : "selectActivities")
    
}
def selectMain(){
	dynamicPage(name: "selectMain", uninstall: true, install: true) {
        section("Title") {
            input "switches", "capability.switch", title: "Switches",multiple: true, required: false
            input "rokuDevices","capability.mediaController", title: "Rokus",multiple: false, required: false, submitOnChange: true
            href "selectActivities", title: "Select Activities", description: "Tap to set", required: true,  submitOnChange: true
        }
    }
}


def selectActivities(){
	if(rokuDevices){
		dynamicPage(name: "selectActivities", uninstall: true, install: true) {
    		section() {
    		    def activities = getActivitiesOptions()
            	input "activities", "enum", title: "Activities",multiple: false, required: false, options : activities
            }
         }
	}
}

def getActivitiesOptions(){
	
    def channels = []
    if(rokuDevices){
    	def xmlChannels = rokuDevices.currentActivityList;
		log.debug "channels : " + xmlChannels;
		def appNode = new XmlSlurper().parseText(xmlChannels)
    	log.debug "Channels ${appNode}"
    	appNode.children().each{it -> channels << it.text()}
    }
    return channels;
    
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
    subscribeEvents()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
    subscribeEvents()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
}

def subscribeEvents(){
	subscribe(switches, "switch", switchEventHandler)
    subscribe(rokuDevices, "activities", mcEventHandler)
    subscribe(rokuDevices, "activeApp", mcEventAPHandler)
    subscribe(rokuDevices, "activityList", mcEventDIHandler)
    
}

def switchEventHandler(evt) {
	log.debug "Switch ${getEventDetails(evt,"AC")}"
}

def mcEventAPHandler(evt) {
	log.debug "mcEventAPHandler ${getEventDetails(evt,"AP")}"
//    log.debug "mcEventDIHandler ${rokuDevices.getActiveApp}"
}

def mcEventDIHandler(evt) {
	log.debug "mcEventDIHandler ${getEventDetails(evt,"DI")}"
}


def mcEventHandler(evt) {
	log.debug "MediaController ${evt.description}"
}
def getCurrentValue(dataType, currentValue){
	if (dataType == "VECTOR3"){
    	return currentValue.toString()
    }
    return currentValue
}

def getDeviceDetails(device){
    def jsonResp = [:]
    jsonResp = [id: device.id, 
        		name: device.displayName, 
                internal_name: device.name, 
                label: device.label,
                hubId: device.hub.id,
                capabilities: device.capabilities.collect{cap -> [
        		attribute: cap.attributes.collect {attr -> [name:attr.name, datatype:attr.dataType, current_value:getCurrentValue(attr.dataType,device.currentValue(attr.name))]}
                //,
                //commands: cap.commands.collect {comm -> [name:comm.name, arguments:comm.arguments]}
                ]}]
    return jsonResp
}
def getEventDetails(event, handler){
	def jsonResp = [id: event.id.toString(), 
        		name: event.name, 
                handler : handler,
                date: event.date.toString(),
//                data: event.data,
                dateValue: event.dateValue.toString(),
                description: event.description,
                descriptionText: event.descriptionText,
                device: getDeviceDetails(event.device),
                deviceId: event.device.id,
                displayName: event.displayName,
                installedSmartAppId: event.installedSmartAppId,
                isDigital: event.isDigital(),
                isPhysical: event.isPhysical(),
                isoDate: event.isoDate,
                isStateChange: event.isStateChange(),
                source: event.source,
                hubId:event.hubId,
                locationId:event.locationId,
                unit:event.unit]       
	return jsonResp;      
}
// TODO: implement event handlers