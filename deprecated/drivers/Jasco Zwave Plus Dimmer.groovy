/**
 *  GE/Jasco Z-Wave Plus Dimmer Switch
 *
 *  Copyright 2017 Chris Nussbaum
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
 *	Author: Chris Nussbaum
 *	Date: 04/19/2017
 *
 *	Changelog:
 *
 *  0.16 (08/03/2017) - Fix bug with status not getting updated when turned on/off from SmartThings
 *  0.15 (04/28/2017) - Fix bug with setting level to 100%
 *  0.14 (04/24/2017) - Fix bug in setting and refreshing dimmer delays
 *  0.13 (04/23/2017) - Fix bug with button press events
 *  0.12 (04/23/2017) - Fix bug with reporting back dimmer levels
 *  0.11 (04/23/2017) - Fix bug in configure() command that was preventing devices from joining properly
 *  0.10 (04/19/2017) -	Initial 0.1 Beta.
 *
 *
 *   Button Mappings:
 *
 *   ACTION          BUTTON#    BUTTON ACTION
 *   Double-Tap Up     1        pressed
 *   Double-Tap Down   2        pressed
 *
 *	Edits by Stephan Hackett
 *	- removed unneeded tiles and simulator sections
 *	- switchMultiLevel V3 changed to V2 (hubitat compatible)
 *	- changed button push code to be hubitat compliant
 *	- replaced physicalgraph with hubitat
 *
 */
metadata {
	definition (name: "GE/Jasco Z-Wave Plus Dimmer Switch", namespace: "nuttytree", author: "Chris Nussbaum") {
		capability "Actuator"
		capability "PushableButton"		
		capability "Configuration"
		capability "Health Check"
		capability "Indicator"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
		capability "Switch Level"

		attribute "inverted", "enum", ["inverted", "not inverted"]
        attribute "zwaveSteps", "number"
        attribute "zwaveDelay", "number"
        attribute "manualSteps", "number"
        attribute "manualDelay", "number"
        attribute "allSteps", "number"
        attribute "allDelay", "number"
        
        command "doubleUp"
        command "doubleDown"
        command "inverted"
        command "notInverted"
        command "levelUp"
        command "levelDown"
        command "setZwaveSteps"
        command "setZwaveDelay"
        command "setManualSteps"
        command "setManualDelay"
        command "setAllSteps"
        command "setAllDelay"
        
        // These include version because there are older firmwares that don't support double-tap or the extra association groups
        fingerprint mfr:"0063", prod:"4944", model:"3038", ver: "5.26", deviceJoinName: "GE Z-Wave Plus Wall Dimmer"
        fingerprint mfr:"0063", prod:"4944", model:"3039", ver: "5.19", deviceJoinName: "GE Z-Wave Plus 1000W Wall Dimmer"
        fingerprint mfr:"0063", prod:"4944", model:"3130", ver: "5.21", deviceJoinName: "GE Z-Wave Plus Toggle Dimmer"
        fingerprint mfr:"0063", prod:"4944", model:"3135", ver: "5.26", deviceJoinName: "Jasco Z-Wave Plus Wall Dimmer"
        fingerprint mfr:"0063", prod:"4944", model:"3136", ver: "5.21", deviceJoinName: "Jasco Z-Wave Plus 1000W Wall Dimmer"
        fingerprint mfr:"0063", prod:"4944", model:"3137", ver: "5.20", deviceJoinName: "Jasco Z-Wave Plus Toggle Dimmer"
	}

    
    preferences {
        input (
            type: "paragraph",
            element: "paragraph",
            title: "Configure Association Groups:",
            description: "Devices in association group 2 will receive Basic Set commands directly from the switch when it is turned on or off. Use this to control another device as if it was connected to this switch.\n\n" +
                         "Devices in association group 3 will receive Basic Set commands directly from the switch when it is double tapped up or down.\n\n" +
                         "Devices are entered as a comma delimited list of IDs in hexadecimal format."
        )

        input (
            name: "requestedGroup2",
            title: "Association Group 2 Members (Max of 5):",
            type: "text",
            required: false
        )

        input (
            name: "requestedGroup3",
            title: "Association Group 3 Members (Max of 4):",
            type: "text",
            required: false
        )
    }

}

