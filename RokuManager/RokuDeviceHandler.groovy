/**
 *  Roku
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
metadata {
	definition (name: "Roku", namespace: "madmouse", author: "Leslie Drewery") {
    	capability "Actuator"
		capability "Sensor"
        capability "Refresh"             
        capability "Switch"
        capability "Media Controller"
        

        attribute "activeAppId", "String"
        attribute "activeAppUpdated", "String"
        attribute "supportsVolume", "String"
        attribute "deviceInfo", "String"
        attribute "activeApp", "String"
        attribute "activityList", "String"
        
		command "homeButton"
        command "selectButton"
        command "previousButton"
        command "nextButton"
        command "startActivityWithContent", ["String","String"]
        command "pressKey", ["String"]
	}

	tiles(scale: 2) {
		multiAttributeTile(name: "main", type:"mediaPlayer", width:6, height:3, canChangeIcon: true) {
			tileAttribute("device.status", key: "PRIMARY_CONTROL") {
				attributeState("default", action:"selectButton")
			}
			tileAttribute("device.status", key: "MEDIA_STATUS") {
				attributeState("default", action:"selectButton",label:"${currentValue}")
			}

			tileAttribute("device.status", key: "PREVIOUS_TRACK") {
				attributeState("default", action:"previousButton")
			}
			
            tileAttribute("device.status", key: "NEXT_TRACK") {
				attributeState("default", action:"nextButton")
			}

			tileAttribute("device.trackDescription", key: "MARQUEE") {
				attributeState("default", label:"${currentValue}", backgroundColor:"#00ffff")
                state "Roku", label:'${currentValue}', backgroundColor:"#6600ff"
                state "Netflix", label:'${currentValue}', backgroundColor:"#ff0000"
                state "Play Movies", label:'${currentValue}', backgroundColor:"#ff0000"
                state "Amazon Video", label:'${currentValue}', backgroundColor:"#6600ff"
            
           }
		}


		standardTile("refresh", "device.status",  width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh", backgroundColor:"#ffffff"
         
		}

		tiles {
    		valueTile("model", "device.model", decoration: "flat", width: 4, height: 1) {
           		state "default", label:'Model : ${currentValue}'
    		}
            
            valueTile("userdevicename", "device.userdevicename", decoration: "flat", width: 4, height: 1) {
           		state "default", label:'Name : ${currentValue}'
    		}
		}
        
		standardTile("homeButton", "device.homeButton",  width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:"", action:"homeButton", icon:"st.Home.home2", backgroundColor:"#ffffff"
         
		}

		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821"
			state "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff"
		}
		tiles {
    		valueTile("softwareVersion", "device.softwareversion", decoration: "flat", width: 4, height: 1) {
           		state "default", label:'Version : ${currentValue}'
    		}
            
            valueTile("deviceId", "device.deviceid", decoration: "flat", width: 4, height: 1) {
           		state "default", label:'ID : ${currentValue}'
    		}
            
            valueTile("channels", "device.channels", decoration: "flat", width: 4, height: 1) {
           		state "default", label:'${currentValue}', action:"mediaController.activities"
    		}
		}

 		main "main"

		details(["main","refresh","userdevicename","model","homeButton","softwareVersion","deviceId","switch","channels"])
	}

}

// parse events into attributes
def parse(description) {
    def msg = parseLanMessage(description)
    def eventObj
    if(msg.xml){
    	try {
        	//Handle Device Info Requests.
        	if(msg.body.contains("<device-info>")){
            	eventObj = parseDeviceInfo(msg.xml)
                //Save Message body to deviceInfo Attribute
                sendEvent(name: "deviceInfo",value: msg.body,isStateChange:true)
            }

			//Handle Currently Active App Requests.
			if(msg.body.contains("<active-app>")){
                eventObj = parseActiveApp(msg.xml);
                //Save Message body to activeApp Attribute
                sendEvent(name: "activeApp",value: msg.body,isStateChange:true)
            }

			//Handle List of Installed App Requests.
            if(msg.body.contains("<apps>")){
                eventObj = parseApps(msg.xml);
                //Save Message body to activityList Attribute
                sendEvent(name: "activityList",value: msg.body)
            }
        }catch (Throwable t) {
            //results << createEvent(name: "parseError", value: "$t")
            eventObj = createEvent(name: "parseError", value: "$t", description: description)
            throw t
        }
    }
    return eventObj
}

def installed() {
	rokuDeviceInfoAction()
	getCurrentActivity();    
}

def refresh() {
 	rokuDeviceInfoAction()
	getCurrentActivity(); 
    getAllActivities()
}

/*** rokuDeviceInfoAction -> parse -> parseDeviceInfo
	Retrieves device information similar to that returned by roDeviceInfo. 

	This command is accessed using an HTTP GET.
    
    <?xml version="1.0" encoding="UTF-8" ?>
    <device-info>
      <udn>xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx</udn>
      <serial-number>xxxxxxxxxxxx</serial-number>
      <device-id>xxxxxxxxxxxx</device-id>
      <vendor-name>Roku</vendor-name>
      <model-number>3100X</model-number>
      <model-name>Roku 2 XS</model-name>
      <wifi-mac>xx:xx:xx:xx:xx:xx</wifi-mac>
      <ethernet-mac>xx:xx:xx:xx:xx:xx</ethernet-mac>
      <network-type>wifi</network-type>
      <user-device-name>Main Room</user-device-name>
      <software-version>7.10</software-version>
      <software-build>04067</software-build>
      <secure-device>true</secure-device>
      <language>en</language>
      <country>GB</country>
      <locale>en_GB</locale>
      <time-zone>Europe/United Kingdom</time-zone>
      <time-zone-offset>60</time-zone-offset>
      <power-mode>PowerOn</power-mode>
      <developer-enabled>false</developer-enabled>
      <keyed-developer-id/>
      <search-enabled>true</search-enabled>
      <voice-search-enabled>true</voice-search-enabled>
      <notifications-enabled>true</notifications-enabled>
      <notifications-first-use>true</notifications-first-use>
      <headphones-connected>false</headphones-connected>
    </device-info>
***/
private rokuDeviceInfoAction() {
    String host = getHostAddress()
    log.info("rokuDeviceInfoAction ${host}")
	def hubAction = new physicalgraph.device.HubAction("""GET /query/device-info HTTP/1.1\r\nHOST: $host\r\n\r\n""", 
    													physicalgraph.device.Protocol.LAN, 
                                                        host)
	sendHubCommand(hubAction)
}
private parseDeviceInfo(bodyXml){
	def model = bodyXml.'model-name'
    sendEvent(name: "model",value: model.text())
    
    def uName = bodyXml.'user-device-name'
    sendEvent(name: "userdevicename",value: uName.text())

	def power = bodyXml.'power-mode'
    sendEvent(name: "switch", value: power.text()=="PowerOn" ? "on" : "off", displayed: false)   
    
   	def version = bodyXml.'software-version'
    def build = bodyXml.'software-build'
    sendEvent(name: "softwareversion", value: "${version.text()}-${build.text()}")   
    
    def deviecId = bodyXml.'device-id'
    sendEvent(name: "deviceid", value: "${deviecId.text()}")   
    return createEvent(name:"activities", value : channelCount, description:description, isStateChange:true);
}

