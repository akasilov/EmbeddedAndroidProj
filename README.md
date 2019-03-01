# Firefly Web Control

## Overview
                                                                                                
### Authors
- Oleksiy Kasilov
- Gerber Matthias
### Project name
- Firefly Web Control
### Project files
- EmbeddedAndroidProj.tar.xz (Android Studio Project, Git: https://github.com/akasilov/EmbeddedAndroidProj)
- EmbeddedAndroidNodeProj.tar.xz (Node-RED Project, Git: https://github.com/akasilov/EmbeddedAndroidNodeProj)
### Date
- February 2018
### Used Frameworks, IDE's, ..
- Android Studio (v3.3.1) 
- Node-RED (v0.19.5), see https://nodered.org/
  - Node-RED-Dashboard (v2.13.2), see https://flows.nodered.org/node/node-red-dashboard
### Used periphery
- Display
- Buttons (T1 .. T4)
- LEDs (L1 .. L4)
- Potentiometer
- I2C: ambient light sensor (MAX44009EDT+T)
- (LAN/WLAN)

## Functional description
The choosen peripherals (LED's L1 .. L4, Buttons T1 .. T4, potentiometer, ambient light sensor) of the Firefly-BFH-Cape will be remote controlled via a web interface. On the one hand, the webinterface shows all current states (buttons and leds) and also the measured data (ambient light value and potentiometer's adc value), on the other hand one can set the states (button and leds) over this interface.
Additionally, the Firefly-BFH-Cape's own display shows the button states, leds states and ambient light data too. It is possible to change the states of leds and buttons via the display, but there are some restrictions (e.g. changing a switches state via display may not lead to a change of the leds state on the display).

Push buttons:
- Pressing a push button (hardware button) changes a switches state (webinterface and Firefly display) from off to on - but only as long as the button is held pressed.
- The push buttons also affects the Firefly leds

List of functions:
- Firefly-BFH-Cape
    - Push a button to set led (T1 -> L1, ..)
    - Measure the current ambient light value (unit: lux, sensor is located on the bottom left)
    - Set a new potentiometer value (rotate potentiometer knob)
    - Shows button and led states on display
    - Shows a lux value chart on display
- Web interface:
    - show or set button state
    - show or set led state
    - show the measured lux value (ambient light sensor) in a chart
    - show the measured potentiometer adc value in a gauge

## How to install
### Android application
Copy and unzip the Android Stuido Project file "EmbeddedAndroidProj.tar.xz" to a local folder.
Check that there is an internet connection (ethernet, wlan, ..).
### Install web interface (Node-RED dashboard)
Install node package manager:
``` $ sudo apt-get install npm ```

Install node red:
``` $ sudo npm install -g --unsafe-perm node-red ```

Check if node red is running:
Start node red in a terminal.
```
$ node-red

27 Feb 22:24:31 - [info] 

Welcome to Node-RED
===================

27 Feb 22:24:31 - [info] Node-RED version: v0.19.5
27 Feb 22:24:31 - [info] Node.js  version: v8.10.0
27 Feb 22:24:31 - [info] Linux 4.15.0-45-generic x64 LE
27 Feb 22:24:31 - [info] Loading palette nodes
27 Feb 22:24:31 - [warn] rpi-gpio : Raspberry Pi specific node set inactive
27 Feb 22:24:31 - [warn] rpi-gpio : Cannot find Pi RPi.GPIO python library
27 Feb 22:24:32 - [info] Dashboard version 2.13.2 started at /ui
27 Feb 22:24:32 - [info] Settings file  : /home/matt/.node-red/settings.js
27 Feb 22:24:32 - [info] Context store  : 'default' [module=memory]
27 Feb 22:24:32 - [info] User directory : /home/matt/.node-red
27 Feb 22:24:32 - [info] Server now running at http://127.0.0.1:1880/
27 Feb 22:24:32 - [info] Active project : EmbeddedAndroidNodeProj
27 Feb 22:24:32 - [info] Flows file     : /home/matt/.node-red/projects/EmbeddedAndroidNodeProj/proj
27 Feb 22:24:32 - [info] Starting flows
27 Feb 22:24:32 - [info] Started flows
27 Feb 22:24:32 - [info] [mqtt-broker:MQTT Broker] Connected to broker: mqtt://m24.cloudmqtt.com:17990
```
Stop node red (Ctrl + C)

Install node red dashboard:
```
$ cd ~/.node-red/
$ npm install node-red-dashboard
```

Enable projects feature: Change settings.js to activate projects feature which is disabled by default. 
`nano ~/.node-red/settings.js`
Search (nano: Crtl+W) for "projects", change from `enabled: false` to `enabled: true`
```
editorTheme: {  
       projects: {  
           enabled: true
       }
   }
```

Clone "EmbeddedAndroidNodeProj" project:
- Start node-red: `$ node-red`
- Open node red in browser: http://localhost:1880/
- Clone git repository:
    - Click on the menu in the upper right corner, select `Projects` -> `New` -> `Clone Repository`
    - Git repository URL: `https://github.com/akasilov/EmbeddedAndroidNodeProj`
    - Enter name and password of your git account and click `Clone Repository`
    - The repository will be cloned into `~/.node-red/projects/`.
- More information about projects: https://nodered.org/docs/user-guide/projects/

Check that the Firefly-BFH-Cape is connected to the internet (ethernet or wlan). This is necessary in order to connect to the MQTT-Broker.
## Start application
### Start Android Studio Project

- Open Android Stuido, open the project `EmbeddedAndroidProj` (archive EmbeddedAndroidProj.tar.xz).
- Connect Firefly-BFH-Cape to your desktop/laptop via USB-C connector.
- Run the project.

### Start Node-RED
Start node red in a terminal:
``` 
$ node-red
``` 
Open dashboard in a browser:
URL: `http://localhost:1880/ui/`

Open Node-RED flow (application) in a browser if desired:
URL: `http://localhost:1880/`