// parse events into attributes
def parse(String description) {
    log.debug "description: $description"
    def result = null
    def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x26: 2, 0x56: 1, 0x70: 2, 0x72: 2, 0x85: 2])
    if (cmd) {
        result = zwaveEvent(cmd)
        log.debug "Parsed ${cmd} to ${result.inspect()}"
    } else {
        log.debug "Non-parsed event: ${description}"
    }
    result    
}

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	log.debug("zwaveEvent(): CRC-16 Encapsulation Command received: ${cmd}")
	def encapsulatedCommand = zwave.commandClass(cmd.commandClass)?.command(cmd.command)?.parse(cmd.data)
	if (!encapsulatedCommand) {
		log.warn("zwaveEvent(): Could not extract command from ${cmd}")
	} else {
		log.debug("zwaveEvent(): Extracted command ${encapsulatedCommand}")
        return zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	dimmerEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelReport cmd) {
	dimmerEvents(cmd)
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv2.SwitchMultilevelSet cmd) {
	dimmerEvents(cmd)
}

private dimmerEvents(hubitat.zwave.Command cmd) {
	def value = (cmd.value ? "on" : "off")
	def result = [createEvent(name: "switch", value: value, type: "physical")]
	if (cmd.value && cmd.value <= 100) {
		result << createEvent(name: "level", value: cmd.value, unit: "%", type: "physical")
	}
	return result
}


def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	if (cmd.value == 255) {
    	createEvent(name: "pushed", value: 1, descriptionText: "Double-tap up (button 1) on $device.displayName", isStateChange: true, type: "physical")
    }
	else if (cmd.value == 0) {
    	createEvent(name: "pushed", value: 2, descriptionText: "Double-tap down (button 2) on $device.displayName", isStateChange: true, type: "physical")
    }
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	log.debug "---ASSOCIATION REPORT V2--- ${device.displayName} sent groupingIdentifier: ${cmd.groupingIdentifier} maxNodesSupported: ${cmd.maxNodesSupported} nodeId: ${cmd.nodeId} reportsToFollow: ${cmd.reportsToFollow}"
    state.group3 = "1,2"
    if (cmd.groupingIdentifier == 3) {
    	if (cmd.nodeId.toString().contains(zwaveHubNodeId.toString())) {
        	createEvent(name: "numberOfButtons", value: 2, displayed: false)
        }
        else {
        	sendEvent(name: "numberOfButtons", value: 0, displayed: false)
			sendHubCommand(new hubitat.device.HubAction(zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: zwaveHubNodeId).format()))
			sendHubCommand(new hubitat.device.HubAction(zwave.associationV2.associationGet(groupingIdentifier: 3).format()))
        }
    }
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
    log.debug "---CONFIGURATION REPORT V2--- ${device.displayName} sent ${cmd}"
	def name = ""
    def value = ""
    def reportValue = cmd.scaledConfigurationValue
    switch (cmd.parameterNumber) {
        case 3:
            name = "indicatorStatus"
            value = reportValue == 1 ? "when on" : reportValue == 2 ? "never" : "when off"
            break
        case 4:
            name = "inverted"
            value = reportValue == 1 ? "true" : "false"
            break
        case 7:
            name = "zwaveSteps"
            value = reportValue
            break
        case 8:
            name = "zwaveDelay"
            value = reportValue
            break
        case 9:
            name = "manualSteps"
            value = reportValue
            break
        case 10:
            name = "manualDelay"
            value = reportValue
            break
        case 11:
            name = "allSteps"
            value = reportValue
            break
        case 12:
            name = "allDelay"
            value = reportValue
            break
        default:
            break
    }
	createEvent([name: name, value: value, displayed: false])
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    log.debug "---MANUFACTURER SPECIFIC REPORT V2---"
	log.debug "manufacturerId:   ${cmd.manufacturerId}"
	log.debug "manufacturerName: ${cmd.manufacturerName}"
    state.manufacturer=cmd.manufacturerName
	log.debug "productId:        ${cmd.productId}"
	log.debug "productTypeId:    ${cmd.productTypeId}"
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	updateDataValue("MSR", msr)	
    sendEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	updateDataValue("fw", fw)
	log.debug "---VERSION REPORT V1--- ${device.displayName} is running firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
}