/*** rokuAppAction -> parse -> parseApps
	Get A list of all Applications installed on the device.
    
    Returns a map of all the channels installed on the Roku device paired with their application ID. 
    This command is accessed using an HTTP GET.
    
    <?xml version="1.0" encoding="UTF-8" ?>
    <apps>
  		<app id="12" type="appl" version="3.1.6041">Netflix</app>
	</apps>
***/
private rokuAppAction() {
    String host = getHostAddress()
	def hubAction = new physicalgraph.device.HubAction("""GET /query/apps HTTP/1.1\r\nHOST: $host\r\n\r\n""", 
    													physicalgraph.device.Protocol.LAN, 
                                                        host)
	sendHubCommand(hubAction)

}
private parseApps(xmlBody) {
	def channelCount = xmlBody.'app'.size();
    //Update Channel Tile Value.
    sendEvent(name: "channels",value: "Channels (${channelCount})")
    //Create Activities Event.
    return createEvent(name:"activities", value : channelCount, description:description, isStateChange:true);
}

/*** rokuActiveAppAction -> Parse -> parseActiveApp
	Get Details of Current App active in the Device.
    
	Returns a child element named 'app' that identifies the active application, 
    in the same format as 'query/apps'. 
    
    If no application is active, such as when the user is in the homescreen, 
    the element only contains "Roku". 
    
    If a screensaver is active, a second element will be included containing "screensaver". 
    If the screensaver is an application-provided or plug-in screensaver, 
    the same information is provided as 'query/apps'. 
    
    If the screensaver is active, 
    but is not running (such as due to system limitations), 
    the screensaver element contains "black". 
    This command is accessed using an HTTP GET.
    
   	Device on Home screen with Screen saver.
    <?xml version="1.0" encoding="UTF-8" ?>
    <active-app>
  		<app>Roku</app>
  		<screensaver id="55545" type="ssvr" version="2.0.1">Default screensaver</screensaver>
	</active-app>
    
    Device is Running Netflix.
    <?xml version="1.0" encoding="UTF-8" ?>
    <active-app>
  		<app id="12" type="appl" version="3.1.6041">Netflix</app>
	</active-app>
***/
private rokuActiveAppAction() {
    String host = getHostAddress()
	def hubAction = new physicalgraph.device.HubAction("""GET /query/active-app HTTP/1.1\r\nHOST: $host\r\n\r\n""", 
    													physicalgraph.device.Protocol.LAN, 
                                                        host)
	sendHubCommand(hubAction)
}
private parseActiveApp(body){
	def currentAppId = device.currentValue("activeAppId")
    def newAppName = body.app
    def newAppId = body.app.@id
    def eventToFire
    if(newAppId){
       if((!currentAppId) || (newAppId.text().compareTo(currentAppId) != 0)) {
            log.debug("activeAppHandler App Update ${currentAppId} -> ${newAppId.text()}")
            //Update the activeAppId Attribute 
            sendEvent(name: "activeAppId",value: newAppId.text())
            //Update the trackDescription Tile 
            sendEvent(name: "trackDescription",value: newAppName.text())
            //Update the Status to test background colors.
            sendEvent(name: "status", value: newAppName.text())
            //Create the Event to fire at the end of parse method.
            eventToFire = createEvent(name:"currentActivity", value : newAppName.text(), description:description,isStateChange:true);
        }
    }
    return eventToFire;
}

