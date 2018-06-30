/**
 *  Motion and time control
 *
 *  Copyright 2017 Geoff Farmer
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
import groovy.time.TimeCategory

definition(
    name: "Motion, light-level and time control",
    namespace: "geofffarmer",
    author: "Geoff Farmer",
    description: "Turn on off lights when motion or light-level at night with brightness dependent on time",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
	page(name: "outputsPage", title: "Outputs", nextPage: "inputsPage", uninstall: true) {
        section("Items to switch on and off") {
            paragraph "What should be switched on and off?"
            input "theSwitchLevels", "capability.switchLevel", title: "Dimmable lights", multiple: true
        }
    }
    page(name: "inputsPage", title: "Motion inputs", nextPage: "overridesPage", uninstall: true) {
    	section("Motion sensors") {
            paragraph "Which motion sensors should be the trigger?"
            input "theMotionSensors", "capability.motionSensor", title: "Motion Sensors", multiple: true
        }
        section("Delay") {
        	paragraph "How long after motion ends before turning off lights?"
            input(name: "theDelay", type: "number", range: "0..60", title: "Delay (Minutes)", required: true)
        }
    }
    
    page(name: "activeTimesPage")
    
    page(name: "overridesPage")
    
    page(name: "lowLightTimes", title: "Low light times", nextPage: "lightLevels", uninstall: true) {
    	section("Start time") {
        	paragraph "At what time should low light start?"
            input(name: "lowLightStartTime", type: "time", title: "Start time", required: true)
        }
    	section("End time") {
        	paragraph "At what time should low light end?"
            input(name: "lowLightEndTime", type: "time", title: "End time", required: true)
        }
    }
    
    page(name: "lightLevels", title: "Light brightness levels", nextPage: "nameAndMode", uninstall: true) {
    	section("Low light level") {
        	paragraph "What brightness should be set at low light times?"
            input(name: "lowLightLevel", type: "number", range: "0..100", title: "Low light level (0 to 100)", required: true)
        }
    	section("High light level") {
        	paragraph "What brightness should be set at high light times??"
            input(name: "highLightLevel", type: "number", range: "0..100", title: "High light level (0 to 100)", required: true)
        }
    }
        
    page(name: "nameAndMode", title: "Name app and configure modes", install: true, uninstall: true) {
        section("General settings") {
            label title: "Assign a name", required: false
            mode title: "Set for specific mode(s)", required: false
            icon title: "Icon", required: false
        }
    }
}

def overridesPage() {
	dynamicPage(name: "overridesPage", title: "Overrides", nextPage: "activeTimesPage", uninstall: true) {
        section("Only run if another device is on") {
        	paragraph "Should this automation only run if another device is already on?"
        	input(name: "onlyRunIfAnotherDeviceOn", type: "enum", title: "Select", options: ["yes":"Yes", "no":"No"], submitOnChange: true)
            if (onlyRunIfAnotherDeviceOn == "yes") {
            	input "theOnSwitches", "capability.switch", title: "Switches", multiple: true
            }
        }
        section("Only run if other devices are off") {
        	paragraph "Should this automation only run if selected devices are all off?"
        	input(name: "onlyRunIfAllDeviceOff", type: "enum", title: "Select", options: ["yes":"Yes", "no":"No"], submitOnChange: true)
            if (onlyRunIfAllDeviceOff == "yes") {
            	input "theOffSwitches", "capability.switch", title: "Switches", multiple: true
            }
        }
    }
}

def activeTimesPage() {
	dynamicPage(name: "activeTimesPage", title: "Active Times", nextPage: "lowLightTimes", uninstall: true) {
        section("Start time") {
        	paragraph "When should this automation start?"
        	input(name: "startTimeType", type: "enum", title: "Start time type?", options: ["sunset":"Sunset", "specificTime":"Speccific time","lightLevel":"Light level"], submitOnChange: true)
            switch (startTimeType) {
                case "sunset":
                    input(name: "sunsetStartOffset", type: "number", title: "Minutes offset", required: true)
                    break
                case "specificTime":
                    input(name: "specificStartTime", type: "time", title: "Specific time", required: true)
                    break
                case "lightLevel":
                    input "theLightLevel1", "capability.illuminanceMeasurement", title: "Light level sensor", multiple: false
                    input(name: "luxStartLevel", type: "number", title: "Light level (lux)", required: true)
                    break
            }
        }
        section("End time") {
        	paragraph "When should this automation end?"
        	input(name: "endTimeType", type: "enum", title: "End time type?", options: ["sunrise":"Sunrise", "specificTime":"Speccific time","lightLevel":"Light Level"], submitOnChange: true)
            switch (endTimeType) {
                case "sunrise":
                    input(name: "sunriseEndOffset", type: "number", title: "Minutes offset", required: true)
                    break
                case "specificTime":
                    input(name: "specificEndTime", type: "time", title: "Specific time", required: true)
                    break
                case "lightLevel":
                    input "theLightLevel2", "capability.illuminanceMeasurement", title: "Light level sensor", multiple: false
                    input(name: "luxEndLevel", type: "number", title: "Light level (lux)", required: true)
                    break
            }
        }
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(theMotionSensors, "motion", motionHandler)
    // subscribe(theOnSwitches, "switch.on", eventHandler)
}

def motionHandler(evt) {
    def startTime = getSunriseAndSunset().sunset + sunsetStartOffset.minutes
    def endTime = getSunriseAndSunset().sunrise + sunriseEndOffset.minutes
	def luxLevelLow = false
	switch (startTimeType) {
    	case "specificTime":
        	startTime = specificStartTime
            break
        case "sunset":
        	use(TimeCategory) {
        		startTime = getSunriseAndSunset().sunset + sunsetStartOffset.minutes
            }
        	break
        case "lightLevel":
    		if (theLightLevel1.currentIlluminance < luxStartLevel) {
    			luxLevelLow = true
    		}
        	break
    }
	switch (endTimeType) {
    	case "specificTime":
        	endTime = specificEndTime
            break
        case "sunrise":
        	use(TimeCategory) {
        		endTime = getSunriseAndSunset().sunrise + sunriseEndOffset.minutes
            }
        	break
        case "lightLevel":
    		if (theLightLevel2.currentIlluminance > luxEndLevel) {
    			luxLevelLow = false
    		}
        	break
    }
    def active = luxLevelLow || !timeOfDayIsBetween(endTime, startTime, new Date(), location.timeZone)
    def lowLight = !timeOfDayIsBetween(lowLightEndTime, lowLightStartTime, new Date(), location.timeZone)
    if ((active && noOverride()) || state.switchOffPending) {
    	if (noMotion()) {
        	runIn(60 * theDelay, turnOffLights)
        } else {
        	if (active && noOverride()) {
            	if (lowLight) {
                	if (lowLightLevel != 0) {
                		theSwitchLevels.setLevel(lowLightLevel)
            			state.switchOffPending = true
            			unschedule()
                    }
            	} else {
                	theSwitchLevels.setLevel(highLightLevel)
            		state.switchOffPending = true
            		unschedule()
            	}
        	}
        }
    }
}

def turnOffLights() {
	theSwitchLevels.setLevel(0)
    state.switchOffPending = false
}

private noMotion() {
    def result = true
    theMotionSensors.each {n -> if (n.currentValue("motion") == "active") result = false }
	return result
}

private noOverride() {
    def result = false
    if (onlyRunIfAnotherDeviceOn == "no") {
    	result = true
    } else {
    	theOnSwitches.each {n -> if (n.currentValue("switch") == "on") result = true }
    }
    if (onlyRunIfAllDeviceOff == "yes") {
    	theOffSwitches.each {n -> if (n.currentValue("switch") == "on") result = false }
    }
	return result
}