def zwaveEvent(hubitat.zwave.Command cmd) {
    log.warn "${device.displayName} received unhandled command: ${cmd}"
}

// handle commands
def configure() {
    def cmds = []
    // Get current config parameter values
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 4).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 7).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 8).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 9).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 10).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 11).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 12).format()
    
    // Add the hub to association group 3 to get double-tap notifications
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: zwaveHubNodeId).format()
    
    delayBetween(cmds,500)
}

def updated() {
    if (state.lastUpdated && now() <= state.lastUpdated + 3000) return
    state.lastUpdated = now()

	def nodes = []
    def cmds = []

	if (settings.requestedGroup2 != state.currentGroup2) {
        nodes = parseAssocGroupList(settings.requestedGroup2, 2)
        cmds << zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: [])
        cmds << zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: nodes)
        cmds << zwave.associationV2.associationGet(groupingIdentifier: 2)
        state.currentGroup2 = settings.requestedGroup2
    }

    if (settings.requestedGroup3 != state.currentGroup3) {
        nodes = parseAssocGroupList(settings.requestedGroup3, 3)
        cmds << zwave.associationV2.associationRemove(groupingIdentifier: 3, nodeId: [])
        cmds << zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: nodes)
        cmds << zwave.associationV2.associationGet(groupingIdentifier: 3)
        state.currentGroup3 = settings.requestedGroup3
    }

	sendHubCommand(cmds.collect{ new hubitat.device.HubAction(it.format()) }, 500)
}

def indicatorWhenOn() {
	sendEvent(name: "indicatorStatus", value: "when on", display: false)
	zwave.configurationV2.configurationSet(configurationValue: [1], parameterNumber: 3, size: 1).format()
}

def indicatorWhenOff() {
	sendEvent(name: "indicatorStatus", value: "when off", display: false)
	zwave.configurationV2.configurationSet(configurationValue: [0], parameterNumber: 3, size: 1).format()
}

def indicatorNever() {
	sendEvent(name: "indicatorStatus", value: "never", display: false)
	zwave.configurationV2.configurationSet(configurationValue: [2], parameterNumber: 3, size: 1).format()
}

def inverted() {
	sendEvent(name: "inverted", value: "inverted", display: false)
	zwave.configurationV2.configurationSet(configurationValue: [1], parameterNumber: 4, size: 1).format()
}

def notInverted() {
	sendEvent(name: "inverted", value: "not inverted", display: false)
	zwave.configurationV2.configurationSet(configurationValue: [0], parameterNumber: 4, size: 1).format()
}

def doubleUp() {
	sendEvent(name: "pushed", value: 1, descriptionText: "Double-tap up (button 1) on $device.displayName", isStateChange: true, type: "digital")
}

def doubleDown() {
	sendEvent(name: "pushed", value: 2, descriptionText: "Double-tap down (button 2) on $device.displayName", isStateChange: true, type: "digital")
}

def setZwaveSteps(steps) {
	steps = Math.max(Math.min(steps, 99), 1)
	sendEvent(name: "zwaveSteps", value: steps, displayed: false)	
	zwave.configurationV2.configurationSet(scaledConfigurationValue: steps, parameterNumber: 7, size: 1).format()
}

def setZwaveDelay(delay) {
	delay = Math.max(Math.min(delay, 255), 1)
	sendEvent(name: "zwaveDelay", value: delay, displayed: false)
	sendHubCommand(new hubitat.device.HubAction(zwave.configurationV2.configurationSet(scaledConfigurationValue: delay, parameterNumber: 8, size: 2).format()))
}

def setManualSteps(steps) {
	steps = Math.max(Math.min(steps, 99), 1)
	sendEvent(name: "manualSteps", value: steps, displayed: false)	
	zwave.configurationV2.configurationSet(scaledConfigurationValue: steps, parameterNumber: 9, size: 1).format()
}

def setManualDelay(delay) {
	delay = Math.max(Math.min(delay, 255), 1)
	sendEvent(name: "manualDelay", value: delay, displayed: false)
	zwave.configurationV2.configurationSet(scaledConfigurationValue: delay, parameterNumber: 10, size: 2).format()
}