/*** rokuLaunchAction
	Launches the channel identified by appName and if the Application supports contentId it will start the content. 
    
    Eg Start Youtube Video
    http://<ip address>:8060/launch/837?contentID=aZtNi6QmA1Y
    
    You can follow the appID with a question mark and a list of URL parameters to be sent to the 
    application as an associative array, and passed to the RunUserInterface() or Main() entry point. 
    
    This command is sent using an HTTP POST with no body.
	
    The launch command should not be used to implement deep-linking to an uninstalled channel, 
    because it will fail to launch uninstalled channels. 
    
    Use the install command instead for uninstalled channels.
***/
private rokuLaunchAction(String appName, String contentId =null) {
	def channelList = device.currentValue("activityList");
    if(channelList){
        def appNode = new XmlSlurper().parseText(channelList).children().find{it.text()==appName}
        if(appNode){
            def appId = appNode.@id
            launchAppId(appId, contentId)
        }
    }
}

/*** launchAppId
	Launches the channel identified by appID and if the Application supports contentId it will start the content. 
    
    Eg Start Youtube Video
    http://<ip address>:8060/launch/837?contentID=aZtNi6QmA1Y
    
    You can follow the appID with a question mark and a list of URL parameters to be sent to the 
    application as an associative array, and passed to the RunUserInterface() or Main() entry point. 
    
    This command is sent using an HTTP POST with no body.
	
    The launch command should not be used to implement deep-linking to an uninstalled channel, 
    because it will fail to launch uninstalled channels. 
    
    Use the install command instead for uninstalled channels.
***/
def launchAppId(String appId, String contentId =null) {
    def urlText = "/launch/${appId}?contentId=${contentId}"
    if(!contentId){
        urlText = "/launch/${appId}"
    }

    def httpRequest = [
        method:		"POST",
        path: 		urlText,
        headers:	[
            HOST: getHostAddress(),
            Accept: 	"* /*",
        ],
    ]

    def hubAction = new physicalgraph.device.HubAction(httpRequest)
    sendHubCommand(hubAction)
}