def setAllSteps(steps) {
	steps = Math.max(Math.min(steps, 99), 1)
	sendEvent(name: "allSteps", value: steps, displayed: false)	
	zwave.configurationV2.configurationSet(scaledConfigurationValue: steps, parameterNumber: 11, size: 1).format()
}

def setAllDelay(delay) {
	delay = Math.max(Math.min(delay, 255), 1)
	sendEvent(name: "allDelay", value: delay, displayed: false)
	zwave.configurationV2.configurationSet(scaledConfigurationValue: delay, parameterNumber: 12, size: 2).format()
}

def poll() {
	def cmds = []
    cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
	if (getDataValue("MSR") == null) {
		cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	}
	delayBetween(cmds,500)
}

def ping() {
	refresh()
}

def refresh() {
	def cmds = []
	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 4).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 7).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 8).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 9).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 10).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 11).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 12).format()
    cmds << zwave.associationV2.associationGet(groupingIdentifier: 3).format()
	if (getDataValue("MSR") == null) {
		cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	}
	delayBetween(cmds,500)
}

def on() {
	def cmds = []
    cmds << zwave.basicV1.basicSet(value: 0xFF).format()
   	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
    def delay = (device.currentValue("zwaveSteps") * device.currentValue("zwaveDelay")).longValue() + 1000
    delayBetween(cmds, delay)
}

def off() {
	def cmds = []
    cmds << zwave.basicV1.basicSet(value: 0x00).format()
   	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
    def delay = (device.currentValue("zwaveSteps") * device.currentValue("zwaveDelay")).longValue() + 1000
    delayBetween(cmds, delay)
}

def setLevel(value) {
	def valueaux = value as Integer
	def level = Math.max(Math.min(valueaux, 99), 0)
	if (level > 0) {
		sendEvent(name: "switch", value: "on")
	} else {
		sendEvent(name: "switch", value: "off")
	}
	sendEvent(name: "level", value: level, unit: "%")
    def delay = (device.currentValue("zwaveSteps") * device.currentValue("zwaveDelay") * level / 100).longValue() + 1000
	delayBetween ([
    	zwave.basicV1.basicSet(value: level).format(),
        zwave.switchMultilevelV1.switchMultilevelGet().format()
    ], delay )
}

def setLevel(value, duration) {
	log.debug "setLevel >> value: $value, duration: $duration"
	def valueaux = value as Integer
	def level = Math.max(Math.min(valueaux, 99), 0)
	def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
	def getStatusDelay = duration < 128 ? (duration*1000)+2000 : (Math.round(duration / 60)*60*1000)+2000
	delayBetween ([zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: dimmingDuration).format(),
				   zwave.switchMultilevelV1.switchMultilevelGet().format()], getStatusDelay)
}

def levelUp() {
    int nextLevel = device.currentValue("level") + 10
    if( nextLevel > 100) {
    	nextLevel = 100
    }
    setLevel(nextLevel)
}
	
def levelDown() {
    int nextLevel = device.currentValue("level") - 10
    if( nextLevel < 0) {
    	nextLevel = 0
    }
    if (nextLevel == 0) {
    	off()
    }
    else {
	    setLevel(nextLevel)
    }
}

// Private Methods

private parseAssocGroupList(list, group) {
    def nodes = group == 2 ? [] : [zwaveHubNodeId]
    if (list) {
        def nodeList = list.split(',')
        def max = group == 2 ? 5 : 4
        def count = 0

        nodeList.each { node ->
            node = node.trim()
            if ( count >= max) {
                log.warn "Association Group ${group}: Number of members is greater than ${max}! The following member was discarded: ${node}"
            }
            else if (node.matches("\\p{XDigit}+")) {
                def nodeId = Integer.parseInt(node,16)
                if (nodeId == zwaveHubNodeId) {
                	log.warn "Association Group ${group}: Adding the hub as an association is not allowed (it would break double-tap)."
                }
                else if ( (nodeId > 0) & (nodeId < 256) ) {
                    nodes << nodeId
                    count++
                }
                else {
                    log.warn "Association Group ${group}: Invalid member: ${node}"
                }
            }
            else {
                log.warn "Association Group ${group}: Invalid member: ${node}"
            }
        }
    }
    
    return nodes
}