/*** rokuKeyPressAppAction
	Equivalent to pressing down and releasing the remote control key identified after the slash. 
    You can also use this command, and the keydown and keyup commands, 
    to send keyboard alphanumeric characters when a keyboard screen is active, 
    as described in Keypress Key Values. 

	This command is sent using an HTTP POST with no body.
    
    Out of the Boxvalues
    Home, Rev, Fwd, Play, Select, Left, Right, Down, Up, Back, InstantReplay, Info, Backspace, +
	Search, Enter, 
    
    Some Roku devices, such as Roku TVs, also support:
	VolumeDown, VolumeMute, VolumeUp
    
    For Literal values like pins and passwords
    Lit_<char>
***/
private rokuKeyPressAppAction(String action) {
    def httpRequest = [
      	method:		"POST",
        path: 		"/keypress/" + action,
        headers:	[
        				HOST: getHostAddress(),
						Accept: 	"*/*",
                    ]
	]

	def hubAction = new physicalgraph.device.HubAction(httpRequest)
	sendHubCommand(hubAction)

}
//-------------- Custom Commands --------------//
/*** startActivityWithContent
	Launch the installed Application via it's Name.
    
    contentId is optional and might not be supported by the Roku Application.
***/
def startActivityWithContent(String appName,contentId = null){
	log.trace "startActivityWithContent Fired ${appName} - ${contentId}"
	rokuLaunchAction(appName, contentId)
    
    //Refresh Current Activity after 5 ish seconds 
    runIn(5, getCurrentActivity)
}
/*** pressKey
	Act as is a button is pressed on the remote.
    
    refer : to rokuKeyPressAppAction for keyValue options.
***/
def pressKey(keyValue){
	rokuKeyPressAppAction(keyValue)
}

/*** selectButton
	Act as if the OK button is pressed on the remote.
***/
def selectButton() {
	rokuKeyPressAppAction("Select")
    //Refresh Current Activity after 5 ish seconds 
    runIn(5, getCurrentActivity)
}
/*** homeButton
	Act as if the Home button is pressed on the remote.
   
    This is connected to play button in the top Panel.
***/
def homeButton() {
	rokuKeyPressAppAction("Home")
    //Refresh Current Activity after 5 ish seconds 
    runIn(5, getCurrentActivity)
}
/*** nextButton
	Act as if the Right Arrow button is pressed on the remote.
***/
def nextButton() {
	rokuKeyPressAppAction("Right")
}
/*** previousButton
	Act as if the Right Arrow button is pressed on the remote.
***/
def previousButton() {
	rokuKeyPressAppAction("Left")
}
//^^^^^^^^^^^^^^ Custom Commands ^^^^^^^^^^^^^^//

//-------------- Switch Commands --------------//
def on() {
    sendEvent(name: "switch", value: "on")
}

def off() {
    sendEvent(name: "switch", value: "off")
}
//^^^^^^^^^^^^^^ Switch Commands ^^^^^^^^^^^^^^//

//-------------- Media Controller Commands --------------//
/*** startActivity -> rokuLaunchAction
	Launch the installed Application via it's Name.
    contentId = null.
***/
def startActivity(String activity){
    log.trace "activity - ${activity}"
    rokuLaunchAction(activity);
    
    //Refresh Current Activity after 5 ish seconds 
    runIn(5, getCurrentActivity)
}
/*** getAllActivities
	Return XML channel list in string format.
    refer to rokuAppAction for XML format.
***/
def getAllActivities(){
	log.trace "getAllActivities"
    rokuAppAction()
}
/*** getCurrentActivity
	Return XML String of the currently running application.
    
    refer to rokuActiveAppAction XML for format.
***/
def getCurrentActivity(){
	log.trace "getCurrentActivity"
	rokuActiveAppAction(); 
}
//^^^^^^^^^^^^^^ Media Controller Commands ^^^^^^^^^^^^^^//

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress() {
  def existingIp = convertHexToIP(getDataValue("ip"))
  def existingPort = convertHexToInt(getDataValue("port"))
  return existingIp + ":" + existingPort
    
}

private dniFromUri(uri) {
	def segs = uri.replaceAll(/http:\/\/([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+)\/.+/,'$1').split(":")
	def nums = segs[0].split("\\.")
	(nums.collect{hex(it.toInteger())}.join('') + ':' + hex(segs[-1].toInteger(),4)).toUpperCase()
}

private hex(value, width=2) {
	def s = new BigInteger(Math.round(value).toString()).toString(16)
	while (s.size() < width) {
		s = "0" + s
	}
	s
}
